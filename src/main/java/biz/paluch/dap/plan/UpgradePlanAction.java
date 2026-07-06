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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Base for Upgrade Plan toolbar actions registered in {@code plugin.xml}. Operates
 * on the {@link UpgradePlanService} resolved from the action's project; enabled
 * while the plan has items and no run is in flight. The tool window reflects any
 * resulting change through the {@link UpgradePlanListener} topic, so actions never
 * reach into the panel.
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
	 * Update the presentation from the resolved plan service, muted during an apply
	 * run to prevent double-actions.
	 *
	 * @param service the plan service; {@literal null} when the event carries no
	 * project.
	 */
	void update(AnActionEvent e, @Nullable UpgradePlanService service) {
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

	abstract void perform(Project project);

}
