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

import java.util.Collection;

import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StepsProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ThrowableConsumer;

/**
 * Transaction boundary for applying an Upgrade Plan.
 *
 * @author Mark Paluch
 */
interface PlanUpdateApplier {

	/**
	 * Apply items using this implementation's transaction semantics.
	 * <p>The caller supplies a background thread. Implementations own the write
	 * boundary.
	 * @return updates that changed files.
	 * @throws VcsException if a required version-control operation fails.
	 */
	AppliedUpdates apply(UpgradePlan plan, ProgressIndicator indicator) throws VcsException;

	default void doWithItems(Collection<UpgradePlanItem> items, ProgressIndicator indicator,
			ThrowableConsumer<UpgradePlanItem, VcsException> consumer) throws VcsException {

		if (items.isEmpty()) {
			return;
		}

		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, items.size());
		for (UpgradePlanItem item : items) {

			String progress = MessageBundle.message("plan.apply.upgrading", item.getDisplayName(), item.getToVersion());

			indicator.checkCanceled();
			indicator.setText2(progress);

			consumer.consume(item);
			steps.nextStep();
		}
	}

}
