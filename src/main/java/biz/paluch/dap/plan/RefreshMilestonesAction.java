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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Refreshes the milestone and label lists; muted while the background load
 * runs, like the selectors it feeds.
 */
class RefreshMilestonesAction extends UpgradePlanAction {

	private static final Pattern BRANCH_VERSION = Pattern.compile("(\\d+)\\.(\\d+)");

	private static final Logger LOG = Logger.getInstance(RefreshMilestonesAction.class);

	private final UpgradePlanService service;

	private final Runnable controlsChanged;

	RefreshMilestonesAction(UpgradePlanService service, Runnable controlsChanged) {
		this.service = service;
		this.controlsChanged = controlsChanged;
		this.getTemplatePresentation().setText(MessageBundle.message("plan.selector.refresh"));
		this.getTemplatePresentation().setIcon(AllIcons.Actions.Refresh);
	}

	@Override
	void update(AnActionEvent e, @Nullable UpgradePlanService service) {
		e.getPresentation().setEnabled(this.service.isRefreshingListsEnabled());
	}

	@Override
	void perform(Project project) {
		perform(project, true);
	}

	void perform(Project project, boolean forceReload) {

		service.setRefreshingMilestones(true);
		controlsChanged.run();

		new Task.Backgroundable(project, MessageBundle.message("plan.refresh.lists.progress"), true) {

			private List<? extends Milestone> milestones = List.of();

			private List<? extends Label> labels = List.of();

			private boolean selectDefaultMilestone = false;

			private Versioned projectVersion = Versioned.unversioned();

			private @Nullable String branch;

			@Override
			public void run(ProgressIndicator indicator) {

				TicketSystem ticketSystem = service.getTicketSystem();
				try {

					TicketRepository repository = ticketSystem.getRepository();
					if (forceReload) {
						milestones = repository.getMilestones(indicator);
						labels = repository.getLabels(indicator);
						selectDefaultMilestone = true;
					}
					else {
						TicketRepository cached = repository.cached();
						milestones = cached.getMilestones(indicator);
						labels = cached.getLabels(indicator);

						if (milestones.isEmpty()) {
							milestones = repository.getMilestones(indicator);
							selectDefaultMilestone = true;
						}

						if (labels.isEmpty()) {
							labels = repository.getLabels(indicator);
						}
					}
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}

				if (selectDefaultMilestone && new Milestones(milestones)
						.getSelection(service.getSelectedMilestoneName()) == null) {
					branch = service.hasVcs() ? service.getVcs().getCurrentBranch() : null;
					projectVersion = resolveProjectVersion(project, service.affectedFiles());
				}
			}

			@Override
			public void onSuccess() {

				Labels labels = new Labels(this.labels);
				service.setLabel(labels.getSelection(service.getLabelName()));

				Milestones milestones = new Milestones(this.milestones);

				if (selectDefaultMilestone) {
					service.setSelectedMilestone(milestones.getSelectionOrDefault(
							service.getSelectedMilestoneName(), branch, projectVersion));
				}
				else {
					service.setSelectedMilestone(milestones.getSelection(service.getSelectedMilestoneName()));
				}

				service.setMilestonesLabels(this.milestones, this.labels);
			}

			@Override
			public void onThrowable(Throwable error) {
				LOG.warn("Failed to load milestones and labels", error);
				new PlanNotifications().error(project, MessageBundle.message("plan.lists.error"), error);
			}

			@Override
			public void onFinished() {
				service.setRefreshingMilestones(false);
				controlsChanged.run();
			}

		}.queue();
	}

	/**
	 * Resolve the project version from the first build file in the plan's scope
	 * that declares one, to default the milestone from the project's version line.
	 * Runs as a cancellable read that yields to pending write actions; must be
	 * called from a background thread.
	 */
	private static Versioned resolveProjectVersion(Project project, List<String> affectedFiles) {

		return ReadAction.nonBlocking(() -> {
			List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll(project);
			BetterPsiManager psiManager = BetterPsiManager.getInstance(project);

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
		}).executeSynchronously();
	}

	static class Milestones {

		private final List<? extends Milestone> milestones;

		Milestones(List<? extends Milestone> milestones) {
			this.milestones = milestones;
		}

		public @Nullable Milestone getSelection(@Nullable String selectedTitle) {
			if (selectedTitle == null) {
				return null;
			}
			for (Milestone milestone : milestones) {
				if (milestone.getTitle().equals(selectedTitle)) {
					return milestone;
				}
			}
			return null;
		}

		public @Nullable Milestone getDefaultMilestone(@Nullable String branch, Versioned projectVersion) {

			if (milestones.isEmpty()) {
				return null;
			}

			String prefix = versionPrefix(branch);
			if (prefix == null && projectVersion.isVersioned()) {
				prefix = versionPrefix(projectVersion.getVersion().toString());
			}
			if (prefix == null) {
				return null;
			}

			Milestone lowest = null;
			for (Milestone milestone : milestones) {

				String title = milestone.getTitle();
				if (!milestone.isOpen() || !title.equals(prefix) && !title.startsWith(prefix + ".")) {
					continue;
				}
				if (lowest == null || MilestoneComparator.INSTANCE.compare(milestone, lowest) < 0) {
					lowest = milestone;
				}
			}

			return lowest;
		}

		public @Nullable Milestone getSelectionOrDefault(@Nullable String selectedTitle,
				@Nullable String branch, Versioned projectVersion) {

			Milestone selected = getSelection(selectedTitle);
			return selected != null ? selected : getDefaultMilestone(branch, projectVersion);
		}

		private static @Nullable String versionPrefix(@Nullable String value) {

			if (value == null) {
				return null;
			}

			Matcher matcher = BRANCH_VERSION.matcher(value);
			return matcher.find() ? matcher.group(1) + "." + matcher.group(2) : null;
		}

	}

	static class Labels {

		private final List<? extends Label> labels;

		Labels(List<? extends Label> labels) {
			this.labels = labels;
		}

		public @Nullable Label getSelection(@Nullable String selectedName) {

			Label containing = null;
			if (selectedName != null) {
				for (Label label : labels) {

					if (label.getName().equals(selectedName)) {
						return label;
					}
				}
			}

			for (Label label : labels) {

				String name = label.getName().toLowerCase(Locale.ROOT);
				if (name.equals("dependencies") || name.equals("dependency")) {
					return label;
				}
				if (containing == null && name.contains("dependenc")) {
					containing = label;
				}
			}

			return containing;
		}
	}

}
