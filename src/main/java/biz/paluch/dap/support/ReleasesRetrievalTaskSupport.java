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
package biz.paluch.dap.support;

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.DependencyUpdateOption;
import biz.paluch.dap.artifact.DependencyUpdates;

import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

/**
 * Suppot class for a background task that refreshes the release state for each used dependency.
 *
 * @author Mark Paluch
 */
public abstract class ReleasesRetrievalTaskSupport extends Task.Backgroundable {

	private final Project project;

	public ReleasesRetrievalTaskSupport(Project project) {
		super(project, MessageBundle.message("action.update.releases.task"), true);
		this.project = project;
	}

	@Override
	public void onSuccess() {

		NotificationGroup group = NotificationGroupManager.getInstance()
				.getNotificationGroup("biz.paluch.dependency-assistant.releases");
		DependencyUpdates result = getUpdates();
		if (result == null || project.isDisposed() || group == null) {
			return;
		}
		int count = result.updates().size();
		String duration = NlsMessages.formatDuration(getDuration(), 1, true);
		String detail = result.updates().stream().map(DependencyUpdateOption::getArtifactId).map(Object::toString)
				.collect(Collectors.joining(System.lineSeparator()));
		Notification notification = group.createNotification(MessageBundle.message("action.update.releases.done.title"),
				MessageBundle.message("action.update.releases.done", count, duration, detail), NotificationType.INFORMATION);
		DaemonCodeAnalyzer.getInstance(project).restart(MessageBundle.message("action.update.releases.done.title"));
		Notifications.Bus.notify(notification, project);
	}

	@Override
	public void onThrowable(Throwable error) {
		Messages.showMessageDialog(project, MessageBundle.message("action.check.dependencies.error", error.getMessage()),
				MessageBundle.message("action.check.dependencies.error.title"), Messages.getErrorIcon());
	}

	protected abstract @Nullable DependencyUpdates getUpdates();

	protected abstract long getDuration();

}
