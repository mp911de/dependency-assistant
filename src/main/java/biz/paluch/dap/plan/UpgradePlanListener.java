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

import java.util.EventListener;

import com.intellij.util.messages.Topic;

/**
 * Project message-bus listener notified when the Upgrade Plan presentation must
 * change. Implementations may subscribe only to the events they need.
 *
 * @author Mark Paluch
 */
interface UpgradePlanListener extends EventListener {

	/**
	 * Topic carrying {@link UpgradePlanListener} events on the project message bus.
	 */
	Topic<UpgradePlanListener> TOPIC = new Topic<>(UpgradePlanListener.class, Topic.BroadcastDirection.NONE);

	/**
	 * Notified after a reversible structural plan transition.
	 */
	default void planChanged() {
	}

	default void milestonesChanged() {
	}

	/**
	 * Notified when the persisted plan was replaced from outside the plan command
	 * model. UI listeners reload as for a structural change; the plan service also
	 * terminates the preceding undo history.
	 */
	default void planReplaced() {
		planChanged();
	}

	/**
	 * Notified when the materialized plan remains valid after a change, for example
	 * when a ticket was linked or an applied item left the plan. Listeners
	 * re-render the current items instead of reconstructing them. May arrive on a
	 * background thread.
	 */
	default void planItemChanged() {
	}

}
