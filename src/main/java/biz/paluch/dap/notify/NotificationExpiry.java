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
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;

/**
 * Condition that expires a shown notification once its actions no longer apply.
 * Implementations subscribe on {@link #watch(Project, Notification)} and
 * release their subscription in {@link Notification#whenExpired(Runnable)}.
 *
 * @author Mark Paluch
 * @see NotificationBuilder#expireWhen(NotificationExpiry)
 */
public interface NotificationExpiry {

	/**
	 * Expire once the notification on {@code undoTransparentActionStarted}.
	 */
	static NotificationExpiry onUndoAction() {

		return (project, notification) -> {

			MessageBusConnection connection = project.getMessageBus().connect();
			notification.whenExpired(connection::disconnect);

			connection.subscribe(CommandListener.TOPIC, new CommandListener() {

				@Override
				public void undoTransparentActionStarted() {
					notification.expire();
				}

			});

			ActionManager actionManager = ActionManager.getInstance();
			connection.subscribe(AnActionListener.TOPIC, new AnActionListener() {

				@Override
				public void beforeActionPerformed(AnAction action, AnActionEvent event) {

					if (event.getProject() != project) {
						return;
					}
					String id = actionManager.getId(action);
					if (IdeActions.ACTION_UNDO.equals(id) || IdeActions.ACTION_REDO.equals(id)) {
						notification.expire();
					}
				}

			});
		};
	}

	/**
	 * Start watching on the EDT. Called after the notification is shown.
	 */
	void watch(Project project, Notification notification);

}
