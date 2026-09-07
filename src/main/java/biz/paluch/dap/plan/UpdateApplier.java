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

import java.io.IOException;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.upgrade.FileUpdateEngine;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * Apply plan items with platform undo and no VCS commit.
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
	public AppliedUpdates apply(UpgradePlan plan, ProgressIndicator indicator) throws IOException {

		AppliedUpdates appliedUpdates = new AppliedUpdates();

		FileScope scope = plan.getScope();
		doWithItems(plan.toList(), indicator, it -> {

			List<DependencyUpdate> updates = it.createUpdates();

			WriteCommandAction.writeCommandAction(service.getProject())
					.withName(getCommandName(it.getDisplayName(), it.getToVersion()))
					.withGlobalUndo()
					.run(() -> {
						engine.apply(scope, updates, applied -> {
							appliedUpdates.record(scope, applied, it.getDisplayName());
							service.removeItem(it);
						});
					});
		});

		return appliedUpdates;
	}

	static String getCommandName(String displayName, ArtifactVersion version) {
		return MessageBundle.message("plan.apply.upgrade", displayName, version);
	}

}
