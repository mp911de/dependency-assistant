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

import biz.paluch.dap.util.MessageBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Upgrade Plan balloon notifications, funneled through the plan notification group
 * so apply, push, and failure messages share one channel.
 *
 * @author Mark Paluch
 */
class PlanNotifications {

	private static final String GROUP = "biz.paluch.dependency-assistant.plan";

	private final NotificationGroup group = NotificationGroupManager.getInstance()
			.getNotificationGroup(GROUP);

	PlanNotifications() {
	}

	/**
	 * Notify that the plan was applied, offering the follow-up actions of the run.
	 * Each action is one-shot: choosing it expires the notification.
	 *
	 * @param commit whether the upgrades were committed.
	 * @param applied the number of applied upgrades.
	 * @param push pushes the committed changes; {@literal null} when not offered.
	 * @param unshelve restores the shelf created for the run; {@literal null} when
	 * not offered.
	 */
	void applied(Project project, boolean commit, int applied, @Nullable Runnable push, @Nullable Runnable unshelve) {

		Notification notification;
		if (applied == 0) {
			notification = group.createNotification(MessageBundle.message("plugin.name"),
					MessageBundle.message("plan.apply.summary.none"), NotificationType.INFORMATION);
		} else {
			notification = group
					.createNotification(MessageBundle.message(commit ? "plan.apply.summary.committed"
							: "plan.apply.summary.applied", applied), NotificationType.INFORMATION)
					.setImportant(true);
			addAction(notification, "plan.apply.push", push);
			addAction(notification, "plan.apply.unshelve", unshelve);
		}

		notification.notify(project);
	}

	private static void addAction(Notification notification, String key, @Nullable Runnable action) {

		if (action != null) {
			notification.addAction(NotificationAction.createSimpleExpiring(MessageBundle.message(key), action));
		}
	}

	/**
	 * Notify with an informational plan message under the plugin-name title.
	 */
	void info(Project project, String message) {
		group.createNotification(MessageBundle.message("plugin.name"), message, NotificationType.INFORMATION)
				.notify(project);
	}

	/**
	 * Communicate a plan failure through the balloon, carrying the failure reason as
	 * the balloon content.
	 */
	void error(Project project, String message, Throwable error) {
		error(project, message, reason(error));
	}

	/**
	 * Communicate an apply failure and offer to restore a shelf created for the
	 * failed run.
	 */
	void error(Project project, String message, Throwable error, @Nullable Runnable unshelve) {

		Notification notification = group.createNotification(message, reason(error), NotificationType.ERROR);
		addAction(notification, "plan.apply.unshelve", unshelve);
		notification.notify(project);
	}

	private void error(Project project, String message, String reason) {
		group.createNotification(message, reason, NotificationType.ERROR)
				.notify(project);
	}

	/**
	 * Communicate a cancelled apply and offer to restore a shelf created for the
	 * cancelled run.
	 */
	void cancelled(Project project, @Nullable Runnable unshelve) {

		Notification notification = group.createNotification(MessageBundle.message("plugin.name"),
				MessageBundle.message("plan.apply.cancelled"), NotificationType.INFORMATION);
		addAction(notification, "plan.apply.unshelve", unshelve);
		notification.notify(project);
	}

	void warning(Project project, String message, String reason) {
		group.createNotification(message, reason, NotificationType.WARNING)
				.notify(project);
	}

	// unwrap wrappers that add no message of their own (UncheckedIOException,
	// RuntimeException(cause)) so the balloon shows the failure, not the wrapper
	private static String reason(Throwable error) {

		Throwable cause = error;
		while (cause.getCause() != null
				&& (cause.getMessage() == null || cause.getMessage().equals(cause.getCause().toString()))) {
			cause = cause.getCause();
		}

		return cause.getMessage() != null ? cause.getMessage() : cause.getClass()
				.getSimpleName();
	}

}
