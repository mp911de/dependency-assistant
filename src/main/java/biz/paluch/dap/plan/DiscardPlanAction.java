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

import com.intellij.openapi.project.Project;

/**
 * Remove all planned upgrades from the plan. Deliberately unguarded: the plan
 * is a cheap, replaceable staging area, the transition is undoable, and the
 * last-resort confirmations sit on the apply actions instead.
 *
 * @author Mark Paluch
 */
public class DiscardPlanAction extends UpgradePlanAction {

	@Override
	void perform(Project project) {
		UpgradePlanService.getInstance(project).clear();
	}

}
