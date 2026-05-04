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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
						.createSimpleExpiring(MessageBundle.message("notification.action.update.releases"), () -> {
							ProgressManager.getInstance().run(taskFunction.apply(project));
						}))
				.addAction(NotificationAction.createSimple(MessageBundle.message("notification.not-now"),
						notification::expire))
				.notify(project);
	}

	/**
	 * Notify the user that release metadata is probably old and offer to update the
	 * cache.
	 */
	public static void releaseMetadataStale(Project project, Instant cacheUpdate,
			Function<Project, Task> taskFunction) {

		String ago = DateFormatUtil.formatBetweenDates(cacheUpdate.toEpochMilli(), Instant.now().toEpochMilli());

		Notification notification = new Notification(
				STICKY_NOTIFICATION, MessageBundle.message("notification.cache.stale.releases.title"),
				MessageBundle.message("notification.cache.stale.releases.description", ago),
				NotificationType.INFORMATION);

		notification
				.setSuggestionType(true)
				.addAction(NotificationAction
						.createSimpleExpiring(MessageBundle.message("notification.action.update.releases"), () -> {
							ProgressManager.getInstance().run(taskFunction.apply(project));
						}))
				.addAction(NotificationAction.createSimple(MessageBundle.message("notification.not-now"),
						notification::expire))
				.notify(project);
	}

	/**
	 * Return a localized age description for the given instant range.
	 */
	public static String agoText(Instant pastInstant, Instant nowInstant, ZoneId zone) {

		LocalDate past = pastInstant.atZone(zone).toLocalDate();
		LocalDate now = nowInstant.atZone(zone).toLocalDate();

		long days = ChronoUnit.DAYS.between(past, now);

		if (days < 0) {
			return DateFormatUtil.formatBetweenDates(pastInstant.toEpochMilli(), nowInstant.toEpochMilli());
		}

		if (days == 0 || days == 1) {
			return DateFormatUtil.formatPrettyDateTime(pastInstant.toEpochMilli());
		}

		if (days < 7) {
			return MessageBundle.message("cache-age.days-ago", days);
		}

		if (days < 14) {
			return MessageBundle.message("cache-age.a-week-ago");
		}

		if (days < 30) {
			return MessageBundle.message("cache-age.days-ago", days);
		}

		long months = ChronoUnit.MONTHS.between(past.withDayOfMonth(1),
				now.withDayOfMonth(1));

		if (months <= 1) {
			return MessageBundle.message("cache-age.a-month-ago", days);
		}

		return MessageBundle.message("cache-age.months-ago", months);
	}

}
