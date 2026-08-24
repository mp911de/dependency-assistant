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

import java.util.List;

import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;

/**
 * A reviewed upgrade ready to be captured into the {@link UpgradePlan}.
 *
 * <p>This is the hand-off contract between the dependency review and the
 * upgrade plan model.
 *
 * @author Mark Paluch
 * @see DependencyUpgradeCandidate
 * @see UpgradePlan
 */
public interface PlannedUpgrade {

	/**
	 * Return the initial display name captured for this upgrade.
	 *
	 * <p>The name is persisted and may later be renamed. It does not define item
	 * identity, which is derived from the captured members.
	 *
	 * @return the upgrade item name.
	 */
	String getDisplayName();

	/**
	 * Return the upgrades this planned upgrade contributes, in update order.
	 *
	 * @return non-empty list of upgrades in update order.
	 */
	List<DependencyUpgradeCandidate> getUpgradeCandidates();

}
