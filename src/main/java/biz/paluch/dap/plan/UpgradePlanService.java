/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.paluch.dap.plan;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.plan.UpgradePlanState.Content;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.support.VersionControl;
import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.ticket.TicketSystemInvalidationListener;
import biz.paluch.dap.ticket.TicketSystemProvider;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.messages.MessageBusConnection;
import org.jspecify.annotations.Nullable;

/**
 * Service for persistent Upgrade Plan transitions.
 * <p>Apply, undo and redo advance revisions. Undo and redo restore the
 * corresponding logical state. Ticket creation is external and cannot be
 * undone, but plan links can.
 *
 * @author Mark Paluch
 */
@Service(Service.Level.PROJECT)
public final class UpgradePlanService implements Disposable {

	private final Project project;

	private final VersionControl vcs;

	private final BetterPsiManager psiManager;

	private final UpgradePlanListener events;

	private final PlanTextTemplates textTemplates;

	private final UpgradePlanState state;

	private volatile @Nullable TicketSystem ticketSystem;

	private final UndoManager undoManager;

	private final CommandProcessor commandProcessor;

	private volatile @Nullable VersionedUpgradePlan versionedPlan;

	private volatile List<Milestone> milestones = List.of();

	private volatile List<Label> labels = List.of();

	private volatile boolean busy;

	private volatile boolean refreshingMilestones;

	UpgradePlanService(Project project) {

		this.project = project;
		this.vcs = VersionControl.find(project);
		this.psiManager = BetterPsiManager.getInstance(project);
		this.events = project.getMessageBus().syncPublisher(UpgradePlanListener.TOPIC);
		this.textTemplates = new PlanTextTemplates(project);
		this.state = UpgradePlanState.getInstance(project);
		MessageBusConnection connection = project.getMessageBus().connect(this);
		connection.subscribe(TicketSystemInvalidationListener.TOPIC,
				(TicketSystemInvalidationListener) () -> replaceTicketSystem(null));
		this.ticketSystem = TicketSystemProvider.find(project);
		this.undoManager = UndoManager.getInstance(project);
		this.commandProcessor = CommandProcessor.getInstance();
	}

	static UpgradePlanService getInstance(Project project) {
		return project.getService(UpgradePlanService.class);
	}

	Project getProject() {
		return project;
	}

	VersionControl getVcs() {
		return this.vcs;
	}

	boolean hasTicketSystem() {
		return ticketSystem != null;
	}

	TicketSystem getTicketSystem() {
		TicketSystem ticketSystem = this.ticketSystem;
		if (ticketSystem == null) {
			throw new IllegalStateException("No ticket system available");
		}
		return ticketSystem;
	}

	void refreshTicketSystem() {
		replaceTicketSystem(TicketSystemProvider.find(project));
	}

	boolean isTicketSystem(TicketSystem candidate) {
		return this.ticketSystem == candidate;
	}

	public void replaceTicketSystem(@Nullable TicketSystem replacement) {

		TicketSystem previous = this.ticketSystem;
		if (previous == replacement) {
			return;
		}

		this.ticketSystem = replacement;
		this.milestones = List.of();
		this.labels = List.of();
		this.refreshingMilestones = false;
		this.versionedPlan = null;
		this.state.getPlan().clearSelectedTicketValues();
		ActivityTracker.getInstance().inc();
		events.ticketSystemChanged();
	}

	void setMilestonesLabels(List<? extends Milestone> milestones, List<? extends Label> labels) {
		this.milestones = List.copyOf(milestones);
		this.labels = List.copyOf(labels);

		events.milestonesChanged();
	}

	List<Milestone> getMilestones() {
		return milestones;
	}

	@Nullable
	Milestone getSelectedMilestone() {
		return this.state.getPlan().getSelectedMilestone();
	}

	void setSelectedMilestone(@Nullable Milestone milestone) {
		this.state.getPlan().setSelectedMilestone(milestone);
	}

	@Nullable
	String getSelectedMilestoneName() {
		return getPlan().getMilestone();
	}

	List<Label> getLabels() {
		return labels;
	}

	@Nullable
	Label getLabel() {
		return this.state.getPlan().getSelectedLabel();
	}

	void setLabel(@Nullable Label label) {
		this.state.getPlan().setSelectedLabel(label);
	}

	@Nullable
	String getLabelName() {
		return getPlan().getLabel();
	}

	FileScope getScope() {
		return FileScope.from(affectedFiles());
	}

	UpgradePlanState.Plan getPlan() {
		return state.getPlan();
	}

