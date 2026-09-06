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
 * Plugin notification groups configured in {@code plugin.xml}.
 *
 * @author Mark Paluch
 */
public enum NotificationChannel {

	/**
	 * Outcomes of release metadata refreshes.
	 */
	RELEASE_METADATA("biz.paluch.dependency-assistant.release-metadata"),

	/**
	 * Release metadata prompts awaiting a decision.
	 */
	RELEASE_METADATA_PROMPT("biz.paluch.dependency-assistant.release-metadata-sticky"),

	/**
	 * Dependency upgrades applied from the editor or dependency check.
	 */
	UPGRADES("biz.paluch.dependency-assistant.upgrades"),

	/**
	 * Upgrade plan outcomes.
	 */
	PLAN("biz.paluch.dependency-assistant.plan"),

	/**
	 * Release notes after a plugin update.
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

	public String getGroupId() {
		return groupId;
	}

	/**
	 * Create a notification without showing it.
	 *
	 * @param content the notification content, which may contain HTML.
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
