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
	 * Apply the given plan items using this implementation's transaction semantics.
	 *
	 * @param plan the plan items to apply in plan order.
	 * @param indicator the progress and cancellation indicator for the run.
	 * @return the applied updates.
	 * @throws VcsException if a required version-control operation fails.
	 * @implNote Implementations run on the invoking background thread and own their
	 * transaction-specific write boundary. They do not schedule UI work.
	 */
	AppliedUpdates apply(UpgradePlan plan, ProgressIndicator indicator) throws VcsException;

	/**
	 * Run the given consumer on each item in the given collection.
	 *
	 * @param items the collection of items to process.
	 * @param indicator the progress and cancellation indicator for the run.
	 * @param consumer the function to apply to each item.
	 * @throws VcsException if a required version-control operation fails.
	 */
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