	UpgradePlan getUpgradePlan() {
		return this.state.doWithContent((planGeneration, content) -> {

			VersionedUpgradePlan versioned = this.versionedPlan;

			if (versioned != null && versioned.hasGeneration(planGeneration)) {
				return versioned.plan();
			}

			UpgradePlan plan = content.createUpgradePlan();
			this.versionedPlan = new VersionedUpgradePlan(planGeneration, plan);

			return plan;
		});
	}

	List<String> affectedFiles() {
		return List.copyOf(getPlan().getContent().getAffectedFiles());
	}

	@Override
	public void dispose() {
	}

	boolean hasItems() {
		return !getPlan().getContent().isEmpty();
	}

	/**
	 * Return whether an apply or ticket-publishing run holds the plan.
	 */
	boolean isBusy() {
		return busy;
	}

	boolean isRefreshingListsEnabled() {
		return !isBusy() && hasTicketSystem() && !refreshingMilestones;
	}

	void setBusy(boolean busy) {
		this.busy = busy;
		ActivityTracker.getInstance().inc();
	}

	void setRefreshingMilestones(boolean refreshingMilestones) {
		this.refreshingMilestones = refreshingMilestones;
		ActivityTracker.getInstance().inc();
	}

	/**
	 * Reload interface metadata and file scope from persisted facts without
	 * changing the plan generation.
	 */
	UpgradePlan reloadPlan() {

		while (true) {

			Snapshot snapshot = state
					.doWithContent((planGeneration, content) -> {
						Content copy = content.snapshot();
						copy.setItems(List.copyOf(content.getItems()));
						return new Snapshot(planGeneration, copy);
					});
			UpgradePlanLoader loader = new UpgradePlanLoader(project, ticketSystem);

			for (Item item : snapshot.content()) {
				item.setMaterialized(loader.create(item));
			}

			UpgradePlan plan = snapshot.content().createUpgradePlan();
			if (state.isCurrent(snapshot.planGeneration())) {
				this.versionedPlan = new VersionedUpgradePlan(snapshot.planGeneration(), plan);
				return plan;
			}
		}
	}

	void requestReload() {
		events.planChanged();
	}

	/**
	 * Replace the plan with reviewed upgrades and their captured scope. Reconsider
	 * the milestone when its catalog is available.
	 */
	void planUpgrades(Map<? extends PlannedUpgrade, ArtifactVersion> upgrades, FileScope scope) {
		execute(PlanAction.planUpgrades(upgrades, scope, getPlan()));

		Milestones milestones = new Milestones(getMilestones());
		if (milestones.isEmpty() || refreshingMilestones) {
			return;
		}
		ReadAction.nonBlocking(this::getMilestoneSelector)
				.inSmartMode(project)
				.expireWith(this)
				.finishOnUiThread(ModalityState.any(), it -> {
					Milestone milestone = milestones.findOrDefault(getSelectedMilestoneName(), it);
					if (milestone != null) {
						setSelectedMilestone(milestone);
						events.milestonesChanged();
					}
				}).submit(AppExecutorUtil.getAppExecutorService());
	}

	MilestoneSelector getMilestoneSelector() {

		String branch = getVcs().getCurrentBranch();
		Versioned projectVersion = resolveProjectVersion(affectedFiles());
		return new MilestoneSelector(branch, projectVersion);
	}

	/**
	 * Paste a fragment as one undoable transition using
	 * {@link PlanAction#pasteItems}.
	 */
	void pasteItems(Content pasted) {

		if (isBusy()) {
			return;
		}

		execute(PlanAction.pasteItems(pasted, getPlan().getContent()));
	}

	/**
	 * Remove an item through undoable state change, retaining the file scope.
	 */
	void removeItem(UpgradePlanItem item) {
		execute(PlanAction.removeItems(getPlan().getContent(), item));
	}

	/**
	 * Remove items through one undoable state change, retaining the file scope.
	 */
	void removeItems(Collection<UpgradePlanItem> items) {
		if (!items.isEmpty()) {
			execute(PlanAction.removeItems(getPlan().getContent(), items));
		}
	}

	/**
	 * Queue an undoable ticket association change. Undo affects the link, not the
	 * external ticket.
	 */
	void linkTicket(UpgradePlanItem item, TicketRepository repository, Ticket ticket) {
		ApplicationManager.getApplication().invokeLater(() -> {
			execute(PlanAction.linkTicket(getPlan().getContent(), item, new UpgradeTicket(repository, ticket)));
		});
	}

	void renameItem(UpgradePlanItem item, String displayName, boolean rememberName) {
		execute(PlanAction.renameItem(getPlan().getContent(), item, displayName, rememberName));
	}

	/**
	 * Clear ticket links in one undoable command without deleting external tickets.
	 */
	void unlinkTickets(List<UpgradePlanItem> items) {

		Content content = getPlan().getContent();
		List<PlanAction> actions = items.stream().filter(UpgradePlanItem::hasTicket)
				.map(it -> PlanAction.unlinkTicket(content, it)).toList();
		if (!actions.isEmpty()) {
			execute(PlanAction.composite(actions));
		}
	}

