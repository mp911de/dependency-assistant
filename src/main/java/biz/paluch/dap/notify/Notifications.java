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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.util.text.DateFormatUtil;

/**
 * Prepared plugin notifications. Add follow-up actions before calling
 * {@link NotificationBuilder#notify(Project)}.
 *
 * @author Mark Paluch
 * @see NotificationActions
 * @see UpgradeNotification
 */
public class Notifications {

	/**
	 * Create an informational notification on the general channel.
	 */
	public static NotificationBuilder info(String title, String content) {
		return info(NotificationChannel.GENERAL, title, content);
	}

	public static NotificationBuilder info(NotificationChannel channel, String title, String content) {
		return new Builder(channel.create(title, content, NotificationType.INFORMATION));
	}

	public static NotificationBuilder warning(NotificationChannel channel, String title, String content) {
		return new Builder(channel.create(title, content, NotificationType.WARNING));
	}

	/**
	 * Create an error notification with the default title on the general channel.
	 */
	public static NotificationBuilder error(String content) {
		return error(MessageBundle.message("error.title"), content);
	}

	/**
	 * Create an error notification on the general channel.
	 */
	public static NotificationBuilder error(String title, String content) {
		return error(NotificationChannel.GENERAL, title, content);
	}

	public static NotificationBuilder error(NotificationChannel channel, String title, String content) {
		return new Builder(channel.create(title, content, NotificationType.ERROR));
	}

	public static NotificationBuilder applied(NotificationChannel channel, UpgradeNotification wording) {
		return new Builder(wording.create(channel));
	}

	/**
	 * Return a displayable failure message. Wrappers that add no message are
	 * unwrapped. A blank or missing message falls back to the exception class name.
	 */
	public static String errorMessage(Throwable error) {

		Throwable cause = error;
		while (cause.getCause() != null
				&& (cause.getMessage() == null || cause.getMessage().equals(cause.getCause().toString()))) {
			cause = cause.getCause();
		}

		String message = cause.getMessage();
		return message != null && !message.isBlank() ? message : cause.getClass().getSimpleName();
	}

	/**
	 * Create a refresh summary that users can opt out of seeing.
	 *
	 * @param durationMs the refresh duration in milliseconds.
	 */
	public static NotificationBuilder releaseMetadataRefreshed(List<ArtifactId> updates, long durationMs) {

		int count = updates.size();
		String duration = NlsMessages.formatDuration(durationMs, 1, true);
		String detail = updates.stream().map(Object::toString)
				.collect(Collectors.joining(", "));

		Notification notification = NotificationChannel.RELEASE_METADATA.create(
				MessageBundle.message("action.refresh-releases.task.done.title"),
				MessageBundle.message("action.refresh-releases.task.done.message", count, duration, detail),
				NotificationType.INFORMATION);

		notification.configureDoNotAskOption("action.refresh-releases.task.done.message",
				MessageBundle.message("notification.do-not-show-again"));

		return new Builder(notification);
	}

	/**
	 * Create a prompt for missing release metadata. Callers supply the refresh and
	 * dismissal actions.
	 */
	public static NotificationBuilder releaseMetadataUnavailable() {

		Notification notification = NotificationChannel.RELEASE_METADATA_PROMPT.create(
				MessageBundle.message("notification.cache.no.releases.title"),
				MessageBundle.message("notification.cache.no.releases.description"),
				NotificationType.INFORMATION);

		notification.configureDoNotAskOption("notification.cache.no.releases",
				MessageBundle.message("notification.do-not-show-again"));
		notification.setSuggestionType(true);
		notification.setIcon(DependencyAssistantIcons.ICON);

		return new Builder(notification);
	}

	/**
	 * Create a prompt for stale release metadata. Callers supply the refresh and
	 * dismissal actions.
	 */
	public static NotificationBuilder releaseMetadataStale(Instant cacheUpdate) {

		String content = MessageBundle.message("notification.cache.stale.releases.description",
				getDurationMessage(cacheUpdate));

		Notification notification = NotificationChannel.RELEASE_METADATA_PROMPT.create(
				MessageBundle.message("notification.cache.stale.releases.title"), content,
				NotificationType.INFORMATION);

		notification.configureDoNotAskOption("notification.cache.stale.releases",
				MessageBundle.message("notification.do-not-show-again"));
		notification.setSuggestionType(true);
		notification.setIcon(DependencyAssistantIcons.ICON);

		return new Builder(notification);
	}

	private static String getDurationMessage(Instant cacheUpdate) {
		Instant now = Instant.now();
		Duration cacheUpdated = Duration.between(cacheUpdate, now);
		if (cacheUpdated.toDays() > 60) {
			return MessageBundle.message("cache-age.long-time-ago");
		}
		return DateFormatUtil.formatBetweenDates(cacheUpdate.toEpochMilli(), now.toEpochMilli());
	}

	private static class Builder implements NotificationBuilder {

		private final Notification notification;

		Builder(Notification notification) {
			this.notification = notification;
		}

		@Override
		public NotificationBuilder action(NotificationAction action) {
			notification.addAction(action);
			return this;
		}

		@Override
		public void notify(Project project) {
			notification.notify(project);
		}

	}

}
