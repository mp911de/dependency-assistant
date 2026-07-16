/*
 * Copyright 2026 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.plan.UpgradePlanState.Content;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.Ticket;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.ticket.TicketSystemInvalidationListener;
import biz.paluch.dap.ticket.TicketSystemProvider;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.GlobalUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jspecify.annotations.Nullable;

/**
 * Aggregate front door for the Upgrade Plan: the single domain writer of the
 * persisted plan.
 *
 * <p>Plan transitions run as guarded undoable commands. Each apply, undo, and
 * redo advances the plan revision while undo and redo restore the corresponding
 * logical plan state. Ticket creation remains outside the undo history because
 * the created ticket exists in the external system either way, while changing
 * its plan association is undoable.
 *
 * @author Mark Paluch
 */
@Service(Service.Level.PROJECT)
public final class UpgradePlanService implements Disposable {

	private final Project project;

	private final PlanVcs vcs;

	private final UpgradePlanListener events;

	private final PlanTextTemplates textTemplates;

	private final UpgradePlanState state;

	private volatile @Nullable TicketSystem ticketSystem;

	private final UndoManager undoManager;

	private final CommandProcessor commandProcessor;

	private volatile @Nullable VersionedUpgradePlan versionedPlan;

	private volatile List<Milestone> milestones = List.of();

	private volatile List<Label> labels = List.of();

	private boolean disposed;

	// transient: an apply or ticket-publishing run is in flight, muting plan actions
	private volatile boolean busy;

	private volatile boolean refreshingMilestones;

