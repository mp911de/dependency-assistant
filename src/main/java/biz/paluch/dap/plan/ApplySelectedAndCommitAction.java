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

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jspecify.annotations.Nullable;

/**
 * Apply and commit selected items through {@link ApplyAllAndCommitAction}. An
 * empty selection does not expand to the whole plan.
 *
 * @author Mark Paluch
 */
public class ApplySelectedAndCommitAction extends ApplyAllAndCommitAction {

	@Override
	public void update(AnActionEvent e, @Nullable UpgradePlanService service) {

		super.update(e, service);

		if (PlanSelection.from(e).isEmpty()) {
			e.getPresentation().setEnabled(false);
		}
	}

	@Override
	public void actionPerformed(AnActionEvent e) {

		if (PlanSelection.from(e).isEmpty()) {
			return;
		}

		super.actionPerformed(e);
	}

}
