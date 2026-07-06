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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Refresh the upgrade plan, reloading it from persisted state so fresh
 * releases, vulnerabilities, and rules are reflected.
 *
 * @author Mark Paluch
 */
public class RefreshPlanAction extends UpgradePlanAction {

	/**
	 * Enabled without items: a refresh is useful on an empty plan view as well.
	 */
	@Override
	void update(AnActionEvent e, @Nullable UpgradePlanService service) {
		e.getPresentation().setEnabled(service != null && !service.isBusy());
	}

	@Override
	void perform(Project project) {
		UpgradePlanService.getInstance(project).requestReload();
	}

}