	/**
	 * Render the commit message. The linked ticket's display and close references
	 * are available to the template and render empty without a ticket or ticket
	 * system.
	 */
	String getCommitMessage(UpgradePlanItem item) {
		return hasTicketSystem() ? textTemplates.getCommitMessage(item, getTicketSystem())
				: textTemplates.getCommitMessage(item);
	}

	String getTicketTitle(UpgradePlanItem item) {
		return textTemplates.getTicketTitle(item);
	}

	/**
	 * Remove a committed item from the plan without registering semantic undo.
	 */
	void removeCommittedItem(UpgradePlanItem item) {
		apply(PlanAction.removeItems(getPlan().getContent(), List.of(item)));
		events.planItemChanged();
	}

	/**
	 * Complete a VCS apply by publishing its final plan change behind a global
	 * nonundoable boundary.
	 */
	void vcsApplied(FileScope scope) {
		addNonUndoableBoundary(scope.toList(), MessageBundle.message("plan.apply.command"));
		events.planChanged();
	}

	private void addNonUndoableBoundary(List<VirtualFile> affected, String commandName) {

		if (affected.isEmpty()) {
			return;
		}

		CommandProcessor.getInstance().executeCommand(project, () -> {
			DocumentReferenceManager references = DocumentReferenceManager.getInstance();
			for (VirtualFile file : affected) {
				undoManager.nonundoableActionPerformed(references.create(file), true);
			}
		}, commandName, null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
		// The platform retains the latest command in a merger. Advance it once so the
		// nonundoable group reaches the global stack before control returns to the UI.
		CommandProcessor.getInstance().executeCommand(project, () -> {
		}, commandName, null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
	}

	/**
	 * Discard all planned upgrades and their captured scope.
	 */
	void clear() {
		execute(PlanAction.discardUpgrades(getPlan()));
	}

	/**
	 * Run one undoable plan transition. The caller must be on the EDT.
	 */
	private void execute(PlanAction action) {

		commandProcessor.executeCommand(project, () -> {
			Pair<PlanGeneration, PlanGeneration> transition = apply(action);
			registerUndoable(transition.first, transition.second, action);
			fire(action.materialization());
		}, action.getCommandName(), null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);
	}

	private Pair<PlanGeneration, PlanGeneration> apply(PlanAction action) {

		while (true) {
			Snapshot before = state.doWithContent(Snapshot::new);
			PlanGeneration after = state.advance(before.planGeneration(), action::apply);
			if (after != null) {
				return Pair.create(before.planGeneration(), after);
			}
		}
	}

	private void registerUndoable(PlanGeneration before, PlanGeneration after, PlanAction action) {

		GlobalUndoableAction undoAction = new GlobalUndoableAction() {

			@Override
			public void undo() throws UnexpectedUndoException {
				if (state.advance(after, before, action::undo) != null) {
					fire(action.materialization());
				} else {
					throw new UnexpectedUndoException("Cannot perform undo");
				}
			}

			@Override
			public void redo() throws UnexpectedUndoException {

				if (state.advance(before, after, action::apply) != null) {
					fire(action.materialization());
				} else {
					throw new UnexpectedUndoException("Cannot perform redo");
				}
			}

		};
		undoManager.undoableActionPerformed(undoAction);
	}

	private void fire(PlanAction.Materialization materialization) {
		if (materialization == PlanAction.Materialization.REBUILD) {
			events.planChanged();
		} else {
			events.planItemChanged();
		}
	}


	@RequiresReadLock
	private Versioned resolveProjectVersion(List<String> affectedFiles) {

		List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll(project);

		for (VirtualFile file : FileScope.from(affectedFiles)) {

			ProgressManager.checkCanceled();
			PsiFile psiFile = psiManager.findFile(file);
			if (psiFile == null) {
				continue;
			}

			for (DependencyAssistant assistant : assistants) {
				if (!assistant.supports(psiFile)) {
					continue;
				}

				ProjectDependencyContext context = assistant.createContext(psiFile);
				Versioned projectVersion = context.getProjectVersion();
				if (context.isAvailable() && projectVersion.isVersioned()) {
					return projectVersion;
				}
			}
		}

		return Versioned.unversioned();
	}

	record VersionedUpgradePlan(PlanGeneration generation, UpgradePlan plan) {

		public boolean hasGeneration(PlanGeneration planGeneration) {
			return this.generation.equals(planGeneration);
		}

	}

	record Snapshot(PlanGeneration planGeneration, Content content) {

	}

}
