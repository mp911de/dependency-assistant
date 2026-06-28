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
import biz.paluch.dap.util.MessageBundle;
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
	 * Notify the user that release metadata was refreshed, reporting how many
	 * artifacts were updated and how long the refresh took.
	 *
	 * @param project the project to notify; must not be {@literal null}.
	 * @param updates the artifacts whose release metadata was refreshed; must not
	 * be {@literal null}.
	 * @param durationMs the refresh duration in milliseconds.
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
	 * Notify that a bulk dependency upgrade has been applied, offering to reverse
	 * only the out-of-bounds entries when at least one is flagged.
	 *
	 * @param updates the applied updates; an empty collection is a no-op.
	 * @param undoOutOfBounds reverse-applies only the out-of-bounds entries.
	 */
	static void updatesApplied(Project project, Collection<AppliedDependencyUpdate> updates,
			Runnable undoOutOfBounds) {

		if (updates.isEmpty()) {
			return;
		}

		List<AppliedDependencyUpdate> outOfBounds = updates.stream().filter(AppliedDependencyUpdate::outOfBounds)
				.toList();

		if (outOfBounds.isEmpty()) {
			updatesApplied(updates).notify(project);
		} else {
			updatesAppliedOutOfBounds(updates, outOfBounds, undoOutOfBounds).notify(project);
		}
	}

	private static Notification updatesApplied(Collection<AppliedDependencyUpdate> updates) {

		List<AppliedDependencyUpdate> outOfBounds = updates.stream().filter(AppliedDependencyUpdate::outOfBounds)
				.toList();

		StringBuilder message = new StringBuilder();
		renderApplied(updates, message);

		return new Notification(
				outOfBounds.isEmpty() ? UPGRADE_NOTIFICATIONS : STICKY_NOTIFICATION,
				MessageBundle.message("notification.dependencies-updates"), message.toString(),
				NotificationType.INFORMATION);
	}

	private static Notification updatesAppliedOutOfBounds(Collection<AppliedDependencyUpdate> updates,
			Collection<AppliedDependencyUpdate> outOfBounds,
			Runnable undoOutOfBounds) {

		StringBuilder message = new StringBuilder();
		renderApplied(updates, message);

		if (!outOfBounds.isEmpty()) {
			message.append(
					MessageBundle.message("notification.dependencies-updates.out-of-bounds", outOfBounds.size()));
			message.append("<ul>");
			for (AppliedDependencyUpdate update : outOfBounds) {
				message.append("<li>")
						.append(MessageBundle.message("notification.dependencies-updates.out-of-bounds.entry",
								update.displayLabel(), update.to()))
						.append("</li>");
			}
			message.append("</ul>");
		}

		Notification notification = new Notification(
				STICKY_NOTIFICATION,
				MessageBundle.message("notification.dependencies-updates"), message.toString(),
				NotificationType.INFORMATION);

		notification.addAction(NotificationAction.createSimpleExpiring(
				MessageBundle.message("notification.dependencies-updates.undo-out-of-bounds"),
				undoOutOfBounds));
		notification.addAction(NotificationAction.createSimple(
				MessageBundle.message("notification.dependencies-updates.dismiss"), notification::expire));

		return notification;
	}

	private static void renderApplied(Collection<AppliedDependencyUpdate> updates, StringBuilder message) {
		message.append(MessageBundle.message("notification.dependencies-updates.description", updates.size()));

		message.append("<ul>");
		for (AppliedDependencyUpdate update : updates) {
			message.append("<li>");
			if (update.to().isNewer(update.from())) {
				message.append(MessageBundle.message("notification.dependencies-updates.upgrade",
						update.displayLabel(), update.to()));
			}
			else if (update.from().isNewer(update.to())) {
				message.append(MessageBundle.message("notification.dependencies-updates.downgrade",
						update.displayLabel(), update.to()));
			}
			else {
				message.append(MessageBundle.message("notification.dependencies-updates.update",
						update.displayLabel(), update.to()));
			}
			message.append("</li>");
		}
		message.append("</ul>");
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
