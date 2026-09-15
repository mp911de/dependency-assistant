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

import java.util.List;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

/**
 * Clear selected plan items' ticket associations without deleting external
 * tickets.
 *
 * @author Mark Paluch
 */
public class UnlinkTicketAction extends UpgradePlanAction {

	@Override
	protected boolean isVisible(AnActionEvent e, UpgradePlanService service) {
		return service.hasTicketSystem();
	}

	@Override
	protected boolean isEnabled(AnActionEvent e, UpgradePlanService service) {

		boolean hasTicket = PlanSelection.from(e).stream().anyMatch(PlannedUpgrade::hasTicket);
		return !service.isBusy() && hasTicket;
	}

	@Override
	public void perform(AnActionEvent e, Project project, UpgradePlanService service) {

		List<PlannedUpgrade> ticketed = PlanSelection.from(e).stream()
				.filter(PlannedUpgrade::hasTicket).toList();
		if (!ticketed.isEmpty()) {
			service.unlinkTickets(ticketed);
		}
	}

}
