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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Base for project-scoped Upgrade Plan actions.
 * <p>Actions are enabled when the plan has items and is idle. Changes reach the
 * tool window through {@link UpgradePlanListener}.
 *
 * @author Mark Paluch
 */
abstract class UpgradePlanAction extends DumbAwareAction {

	@Override
	public final void update(AnActionEvent e) {

		Project project = e.getProject();
		update(e, project == null ? null : UpgradePlanService.getInstance(project));
	}

	/**
	 * Update action availability for the resolved plan.
	 * @param service the plan service, or {@literal null} without a project.
	 */
	public void update(AnActionEvent e, @Nullable UpgradePlanService service) {
		e.getPresentation().setEnabled(service != null && service.hasItems() && !service.isBusy());
	}

	@Override
	public ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void actionPerformed(AnActionEvent e) {

		Project project = e.getProject();
		if (project != null) {
			UpgradePlanService service = UpgradePlanService.getInstance(project);
			if (!service.isBusy()) {
				perform(project);
			}
		}
	}

	public abstract void perform(Project project);

}
