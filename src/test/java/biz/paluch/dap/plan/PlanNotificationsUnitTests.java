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

package biz.paluch.dap.plan;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PlanNotifications}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class PlanNotificationsUnitTests {

	@Test
	void cancelledApplyOffersShelfRecovery(Project project) {

		AtomicBoolean recovered = new AtomicBoolean();
		Notification notification = capture(project,
				() -> new PlanNotifications().cancelled(project, () -> recovered.set(true)));

		assertThat(notification.getType()).isEqualTo(NotificationType.INFORMATION);
		assertThat(notification.getContent()).isEqualTo(MessageBundle.message("plan.apply.cancelled"));
		runRecovery(notification);
		assertThat(recovered).isTrue();
	}

	@Test
	void failedApplyOffersShelfRecovery(Project project) {

		AtomicBoolean recovered = new AtomicBoolean();
		Notification notification = capture(project, () -> new PlanNotifications().error(project,
				MessageBundle.message("plan.apply.error"), new IllegalStateException("broken"),
				() -> recovered.set(true)));

		assertThat(notification.getType()).isEqualTo(NotificationType.ERROR);
		assertThat(notification.getContent()).isEqualTo("broken");
		runRecovery(notification);
		assertThat(recovered).isTrue();
	}

	private static Notification capture(Project project, Runnable operation) {

		AtomicReference<Notification> captured = new AtomicReference<>();
		MessageBusConnection connection = project.getMessageBus().connect();
		connection.subscribe(Notifications.TOPIC, new Notifications() {

			@Override
			public void notify(Notification notification) {
				captured.set(notification);
			}

		});
		try {
			operation.run();
			return Objects.requireNonNull(captured.get());
		} finally {
			connection.disconnect();
		}
	}

	private static void runRecovery(Notification notification) {

		assertThat(notification.getActions()).singleElement()
				.isInstanceOfSatisfying(NotificationAction.class, action -> {
					assertThat(action.getTemplatePresentation().getText())
							.isEqualTo(MessageBundle.message("plan.apply.unshelve"));
					AnActionEvent event = AnActionEvent.createEvent(action, DataContext.EMPTY_CONTEXT, null,
							ActionPlaces.UNKNOWN, ActionUiKind.NONE, null);
					action.actionPerformed(event, notification);
				});
	}

}
