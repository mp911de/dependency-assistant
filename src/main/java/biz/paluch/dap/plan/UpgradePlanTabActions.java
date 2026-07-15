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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DefaultCustomComponentAction;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Milestone and label controls installed in the Upgrade Plan tool-window tab
 * row. Owns their models, actions, refresh action, and catalog rebinding as one
 * unit.
 *
 * @author Mark Paluch
 */
class UpgradePlanTabActions {

	private static final int UPCOMING_DAYS = 3;

	private static final int SECONDARY_TEXT_GAP = 5;

	private final UpgradePlanService service;

	private final SelectorAction.SelectorModel<Milestone> milestonesModel;

	private final SelectorAction.SelectorModel<Label> labelsModel;

	private final SelectorAction<Milestone> milestoneSelector;

	private final SelectorAction<Label> labelSelector;

	private final RefreshMilestonesAction refreshAction;

	private final AnAction selectorGap;

	private final AnAction refreshGap;

	UpgradePlanTabActions(UpgradePlanService service, Component parent) {

		this.service = service;
		this.milestonesModel = new MilestonesModel();
		this.labelsModel = new LabelsModel();
		this.milestoneSelector = new SelectorAction<>(service, parent, "plan.selector.milestone",
				DependencyAssistantIcons.PLAN_MILESTONE, milestonesModel);
		this.labelSelector = new SelectorAction<>(service, parent, "plan.selector.label",
				DependencyAssistantIcons.PLAN_LABEL, labelsModel);
		this.refreshAction = new RefreshMilestonesAction(service, this::refreshSelectors);
		this.selectorGap = gap(service, 4);
		this.refreshGap = gap(service, 6);
	}

	AnAction[] getActions() {
		return new AnAction[] {milestoneSelector, selectorGap, labelSelector, refreshGap, refreshAction};
	}

	private static AnAction gap(UpgradePlanService service, int width) {

		return new DefaultCustomComponentAction(() -> {
			Dimension size = JBUI.size(width, 0);
			return new Box.Filler(size, size, size);
		}) {

			@Override
			public void update(AnActionEvent event) {
				event.getPresentation().setVisible(service.hasTicketSystem());
			}

			@Override
			public void updateCustomComponent(JComponent component, Presentation presentation) {
				component.setVisible(presentation.isVisible());
			}

			@Override
			public ActionUpdateThread getActionUpdateThread() {
				return ActionUpdateThread.EDT;
			}

		};
	}

	void restore() {
		if (service.hasTicketSystem()) {
			refreshAction.perform(service.getProject(), false);
		}
	}

	void milestonesChanged() {

		List<Milestone> milestones = new ArrayList<>(service.getMilestones());
		milestones.sort(MilestoneComparator.INSTANCE);
		milestonesModel.setValues(milestones, service.getSelectedMilestone());

		List<Label> labels = new ArrayList<>(service.getLabels());
		labels.sort(Comparator.comparing(Label::getName, String.CASE_INSENSITIVE_ORDER));
		labelsModel.setValues(labels, service.getLabel());
	}

	void ticketSystemChanged() {
		milestonesModel.setValues(List.of(), null);
		labelsModel.setValues(List.of(), null);
	}

	private void refreshSelectors() {
		milestonesModel.update();
		labelsModel.update();
	}

	private class LabelsModel extends SelectorAction.SelectorModel<Label> {

		private final Map<Color, Icon> labelIcons = new ConcurrentHashMap<>();

		@Override
		String getText(Label value) {
			return value.getName();
		}

		@Override
		@Nullable
		Icon getIcon(Label value) {
			Color color = value.getColor();
			if (color == null) {
				return null;
			}
			return labelIcons.computeIfAbsent(color, LabelColorIcon::new);
		}

		@Override
		void selectionChanged(@Nullable Label selection) {
			service.setLabel(selection);
		}

	}

	private class MilestonesModel extends SelectorAction.SelectorModel<Milestone> {

		@Override
		String getText(Milestone value) {
			return value.getTitle();
		}

		@Override
		String getToolTip(Milestone value) {

			String title = StringUtil.escapeXmlEntities(value.getTitle());
			String description = value.getDescription();
			if (description == null || description.isBlank()) {
				return title;
			}

			return "<html><b>" + title + "</b><br>" + StringUtil.escapeXmlEntities(description) + "</html>";
		}

		@Override
		Icon getIcon(Milestone value) {

			if (value.isOverdue()) {
				return AllIcons.General.ShowWarning;
			}
			if (!value.isOpen()) {
				return DependencyAssistantIcons.PLAN_MILESTONE_CLOSED;
			}

			return value.getReleaseDay() != null ? DependencyAssistantIcons.PLAN_MILESTONE_OPEN
					: DependencyAssistantIcons.PLAN_MILESTONE_UNSCHEDULED;
		}

		@Override
		@Nullable
		String getSecondaryText(Milestone value) {

			LocalDate dueDate = value.getReleaseDay();
			if (dueDate == null) {
				return null;
			}

			String text = ReleaseDateFormatter.create().formatDue(dueDate);
			if (!value.isOpen()) {
				return secondary(text, null);
			}

			long days = ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
			if (days < 0) {
				return secondary(text, JBUI.CurrentTheme.Label.warningForeground());
			}
			if (days <= UPCOMING_DAYS) {
				return secondary(text, UIUtil.getLabelSuccessForeground());
			}

			return secondary(text, null);
		}

		@Override
		boolean startsNewGroup(Milestone previous, Milestone value) {
			return MilestoneComparator.startsNewGroup(previous, value);
		}

		@Override
		void selectionChanged(@Nullable Milestone selection) {
			service.setSelectedMilestone(selection);
		}

		/**
		 * Render the date, widening the renderer's stock 6px title-date gap by
		 * {@link #SECONDARY_TEXT_GAP} non-breaking spaces and coloring the date when a
		 * color is given.
		 */
		private String secondary(String text, @Nullable Color color) {

			HtmlChunk date = color != null
					? HtmlChunk.span("color: " + ColorUtil.toHtmlColor(color)).addText(text)
					: HtmlChunk.text(text);
			return new HtmlBuilder().append(HtmlChunk.nbsp(SECONDARY_TEXT_GAP)).append(date)
					.wrapWithHtmlBody().toString();
		}

	}

}
