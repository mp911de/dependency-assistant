/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap.support;

import java.util.function.Function;
import java.util.stream.Collectors;

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.DependencyUpdateOption;
import biz.paluch.dap.artifact.DependencyUpdates;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;

/**
 * Utility to manage Dependency Assistant notifications.
 * @author Mark Paluch
 */
public class Notifications {

	private static final String BALLOON_NOTIFICATION = "biz.paluch.dependency-assistant.releases";

	private static final String STICKY_NOTIFICATION = "biz.paluch.dependency-assistant.releases-sticky";

	/**
	 * Notify the user that release metadata is unavailable and offer to update the
	 * cache.
	 */
	public static void releaseMetadataRefreshed(Project project, DependencyUpdates updates, long durationMs) {

		int count = updates.updates().size();
		String duration = NlsMessages.formatDuration(durationMs, 1, true);
		String detail = updates.updates().stream().map(DependencyUpdateOption::getArtifactId).map(Object::toString)
				.collect(Collectors.joining(", "));

		Notification notification = new Notification(
				BALLOON_NOTIFICATION, MessageBundle.message("action.update.releases.done.title"),
				MessageBundle.message("action.update.releases.done", count, duration, detail),
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
				.addAction(NotificationAction.createSimple(MessageBundle.message("notification.dismiss"),
						notification::expire))
				.notify(project);
	}

}
