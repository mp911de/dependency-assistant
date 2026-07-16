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

import biz.paluch.dap.assistant.check.DependencySiteNavigator;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;

/**
 * Find the build-file declaration sites of the first selected plan item and
 * hand them to the Find tool window, through the same Dependency Site search
 * the upgrade review dialog uses. Site resolution runs lazily on invocation;
 * enablement only requires a selection and an idle plan.
 *
 * @author Mark Paluch
 */
public class FindUsagesAction extends DumbAwareAction {

	@Override
	public void update(AnActionEvent e) {

		Project project = e.getProject();
		UpgradePlanService service = project != null ? UpgradePlanService.getInstance(project) : null;
		e.getPresentation().setEnabled(service != null && !service.isBusy() && !PlanSelection.from(e).isEmpty());
	}

	@Override
	public ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void actionPerformed(AnActionEvent e) {

		PlanSelection.from(e).doWithFirst(it -> {

			Project project = e.getProject();
			if (project == null) {
				return;
			}

			findUsages(project, JBPopupFactory.getInstance().guessBestPopupLocation(e.getDataContext()), it);
		});
	}

	static void findUsages(Project project, RelativePoint where, UpgradePlanItem item) {
		UpgradePlanService service = UpgradePlanService.getInstance(project);
		new DependencySiteNavigator(project, service, service.getScope())
				.openInFindWindow(item.toQuery(), where);
	}

}
