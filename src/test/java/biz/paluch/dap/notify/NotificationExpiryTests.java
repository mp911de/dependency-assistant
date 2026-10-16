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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import biz.paluch.dap.assistant.DependencyCheckListener;
import biz.paluch.dap.assistant.check.DependencyCheck;
import biz.paluch.dap.assistant.check.UpgradeScope;
import biz.paluch.dap.extension.IdeaProjectTests;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link NotificationExpiry}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class NotificationExpiryTests {

	@Test
	void stalePromptExpiresBeforeCheckWorkEvenWhenCancelled(Project project) {

		AtomicReference<Notification> shown = new AtomicReference<>();
		Notifications.releaseMetadataStale(Instant.EPOCH)
				.expireWhen((owner, notification) -> shown.set(notification)).notify(project);

		try {
			assertThat(shown.get().isExpired()).isFalse();

			EmptyProgressIndicator indicator = new EmptyProgressIndicator() {

				@Override
				public void setIndeterminate(boolean indeterminate) {
					throw new ProcessCanceledException();
				}

			};

			assertThatExceptionOfType(ProcessCanceledException.class).isThrownBy(() -> {
				new DependencyCheck(project).findDependencyUpgrades(indicator, UpgradeScope.resolved(List.of()));
			});
			assertThat(shown.get().isExpired()).isTrue();
		} finally {
			shown.get().expire();
		}
	}

	@Test
	void dependencyCheckExpiryStaysWithinProject(Project project) {

		Notification notification = NotificationChannel.RELEASE_METADATA_PROMPT.create("Stale", "",
				NotificationType.INFORMATION);
		NotificationExpiry.onDependencyCheckStarted().watch(project, notification);

		try {
			ApplicationManager.getApplication().getMessageBus()
					.syncPublisher(DependencyCheckListener.TOPIC).dependencyCheckStarted();
			assertThat(notification.isExpired()).isFalse();

			project.getMessageBus().syncPublisher(DependencyCheckListener.TOPIC).dependencyCheckStarted();
			assertThat(notification.isExpired()).isTrue();
		} finally {
			notification.expire();
		}
	}

}
