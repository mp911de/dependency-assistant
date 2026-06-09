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

package biz.paluch.dap.assistant;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.text.DateFormatUtil;

/**
 * Utility to manage Dependency Assistant notifications.
 *
 * @author Mark Paluch
 */
public class Notifications {

	private static final String BALLOON_NOTIFICATION = "biz.paluch.dependency-assistant.releases";

	private static final String STICKY_NOTIFICATION = "biz.paluch.dependency-assistant.releases-sticky";

	private static final String ERROR_NOTIFICATION = "biz.paluch.dependency-assistant.errors";

	private static final String UPGRADE_NOTIFICATIONS = "biz.paluch.dependency-assistant.upgrades";

	/**
	 * Notify the user about an error.
	 */
	public static void error(Project project, String content) {
		error(project, MessageBundle.message("error.title"), content);
	}

	/**
	 * Notify the user about an error.
	 */
	public static void error(Project project, String title, String content) {

		Notification notification = new Notification(ERROR_NOTIFICATION, title, content, NotificationType.ERROR);
		notification.notify(project);
	}

	/**
	 * Notify the user with an informational balloon.
	 * @param project the project to notify in.
	 * @param title the balloon title.
	 * @param content the balloon body text.
	 */
	public static void info(Project project, String title, String content) {

		Notification notification = new Notification(BALLOON_NOTIFICATION, title, content,
				NotificationType.INFORMATION);
		notification.notify(project);
	}

	/**
	 * Return a displayable message for the given error.
	 */
	public static String errorMessage(Throwable error) {

		String message = error.getMessage();
		return message != null && !message.isBlank() ? message : error.getClass().getSimpleName();
	}

	/**
	 * Notify the user that release metadata is unavailable and offer to update the
	 * cache.
	 */
	public static void releaseMetadataRefreshed(Project project, List<ArtifactId> updates, long durationMs) {

		int count = updates.size();
		String duration = NlsMessages.formatDuration(durationMs, 1, true);
		String detail = updates.stream().map(Object::toString)
				.collect(Collectors.joining(", "));

		Notification notification = new Notification(
				BALLOON_NOTIFICATION, MessageBundle.message("action.refresh-releases.task.done.title"),
				MessageBundle.message("action.refresh-releases.task.done.message", count, duration, detail),
				NotificationType.INFORMATION);
		notification.notify(project);
	}

	/**
	 * Notify the user that release metadata is unavailable and offer to update the
	 * cache.
	 */
	public static void releaseMetadataUnavailable(Project project, Function<Project, Task> taskFunction) {

		Notification notification = new Notification(
				STICKY_NOTIFICATION, MessageBundle.message("notification.cache.no.releases.title"),
				MessageBundle.message("notification.cache.no.releases.description"),
				NotificationType.INFORMATION);

		notification
				.setSuggestionType(true)
				.addAction(NotificationAction
						.createSimpleExpiring(MessageBundle.message("notification.action.refresh-releases-metadata"),
								() -> {
									ProgressManager.getInstance().run(taskFunction.apply(project));
								}))
				.addAction(NotificationAction.createSimple(MessageBundle.message("notification.not-now"),
						notification::expire))
				.notify(project);
	}

	/**
	 * Notify that a dependency upgrade has been applied.
	 */
	static void updatesApplied(Project project, Collection<AppliedDependencyUpdate> updates) {

		if (updates.isEmpty()) {
			return;
		}

		StringBuilder message = new StringBuilder();
		message.append(MessageBundle.message("notification.dependencies-updates.description", updates.size()));

		message.append("<ul>");
		for (AppliedDependencyUpdate update : updates) {
			message.append("<li>");
			if (update.to().isNewer(update.from())) {
				message.append(MessageBundle.message("notification.dependencies-updates.upgrade",
						update.getDependencyName(), update.to()));
			}
			else if (update.from().isNewer(update.to())) {
				message.append(MessageBundle.message("notification.dependencies-updates.downgrade",
						update.getDependencyName(), update.to()));
			}
			else {
				message.append(MessageBundle.message("notification.dependencies-updates.update",
						update.getDependencyName(), update.to()));
			}
			message.append("</li>");
		}
		message.append("</ul>");

		Notification notification = new Notification(
				UPGRADE_NOTIFICATIONS, MessageBundle.message("notification.dependencies-updates"), message.toString(), NotificationType.INFORMATION);

		notification.notify(project);
	}

	/**
	 * Notify the user that release metadata is probably old and offer to update the
	 * cache.
	 */
	public static void releaseMetadataStale(Project project, Instant cacheUpdate,
			Function<Project, Task> taskFunction) {

		String ago = getDurationMessage(cacheUpdate);

		Notification notification = new Notification(
				STICKY_NOTIFICATION, MessageBundle.message("notification.cache.stale.releases.title"),
				MessageBundle.message("notification.cache.stale.releases.description", ago),
				NotificationType.INFORMATION);

		notification
				.setSuggestionType(true)
				.addAction(NotificationAction
						.createSimpleExpiring(MessageBundle.message("notification.action.refresh-releases-metadata"),
								() -> {
									ProgressManager.getInstance().run(taskFunction.apply(project));
								}))
				.addAction(NotificationAction.createSimple(MessageBundle.message("notification.not-now"),
						notification::expire))
				.notify(project);
	}

	private static String getDurationMessage(Instant cacheUpdate) {
		Instant now = Instant.now();
		Duration cacheUpdated = Duration.between(cacheUpdate, now);
		if (cacheUpdated.toDays() > 60) {
			return MessageBundle.message("cache-age.long-time-ago");
		}
		return DateFormatUtil.formatBetweenDates(cacheUpdate.toEpochMilli(), now.toEpochMilli());
	}

}
