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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.AppliedUpdate;
import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.support.DependencyUpdate;
import com.intellij.notification.NotificationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradeNotification}.
 *
 * @author Mark Paluch
 */
class UpgradeNotificationUnitTests {

	@Test
	void noUpdateReportsNothingChanged() {

		UpgradeNotification notification = UpgradeNotification.applied(new AppliedUpdates());

		assertThat(notification.isEmpty()).isTrue();
		assertThat(notification.getTitle()).isEqualTo("No build file changed");
		assertThat(notification.getContent()).isEmpty();
		assertThat(notification.withFlagged().getType()).isEqualTo(NotificationType.INFORMATION);
	}

	@Test
	void singleUpdateNamesDependencyAndVersionInTitle() {

		AppliedUpdates updates = new AppliedUpdates();
		updates.record(List.of(), new DependencyUpdate(ArtifactId.of("org.springframework", "spring-core"),
				ArtifactVersion.of("6.2.0"), ArtifactVersion.of("6.2.1"), List.of(), List.of()), "spring-core");

		UpgradeNotification notification = UpgradeNotification.applied(updates);

		assertThat(notification.isSingle()).isTrue();
		assertThat(notification.getTitle()).isEqualTo("Upgraded to spring-core 6.2.1");
		assertThat(notification.getContent()).isEmpty();
		assertThat(notification.getType()).isEqualTo(NotificationType.INFORMATION);
	}

	@Test
	void singleDowngradeUsesDowngradeWording() {

		AppliedUpdates updates = new AppliedUpdates();
		updates.record(List.of(), new DependencyUpdate(ArtifactId.of("org.springframework", "spring-core"),
				ArtifactVersion.of("6.2.1"), ArtifactVersion.of("6.2.0"), List.of(), List.of()), "spring-core");

		assertThat(UpgradeNotification.applied(updates).getTitle()).isEqualTo("Downgraded to spring-core 6.2.0");
		assertThat(UpgradeNotification.committed(updates).getTitle())
				.isEqualTo("Committed downgrade to spring-core 6.2.0");
	}

	@Test
	void severalUpdatesUseCountedTitleAndListContent() {

		AppliedUpdates updates = new AppliedUpdates();
		updates.record(List.of(), new DependencyUpdate(ArtifactId.of("org.springframework", "spring-core"),
				ArtifactVersion.of("6.2.0"), ArtifactVersion.of("6.2.1"), List.of(), List.of()), "spring-core");
		updates.record(List.of(), new DependencyUpdate(ArtifactId.of("org.springframework.data", "spring-data-commons"),
				ArtifactVersion.of("3.4.0"), ArtifactVersion.of("3.4.1"), List.of(), List.of()), "spring-data-commons");

		UpgradeNotification notification = UpgradeNotification.applied(updates);

		assertThat(notification.isSingle()).isFalse();
		assertThat(notification.getTitle()).isEqualTo("Upgraded 2 dependencies");
		assertThat(notification.getContent()).isEqualTo("spring-core 6.2.1, spring-data-commons 3.4.1");
		assertThat(UpgradeNotification.committed(updates).getTitle())
				.isEqualTo("Committed 2 dependency upgrades");
	}

	@Test
	void singleFlaggedUpdateDescribesFlagAsSentenceAndWarns() {

		AppliedUpdates updates = new AppliedUpdates();
		updates.applied().add(violation("spring-core", "6.2.0", "7.0.0"));

		UpgradeNotification notification = UpgradeNotification.applied(updates).withFlagged();

		assertThat(notification.getTitle()).isEqualTo("Upgraded to spring-core 7.0.0");
		assertThat(notification.getContent()).isEqualTo("Upgrade violates the dependency rule.");
		assertThat(notification.getType()).isEqualTo(NotificationType.WARNING);
	}

	@Test
	void severalFlaggedUpdatesListAffectedEntriesUnderHeading() {

		AppliedUpdates updates = new AppliedUpdates();
		updates.applied().add(violation("spring-core", "6.2.0", "7.0.0"));
		updates.record(List.of(), new DependencyUpdate(ArtifactId.of("org.springframework.data", "spring-data-commons"),
				ArtifactVersion.of("3.4.0"), ArtifactVersion.of("3.4.1"), List.of(), List.of()), "spring-data-commons");

		UpgradeNotification notification = UpgradeNotification.applied(updates).withFlagged();

		assertThat(notification.getContent()).isEqualTo("<p>spring-core 7.0.0, spring-data-commons 3.4.1</p>"
				+ "<p><b>1 upgrade violates a dependency rule:</b><ul><li>spring-core 7.0.0</li></ul></p>");
		assertThat(notification.getType()).isEqualTo(NotificationType.WARNING);
	}

	@Test
	void majorCrossingWithoutRuleStaysInformational() {

		AppliedUpdates updates = new AppliedUpdates();
		updates.record(List.of(), new DependencyUpdate(ArtifactId.of("org.springframework", "spring-core"),
				ArtifactVersion.of("6.2.0"), ArtifactVersion.of("7.0.0"), List.of(), List.of()), "spring-core");

		UpgradeNotification notification = UpgradeNotification.applied(updates).withFlagged();

		assertThat(notification.getContent()).isEmpty();
		assertThat(notification.getType()).isEqualTo(NotificationType.INFORMATION);
	}

	private static AppliedUpdate violation(String artifact, String from, String to) {
		return new AppliedUpdate(new DependencyUpdate(ArtifactId.of("org.springframework", artifact),
				ArtifactVersion.of(from), ArtifactVersion.of(to), List.of(), List.of()), artifact,
				AppliedUpdate.Flag.COMPLIANCE);
	}

}
