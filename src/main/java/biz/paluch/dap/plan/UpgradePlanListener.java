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

import java.util.EventListener;

import com.intellij.util.messages.Topic;

/**
 * Project message-bus listener notified when the Upgrade Plan presentation must
 * change. Implementations may subscribe only to the events they need.
 *
 * @author Mark Paluch
 */
interface UpgradePlanListener extends EventListener {

	Topic<UpgradePlanListener> TOPIC = new Topic<>(UpgradePlanListener.class, Topic.BroadcastDirection.NONE);

	/**
	 * Rebuild the live plan after a structural change or reload request.
	 */
	default void planChanged() {
	}

	default void milestonesChanged() {
	}

	/**
	 * The bound ticket system changed or became unavailable.
	 */
	default void ticketSystemChanged() {
	}

	/**
	 * Persisted state was replaced outside the plan command model. Reload the view
	 * because prior materialization is stale.
	 */
	default void planReplaced() {
		planChanged();
	}

	/**
	 * Refresh current materialized items without reconstructing them. This event
	 * may arrive on a background thread.
	 */
	default void planItemChanged() {
	}

}