	UpgradePlanService(Project project) {

		this.project = project;
		this.vcs = new PlanVcs(project);
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

	/**
	 * Return the project-scoped service instance.
	 *
	 * @param project the IntelliJ project.
	 * @return the corresponding service instance.
	 */
	static UpgradePlanService getInstance(Project project) {
		return project.getService(UpgradePlanService.class);
	}

	Project getProject() {
		return project;
	}

	boolean hasVcs() {
		return vcs.hasVcs();
	}

	PlanVcs getVcs() {
		return this.vcs;
	}

	boolean hasTicketSystem() {
		return ticketSystem != null;
	}

	TicketSystem getTicketSystem() {

		if (this.ticketSystem == null) {
			throw new IllegalStateException("No ticket system available");
		}
		return this.ticketSystem;
	}

	void refreshTicketSystem() {
		replaceTicketSystem(TicketSystemProvider.find(project));
	}

	boolean isTicketSystem(TicketSystem candidate) {
		return this.ticketSystem == candidate;
	}

	private void replaceTicketSystem(@Nullable TicketSystem replacement) {

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

		Snapshot snapshot = this.state.doWithContent(Snapshot::new);
		VersionedUpgradePlan versioned = this.versionedPlan;

		if (versioned != null && versioned.hasGeneration(snapshot.planGeneration)) {
			return versioned.plan();
		}

		UpgradePlan plan = createUpgradePlan(snapshot);
		this.versionedPlan = new VersionedUpgradePlan(snapshot.planGeneration(), plan);

		return plan;
	}

	private UpgradePlan createUpgradePlan(Snapshot snapshot) {
		FileScope scope = FileScope.from(snapshot.content.getAffectedFiles());
		List<UpgradePlanItem> items = new ArrayList<>();
		for (Item item : snapshot.content()) {
			UpgradePlanItem materialized = item.getMaterialized();
			if (materialized != null) {
				items.add(materialized);
			}
		}
		return UpgradePlan.of(scope, items);
	}

	/**
	 * Return the build files in the plan's scope.
	 */
	List<String> affectedFiles() {
		return List.copyOf(getContent().getAffectedFiles());
	}

	@Override
	public void dispose() {
		this.disposed = true;
	}

	boolean isDisposed() {
		return disposed || project.isDisposed();
	}

	/**
	 * Return whether the plan currently holds any item.
	 */
	boolean hasItems() {
		return !getContent().isEmpty();
	}

	/**
	 * Return whether an apply or ticket-publishing run currently holds the plan.
	 */
	boolean isBusy() {
		return busy;
	}

	boolean isRefreshingListsEnabled() {
		return !isBusy() && hasTicketSystem() && !refreshingMilestones;
	}

	/**
	 * Mark the plan busy or idle for the duration of a long-running run so plan
	 * actions mute while it proceeds.
	 */
	void setBusy(boolean busy) {
		this.busy = busy;
		ActivityTracker.getInstance().inc();
	}

	void setRefreshingMilestones(boolean refreshingMilestones) {
		this.refreshingMilestones = refreshingMilestones;
		ActivityTracker.getInstance().inc();
	}

	/**
	 * Reload the live plan from persisted state, resolving interface metadata and
	 * the build-file scope while retaining the current {@link PlanGeneration}.
	 *
	 * @return the reloaded plan items in order.
	 */
	UpgradePlan reloadPlan() {

		while (true) {

			Snapshot snapshot = state.doWithContent(Snapshot::new);
			FileScope scope = FileScope.from(snapshot.content().getAffectedFiles());
			UpgradePlanLoader loader = new UpgradePlanLoader(ticketSystem);

			for (Item item : snapshot.content()) {
				item.setMaterialized(loader.create(item));
			}

			UpgradePlan plan = createUpgradePlan(snapshot);
			if (state.isCurrent(snapshot.planGeneration())) {
				this.versionedPlan = new VersionedUpgradePlan(snapshot.planGeneration(), plan);
				return plan;
			}
		}
	}

	/**
	 * Request a fresh reload of the live plan by publishing a plan-changed event.
	 */
	void requestReload() {
		events.planChanged();
	}

	/**
	 * Capture the armed upgrades into a fresh plan, discarding any previous plan.
	 * The front door of the review transfer: the persisted shape is derived here,
	 * so callers hand over reviewed upgrades and their pinned targets, not stored
	 * state.
	 */
	void planUpgrades(Map<? extends PlannedUpgrade, ArtifactVersion> upgrades, FileScope scope) {
		execute(PlanAction.planUpgrades(upgrades, scope, getPlan()));
	}

	/**
	 * Merge a copied plan fragment into the plan as one undoable transition.
	 * Fragment items whose members and target are already planned are skipped; the
	 * fragment's scope files join the plan scope. The live plan rebuilds through
	 * the published change event.
	 */
	void pasteItems(Content pasted) {

		if (isBusy()) {
			return;
		}

		execute(PlanAction.pasteItems(pasted, getContent()));
	}

	/**
	 * Remove the given plan item, keeping the current scope, as one undoable
	 * transition.
	 */
	void removeItem(UpgradePlanItem item) {
		execute(PlanAction.removeItems(getContent(), item));
	}

	/**
	 * Remove the given plan items, keeping the current scope, as one undoable
	 * transition.
	 */
	void removeItems(Collection<UpgradePlanItem> items) {
		if (!items.isEmpty()) {
			execute(PlanAction.removeItems(getContent(), items));
		}
	}

	/**
	 * Link a ticket to a plan item as one undoable association change. Undo removes
	 * only the plan association; the ticket continues to exist in the external
	 * system.
	 */
	void linkTicket(UpgradePlanItem item, TicketRepository repository, Ticket ticket) {
		ApplicationManager.getApplication().invokeLater(() -> {
			execute(PlanAction.linkTicket(getContent(), item, new UpgradeTicket(repository, ticket)));
		});
	}

	/**
	 * Remove the ticket association of the given plan items as one undoable
	 * command. The ticket keeps existing in the external system; only the plan
	 * links are cleared and restored.
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
	 * Return the rendered commit message for the given plan item, followed by the
	 * ticket system's close reference when the item has a linked ticket.
	 */
	String getCommitMessage(UpgradePlanItem item) {
		String subject = textTemplates.commitMessage(item);
		String close = getTicketCloseReference(item);
		return close != null ? subject + "\n\n" + close : subject;
	}

	String getTicketTitle(UpgradePlanItem item) {
		return textTemplates.ticketTitle(item);
	}

	/**
	 * Return the ticket system's close reference for the item's linked ticket.
	 *
	 * @param item the plan item.
	 * @return the close reference, or {@literal null} when the item has no linked
	 * ticket, no ticket system is bound, or the system defines no close reference.
	 */
	@Nullable
	String getTicketCloseReference(UpgradePlanItem item) {
		TicketKey ticketKey = item.getTicketKey();
		if (ticketKey == null || !hasTicketSystem()) {
			return null;
		}
		return getTicketSystem().getCloseReference(ticketKey);
	}

	/**
	 * Remove a committed item from the plan without registering semantic undo.
	 */
	void removeCommittedItem(UpgradePlanItem item) {
		apply(PlanAction.removeItems(getContent(), List.of(item)));
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

	private Content getContent() {
		return getPlan().getContent();
	}

	/**
	 * Run a plan action as one undoable command, capture its before and after plan
	 * states, register the platform undo adapter, and publish the change. Must run
	 * on the EDT.
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

	record VersionedUpgradePlan(PlanGeneration generation, UpgradePlan plan) {

		public boolean hasGeneration(PlanGeneration planGeneration) {
			return this.generation.equals(planGeneration);
		}

	}

	record Snapshot(PlanGeneration planGeneration, Content content) {

	}

}
