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

import java.util.List;

import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;

/**
 * A reviewed upgrade ready to be captured into the {@link UpgradePlan}.
 *
 * <p>This is the hand-off contract between the dependency review dialog and the
 * plan model: a review row exposes itself as a {@code PlannedUpgrade} so the
 * plan can persist it without depending on review internals. Implemented by the
 * review rows {@code TableRow} (a single upgrade) and {@code GroupRow} (a
 * collapsed group whose members each contribute one upgrade). Callers pair a
 * planned upgrade with its pinned target
 * {@link biz.paluch.dap.artifact.ArtifactVersion} and hand both to the plan
 * through {@code UpgradePlanService} or {@link UpgradePlanToolWindowFactory}.
 *
 * @author Mark Paluch
 * @see DependencyUpgradeCandidate
 * @see UpgradePlan
 */
public interface PlannedUpgrade {

	/**
	 * Return the stable name persisted for this upgrade as its plan item.
	 *
	 * <p>The name identifies the item across sessions and drives its display label:
	 * the artifact id for a single upgrade, the governing rule's dependency name,
	 * or the derived group name for a collapsed group.
	 *
	 * @return the stable plan-item name; never {@literal null} or empty.
	 */
	String getName();

	/**
	 * Return the upgrades this planned upgrade contributes, in update order.
	 *
	 * <p>A single row yields exactly one upgrade; a collapsed group yields one per
	 * member so the plan can fan a chosen target out to every grouped dependency.
	 *
	 * @return an immutable, non-empty list of upgrades in update order.
	 */
	List<DependencyUpgradeCandidate> getUpgrades();

}
