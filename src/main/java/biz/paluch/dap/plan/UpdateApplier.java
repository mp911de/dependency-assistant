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

import biz.paluch.dap.support.UpgradeResult;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;

/**
 * Plain apply strategy. Owns the platform command and semantic plan transition,
 * while {@link FileUpdateEngine} owns the underlying file mutations.
 *
 * @author Mark Paluch
 */
class UpdateApplier implements PlanUpdateApplier {

	private final UpgradePlanService service;

	private final FileUpdateEngine engine;

	UpdateApplier(UpgradePlanService service) {
		this(service, new FileUpdateEngine(service.getProject()));
	}

	UpdateApplier(UpgradePlanService service, FileUpdateEngine engine) {
		this.service = service;
		this.engine = engine;
	}

	@Override
	public int apply(UpgradePlan plan, ProgressIndicator indicator) throws VcsException {

		return doWithItems(plan.toList(), indicator, it -> {

			String commandName = MessageBundle.message("plan.apply.upgrade", it.getDisplayName(),
					it.getToVersion());

			return WriteCommandAction.writeCommandAction(service.getProject())
					.withName(commandName)
					.withGlobalUndo()
					.compute(() -> {
						UpgradeResult result = engine.apply(plan.getScope(), it.createUpdates());
						if (result.hasChanges()) {
							service.removeItem(it);
						}
						return result;
					});
		});
	}

}
