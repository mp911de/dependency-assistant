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
 * Reviewed upgrade ready for capture into the {@link UpgradePlan}.
 *
 * @author Mark Paluch
 */
public interface PlannedUpgrade {

	/**
	 * Return the initial display name. It may be renamed and does not define item
	 * identity.
	 */
	String getDisplayName();

	/**
	 * Return a non-empty list of contributing candidates in update order.
	 */
	List<DependencyUpgradeCandidate> getUpgradeCandidates();

}
