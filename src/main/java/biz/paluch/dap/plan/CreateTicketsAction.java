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

import java.util.List;

import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Create a ticket for each planned upgrade that has none, through the project's
 * ticket system. A tree selection narrows the run to the selected items;
 * without a selection the whole plan is covered. Disabled when no ticket system
 * is bound or no covered item is missing a ticket.
 *
 * @author Mark Paluch
 */
public class CreateTicketsAction extends UpgradePlanAction {

	@Override
	void update(AnActionEvent e, @Nullable UpgradePlanService service) {

		super.update(e, service);

		boolean available = service != null && service.hasTicketSystem();
		Presentation presentation = e.getPresentation();
		presentation.setVisible(available);
		if (!available) {
			presentation.setEnabled(false);
			return;
		}

		PlanSelection selection = PlanSelection.from(e);
		List<UpgradePlanItem> targetItems = selection.orElseGet(() -> service.getUpgradePlan().getItems());
		int withoutTickets = (int) targetItems.stream().filter(item -> !item.hasTicket()).count();
		presentation.setText(MessageBundle.message("plan.create-tickets.text", withoutTickets));
		presentation.setDescription(description(withoutTickets, !selection.isEmpty()));
		if (presentation.isEnabled()) {
			presentation.setEnabled(withoutTickets > 0);
		}
	}

	static String description(int itemCount, boolean selectedScope) {
		return MessageBundle.message(selectedScope ? "plan.create-tickets.selected.description"
				: "plan.create-tickets.all.description", itemCount);
	}

	@Override
	public void actionPerformed(AnActionEvent e) {

		Project project = e.getProject();
		if (project == null) {
			return;
		}

		UpgradePlanService service = UpgradePlanService.getInstance(project);
		if (service.hasTicketSystem()) {
			createTickets(service, PlanSelection.from(e).orElseGet(() -> service.getUpgradePlan().getItems()));
		}
	}

	/**
	 * Selection-less entry required by {@link UpgradePlanAction}: covers the whole
	 * plan. Invocations that carry a data context go through
	 * {@link #actionPerformed} instead, which honours the selection.
	 */
	@Override
	void perform(Project project) {

		UpgradePlanService service = UpgradePlanService.getInstance(project);
		if (service.hasTicketSystem()) {
			createTickets(service, service.getUpgradePlan().getItems());
		}
	}

	private void createTickets(UpgradePlanService service, List<UpgradePlanItem> items) {

		TicketSystem ticketSystem = service.getTicketSystem();

		Label label = service.getLabel();
		List<Label> labels = label != null ? List.of(label) : List.of();
		Milestone milestone = service.getSelectedMilestone();
		List<Milestone> milestones = milestone != null ? List.of(milestone) : List.of();

		new FindOrCreateUpgradeTickets(service, ticketSystem, milestones, labels, items).start();
	}

}
