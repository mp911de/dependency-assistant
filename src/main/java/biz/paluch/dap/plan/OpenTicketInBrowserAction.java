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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;

/**
 * Open the linked ticket of the first selected plan item in the browser.
 * Visible only when a ticket system is bound; enabled when the first selected
 * item carries a ticket URL and no plan run is in flight.
 *
 * @author Mark Paluch
 */
public class OpenTicketInBrowserAction extends DumbAwareAction {

	@Override
	public void update(AnActionEvent e) {

		Project project = e.getProject();
		UpgradePlanService service = project != null ? UpgradePlanService.getInstance(project) : null;
		boolean visible = service != null && service.hasTicketSystem();
		UpgradePlanItem item = PlanSelection.from(e).first();

		Presentation presentation = e.getPresentation();
		presentation.setVisible(visible);

		if (!visible || item == null) {
			presentation.setEnabled(false);
		} else {
			presentation
					.setEnabled(!service.isBusy() && item.hasTicket());
		}
	}

	@Override
	public void actionPerformed(AnActionEvent e) {
		PlanSelection.from(e).doWithFirst(it -> {
			UpgradeTicket ticket = it.getTicket();
			if (ticket != null) {
				BrowserUtil.browse(ticket.getUrl());
			}
		});
	}

}
