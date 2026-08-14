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

import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Refreshes the milestone and label lists; muted while the background load
 * runs, like the selectors it feeds.
 */
class RefreshMilestonesAction extends UpgradePlanAction {

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
		boolean visible = this.service.hasTicketSystem();
		e.getPresentation().setVisible(visible);
		e.getPresentation().setEnabled(visible && this.service.isRefreshingListsEnabled());
	}

	@Override
	void perform(Project project) {
		perform(project, true);
	}

	void perform(Project project, boolean forceReload) {

		TicketSystem ticketSystem = service.getTicketSystem();
		service.setRefreshingMilestones(true);
		controlsChanged.run();

		new Task.Backgroundable(project, MessageBundle.message("plan.refresh.lists.progress"), true) {

			private List<? extends Milestone> milestones = List.of();

			private List<? extends Label> labels = List.of();

			private boolean selectDefaultMilestone = false;

			private @Nullable MilestoneSelector milestoneSelector;

			@Override
			public void run(ProgressIndicator indicator) {

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
						.findMilestone(service.getSelectedMilestoneName()) == null) {
					milestoneSelector = ReadAction.nonBlocking(() -> service.getMilestoneSelector())
							.inSmartMode(project)
							.expireWith(service)
							.executeSynchronously();
				}
			}

			@Override
			public void onSuccess() {
				if (!service.isTicketSystem(ticketSystem)) {
					return;
				}

				Labels labels = new Labels(this.labels);
				service.setLabel(labels.getSelection(service.getLabelName()));

				Milestones milestones = new Milestones(this.milestones);

				if (selectDefaultMilestone) {
					service.setSelectedMilestone(milestones.findOrDefault(
							service.getSelectedMilestoneName(), milestoneSelector));
				}
				else {
					service.setSelectedMilestone(milestones.findMilestone(service.getSelectedMilestoneName()));
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
				if (name.equals("dependency-upgrade")) {
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
