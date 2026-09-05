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

package biz.paluch.dap.notify;

import com.intellij.notification.NotificationAction;
import com.intellij.openapi.project.Project;

/**
 * Builder for a notification whose wording and channel are fixed. Follow-up
 * actions are added one at a time; {@link #notify(Project)} shows the balloon.
 *
 * @author Mark Paluch
 * @see Notifications
 * @see NotificationActions
 */
public interface NotificationBuilder {

	/**
	 * Offer a follow-up action on the notification.
	 *
	 * @param action the action to offer.
	 * @return {@code this} builder.
	 */
	NotificationBuilder action(NotificationAction action);

	/**
	 * Show the notification in the given project.
	 *
	 * @param project the project to notify.
	 */
	void notify(Project project);

}
