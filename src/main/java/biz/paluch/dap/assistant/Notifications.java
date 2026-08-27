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
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.DateFormatUtil;

/**
 * Notification facade for dependency checks, release metadata, and applied
 * updates.
 *
 * <p>Notification operations dispatch to the group registered for their
 * lifecycle. Release-cache prompts own their refresh and dismissal actions.
 * Applied-update notifications own the platform Undo action and, when needed, a
 * separate flagged-entry reversal action supplied by the caller.
 *
 * @author Mark Paluch
 */
public class Notifications {

	private static final String BALLOON_REFRESHED = "biz.paluch.dependency-assistant.releases-refreshed";

	private static final String STICKY_NOTIFICATION = "biz.paluch.dependency-assistant.releases-sticky";

	private static final String ERROR_NOTIFICATION = "biz.paluch.dependency-assistant.errors";

	private static final String UPGRADE_NOTIFICATIONS = "biz.paluch.dependency-assistant.upgrades";

	/**
	 * Notify the user about an error under the default error title.
	 *
	 * @param project the project to notify.
	 * @param content the error content.
	 */
	public static void error(Project project, String content) {
		error(project, MessageBundle.message("error.title"), content);
	}

	/**
	 * Notify the user about an error.
	 *
	 * @param project the project to notify.
	 * @param title the error title.
	 * @param content the error content.
	 */
	public static void error(Project project, String title, String content) {

		Notification notification = new Notification(ERROR_NOTIFICATION, title, content, NotificationType.ERROR);
		notification.notify(project);
	}

	/**
	 * Notify the user with an informational balloon.
	 *
	 * @param project the project to notify in.
	 * @param title the balloon title.
	 * @param content the balloon body text.
	 */
	public static void info(Project project, String title, String content) {

		Notification notification = new Notification(BALLOON_REFRESHED, title, content,
				NotificationType.INFORMATION);
		notification.notify(project);
	}

	/**
	 * Return a displayable message for the given error.
	 *
	 * @param error the failure to describe.
	 * @return the non-blank exception message, or the exception class name when no
	 * message is available.
	 */
	public static String errorMessage(Throwable error) {

		String message = error.getMessage();
		return message != null && !message.isBlank() ? message : error.getClass().getSimpleName();
	}

	/**
	 * Notify the user that release metadata was refreshed, reporting how many
	 * artifacts were updated and how long the refresh took.
	 *
	 * @param project the project to notify.
	 * @param updates the artifacts whose release metadata was refreshed.
	 * @param durationMs the refresh duration in milliseconds.
	 */
	public static void releaseMetadataRefreshed(Project project, List<ArtifactId> updates, long durationMs) {

		int count = updates.size();
		String duration = NlsMessages.formatDuration(durationMs, 1, true);
		String detail = updates.stream().map(Object::toString)
				.collect(Collectors.joining(", "));

		Notification notification = new Notification(
				BALLOON_REFRESHED, MessageBundle.message("action.refresh-releases.task.done.title"),
				MessageBundle.message("action.refresh-releases.task.done.message", count, duration, detail),
				NotificationType.INFORMATION);
		notification.notify(project);
	}

