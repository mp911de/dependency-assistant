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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;

/**
 * Remove the ticket association of the selected plan items. The ticket itself
 * stays in the external system; only the plan link is cleared, so the next
 * ticket-creation run finds or recreates it. Visible only when a ticket system
 * is bound; enabled when the selection carries at least one linked ticket and
 * no plan run is in flight.
 *
 * @author Mark Paluch
 */
public class UnlinkTicketAction extends DumbAwareAction {

	@Override
	public void update(AnActionEvent e) {

		Project project = e.getProject();
		UpgradePlanService service = project != null ? UpgradePlanService.getInstance(project) : null;
		boolean visible = service != null && service.hasTicketSystem();
		Presentation presentation = e.getPresentation();
		presentation.setVisible(visible);

		boolean hasTicket = PlanSelection.from(e).stream().anyMatch(UpgradePlanItem::hasTicket);
		presentation.setEnabled(visible && !service.isBusy() && hasTicket);
	}

	@Override
	public ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void actionPerformed(AnActionEvent e) {

		Project project = e.getProject();
		if (project == null) {
			return;
		}

		List<UpgradePlanItem> ticketed = PlanSelection.from(e).stream()
				.filter(UpgradePlanItem::hasTicket).toList();
		if (!ticketed.isEmpty()) {
			UpgradePlanService.getInstance(project).unlinkTickets(ticketed);
		}
	}

}
