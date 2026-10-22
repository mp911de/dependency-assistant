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

import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Find suitable upgrade tickets for selected plan items that have none.
 *
 * @author Mark Paluch
 */
public class FindAndLinkTicketAction extends UpgradePlanAction {

	@Override
	public void update(AnActionEvent e, @Nullable UpgradePlanService service) {

		super.update(e, service);

		boolean available = service != null && service.hasTicketSystem();
		Presentation presentation = e.getPresentation();
		if (!available) {
			return;
		}

		List<PlannedUpgrade> targetItems = getTargetItems(e);
		int withoutTickets = (int) targetItems.stream().filter(item -> !item.hasTicket()).count();
		presentation.setText(MessageBundle.message("plan.link-existing-tickets.text", withoutTickets));
		presentation.setDescription(MessageBundle.message("plan.link-existing-tickets.selected.description"));
	}

	@Override
	protected boolean isVisible(AnActionEvent e, UpgradePlanService service) {
		return service.hasTicketSystem();
	}

	@Override
	protected boolean isEnabled(AnActionEvent e, UpgradePlanService service) {
		List<PlannedUpgrade> targetItems = getTargetItems(e);
		int withoutTickets = 0;
		for (PlannedUpgrade item : targetItems) {
			if (!item.hasTicket()) {
				withoutTickets++;
			}
		}
		return super.isEnabled(e, service) && withoutTickets > 0;
	}

	@Override
	public void perform(AnActionEvent e, Project project, UpgradePlanService service) {
		if (service.hasTicketSystem()) {
			new FindAndLinkUpgradeTickets(service, getTargetItems(e)).start();
		}
	}

	private List<PlannedUpgrade> getTargetItems(AnActionEvent e) {
		return PlanSelection.from(e).items();
	}

}