	/**
	 * Notify the user that release metadata is unavailable and offer to update the
	 * cache.
	 *
	 * <p>The task factory is evaluated only when the user chooses refresh. Choosing
	 * not now invokes the dismissal callback and expires the notification.
	 *
	 * @param project the project to notify.
	 * @param taskFunction the factory for the refresh task.
	 * @param notNow the callback that records dismissal of the prompt.
	 */
	public static void releaseMetadataUnavailable(Project project, Function<Project, Task> taskFunction,
			Runnable notNow) {

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
						() -> {
							notNow.run();
							notification.expire();
						}))
				.notify(project);
	}

	/**
	 * Notify that dependency updates have been applied.
	 *
	 * <p>An empty accumulator produces no notification. Every emitted notification
	 * offers the platform's current Undo operation. When at least one summary is
	 * flagged for compliance or a major crossing, the notification also offers the
	 * caller-supplied operation that reverses only flagged entries.
	 *
	 * @param project the project to notify.
	 * @param updates the applied updates. An empty accumulator is a no-op.
	 * @param undoFlagged the operation that reverse-applies only flagged entries.
	 */
	public static void updatesApplied(Project project, AppliedUpdates updates,
			Runnable undoFlagged) {

		if (updates.isEmpty()) {
			return;
		}

		List<AppliedDependencyUpdate> flagged = updates.stream().filter(AppliedDependencyUpdate::isFlagged)
				.toList();

		Notification notification = flagged.isEmpty() ? updatesApplied(updates)
				: updatesAppliedFlagged(updates, flagged, undoFlagged);

		Runnable undo = () -> {

			UndoManager undoManager = UndoManager.getInstance(project);
			if (undoManager.isUndoAvailable(null)) {
				undoManager.undo(null);
			}
		};

		notification.addAction(NotificationAction.createSimpleExpiring(
				MessageBundle.message("notification.undo"), undo));
		notification.notify(project);
	}

	private static Notification updatesApplied(AppliedUpdates updates) {
		return new Notification(UPGRADE_NOTIFICATIONS, getTitle(updates),
				StringUtil.escapeXmlEntities(updates.toString()),
				NotificationType.INFORMATION);
	}

	private static Notification updatesAppliedFlagged(AppliedUpdates updates,
			Collection<AppliedDependencyUpdate> flagged,
			Runnable undoFlagged) {

		StringBuilder message = new StringBuilder();
		message.append("<p>").append(updates).append("</p>");

		List<AppliedDependencyUpdate> outOfBounds = flagged.stream()
				.filter(update -> update.flag() == AppliedDependencyUpdate.Flag.COMPLIANCE).toList();
		if (!outOfBounds.isEmpty()) {
			message.append("<br/><p>").append(updates.renderOutOfBounds(
					MessageBundle.message("notification.out-of-bounds", outOfBounds.size()),
					outOfBounds)).append("</p>");
		}

		List<AppliedDependencyUpdate> majorCrossings = flagged.stream()
				.filter(update -> update.flag() == AppliedDependencyUpdate.Flag.MAJOR_CROSSING).toList();
		if (!majorCrossings.isEmpty()) {
			message.append("<br/><p>").append(updates.renderOutOfBounds(
					MessageBundle.message("notification.major-crossing", majorCrossings.size()),
					majorCrossings)).append("</p>");
		}

		Notification notification = new Notification(STICKY_NOTIFICATION, getTitle(updates), message.toString(),
				NotificationType.INFORMATION);

		notification.addAction(NotificationAction.createSimpleExpiring(
				MessageBundle.message("notification.undo-out-of-bounds"),
				undoFlagged));

		return notification;
	}

	/**
	 * Return the localized applied-update title for the given summaries.
	 *
	 * @param updates the summaries whose title is requested.
	 * @return a dependency-specific title for one summary, or a counted title for
	 * several summaries.
	 */
	public static String getTitle(AppliedUpdates updates) {
		if (updates.size() == 1) {
			AppliedDependencyUpdate item = updates.iterator().next();
			return MessageBundle.message("notification.updated", item.displayName());
		}
		return MessageBundle.message("notification.updates", updates.size());
	}

	/**
	 * Notify the user that release metadata is probably old and offer to update the
	 * cache.
	 *
	 * <p>The task factory is evaluated only when the user chooses refresh. Choosing
	 * not now invokes the dismissal callback and expires the notification.
	 *
	 * @param project the project to notify.
	 * @param cacheUpdate when the release cache was last updated.
	 * @param taskFunction the factory for the refresh task.
	 * @param notNow the callback that records dismissal of the prompt.
	 */
	public static void releaseMetadataStale(Project project, Instant cacheUpdate,
			Function<Project, Task> taskFunction, Runnable notNow) {

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
						() -> {
							notNow.run();
							notification.expire();
						}))
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
