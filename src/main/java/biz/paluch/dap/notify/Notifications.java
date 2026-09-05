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
 * Entry point for plugin notifications. Every method words a notification on a
 * {@link NotificationChannel channel} and returns a {@link NotificationBuilder}
 * that accepts follow-up {@link NotificationActions actions} before the balloon
 * is shown.
 *
 * @author Mark Paluch
 * @see NotificationChannel
 * @see NotificationActions
 * @see UpgradeNotification
 */
public class Notifications {

	/**
	 * Word an informational notification on the general channel.
	 *
	 * @param title the balloon title.
	 * @param content the balloon body text.
	 * @return the builder.
	 */
	public static NotificationBuilder info(String title, String content) {
		return info(NotificationChannel.GENERAL, title, content);
	}

	/**
	 * Word an informational notification.
	 *
	 * @param channel the channel to notify on.
	 * @param title the balloon title.
	 * @param content the balloon body text.
	 * @return the builder.
	 */
	public static NotificationBuilder info(NotificationChannel channel, String title, String content) {
		return new Builder(channel.create(title, content, NotificationType.INFORMATION));
	}

	/**
	 * Word a warning notification.
	 *
	 * @param channel the channel to notify on.
	 * @param title the balloon title.
	 * @param content the balloon body text.
	 * @return the builder.
	 */
	public static NotificationBuilder warning(NotificationChannel channel, String title, String content) {
		return new Builder(channel.create(title, content, NotificationType.WARNING));
	}

	/**
	 * Word an error notification under the default error title on the general
	 * channel.
	 *
	 * @param content the error content.
	 * @return the builder.
	 */
	public static NotificationBuilder error(String content) {
		return error(MessageBundle.message("error.title"), content);
	}

	/**
	 * Word an error notification on the general channel.
	 *
	 * @param title the error title.
	 * @param content the error content.
	 * @return the builder.
	 */
	public static NotificationBuilder error(String title, String content) {
		return error(NotificationChannel.GENERAL, title, content);
	}

	/**
	 * Word an error notification.
	 *
	 * @param channel the channel to notify on.
	 * @param title the error title.
	 * @param content the error content.
	 * @return the builder.
	 */
	public static NotificationBuilder error(NotificationChannel channel, String title, String content) {
		return new Builder(channel.create(title, content, NotificationType.ERROR));
	}

	/**
	 * Word a notification about applied dependency upgrades.
	 *
	 * @param channel the channel to notify on.
	 * @param wording the applied-upgrade wording.
	 * @return the builder.
	 */
	public static NotificationBuilder applied(NotificationChannel channel, UpgradeNotification wording) {
		return new Builder(wording.create(channel));
	}

	/**
	 * Return a displayable message for the given error.
	 *
	 * <p>Wrappers that add no message of their own, such as
	 * {@code UncheckedIOException} or {@code RuntimeException(cause)}, are
	 * unwrapped so the message describes the failure, not the wrapper.
	 *
	 * @param error the failure to describe.
	 * @return the non-blank message of the innermost meaningful cause, or its class
	 * name when no message is available.
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
	 * Word the outcome of a release metadata refresh: how many artifacts were
	 * updated and how long the refresh took. Users can opt out of this balloon.
	 *
	 * @param updates the artifacts whose release metadata was refreshed.
	 * @param durationMs the refresh duration in milliseconds.
	 * @return the builder.
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
	 * Word the prompt that release metadata is unavailable. Callers offer
	 * {@link NotificationActions#refreshReleaseMetadata(Runnable) refresh} and
	 * {@link NotificationActions#notNow(Runnable) not now}.
	 *
	 * @return the builder.
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
	 * Word the prompt that release metadata is probably old. Callers offer
	 * {@link NotificationActions#refreshReleaseMetadata(Runnable) refresh} and
	 * {@link NotificationActions#notNow(Runnable) not now}.
	 *
	 * @param cacheUpdate when the release cache was last updated.
	 * @return the builder.
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
