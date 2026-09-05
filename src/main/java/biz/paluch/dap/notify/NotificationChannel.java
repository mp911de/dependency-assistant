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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;

/**
 * Notification channels of the plugin, one per registered notification group.
 *
 * <p>A channel is the user-facing address of a bounded context: it names the
 * group listed under Settings | Notifications and fixes the display type
 * (balloon or sticky balloon) and logging behaviour declared in
 * {@code plugin.xml}. Callers create notifications through a channel instead of
 * repeating group ids.
 *
 * @author Mark Paluch
 */
public enum NotificationChannel {

	/**
	 * Outcomes of release metadata refreshes. Transient balloon.
	 */
	RELEASE_METADATA("biz.paluch.dependency-assistant.release-metadata"),

	/**
	 * Release metadata prompts awaiting a decision. Sticky balloon.
	 */
	RELEASE_METADATA_PROMPT("biz.paluch.dependency-assistant.release-metadata-sticky"),

	/**
	 * Dependency upgrades applied from the editor or the dependency check. Logged.
	 */
	UPGRADES("biz.paluch.dependency-assistant.upgrades"),

	/**
	 * Upgrade plan runs: apply, commit, push, and their failures.
	 */
	PLAN("biz.paluch.dependency-assistant.plan"),

	/**
	 * Plugin release notes after a plugin update. Sticky balloon, logged.
	 */
	PLUGIN_UPDATE("biz.paluch.dependency-assistant.plugin-update"),

	/**
	 * Errors and confirmations without a dedicated channel.
	 */
	GENERAL("biz.paluch.dependency-assistant.info");

	private final String groupId;

	NotificationChannel(String groupId) {
		this.groupId = groupId;
	}

	/**
	 * Return the registered notification group id.
	 */
	public String getGroupId() {
		return groupId;
	}

	/**
	 * Create a notification with title and content on this channel.
	 *
	 * @param title the notification title.
	 * @param content the notification content; HTML is rendered.
	 * @param type the notification type.
	 * @return the notification, not yet shown.
	 */
	public Notification create(String title, String content, NotificationType type) {
		return getGroup().createNotification(title, content, type);
	}

	NotificationGroup getGroup() {

		NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(groupId);
		if (group == null) {
			throw new IllegalStateException("Notification group '%s' is not registered".formatted(groupId));
		}

		return group;
	}

}
