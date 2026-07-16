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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.plan.UpgradePlanState.DeclarationKind;
import biz.paluch.dap.plan.UpgradePlanState.DeclarationSourceState;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.plan.UpgradePlanState.Member;
import biz.paluch.dap.plan.UpgradePlanState.VersionSourceKind;
import biz.paluch.dap.plan.UpgradePlanState.VersionSourceState;
import biz.paluch.dap.support.DependencyUpdate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradePlanLoader}.
 *
 * @author Mark Paluch
 */
class UpgradePlanLoaderUnitTests {

	@Test
	void reconstructsApplyAndNavigationFactsFromPersistedState() {

		Item stored = item("Spring Core", "6.2.2",
				member("org.springframework", "spring-core", "6.2.1", "spring.version"));

		UpgradePlanItem planItem = loader().create(stored);

		assertThat(planItem).isNotNull();
		assertThat(planItem.getDisplayName()).isEqualTo("Spring Core");
		assertThat(planItem.getFromVersion()).isEqualTo(ArtifactVersion.of("6.2.1"));
		assertThat(planItem.getToVersion()).isEqualTo(ArtifactVersion.of("6.2.2"));
		assertThat(planItem.isGroup()).isFalse();
		assertThat(planItem.getVersionPropertyNames()).containsExactly("spring.version");
		assertThat(planItem.toQuery().artifacts())
				.containsExactly(ArtifactId.of("org.springframework", "spring-core"));
		assertThat(planItem.toQuery().versionProperties()).containsExactly("spring.version");

		DependencyUpdate update = planItem.createUpdates().getFirst();
		assertThat(update.artifactId()).isEqualTo(ArtifactId.of("org.springframework", "spring-core"));
		assertThat(update.from().getVersion()).isEqualTo(ArtifactVersion.of("6.2.1"));
		assertThat(update.version()).isEqualTo(ArtifactVersion.of("6.2.2"));
		assertThat(update.declarationSources()).containsExactly(DeclarationSource.dependency());
		assertThat(update.versionSources()).containsExactly(VersionSource.property("spring.version"));
	}

	@Test
	void retainsUnresolvedStateWhenAssistantCannotBeResolved() {

		Item stored = item("Spring Core", "6.2.2",
				member("org.springframework", "spring-core", "6.2.1", "spring.version"));
		stored.getMembers().getFirst().assistant = "missing.Assistant";

		assertThat(loader().create(stored)).isNull();
	}

	private static UpgradePlanLoader loader() {
		return new UpgradePlanLoader(List.of(TestInterfaceAssistant.INSTANCE), null);
	}

	private static Item item(String displayName, String target, Member... members) {
		Item item = new Item();
		item.setDisplayName(displayName);
		item.setToVersion(target);
		item.setMembers(List.of(members));
		return item;
	}

	private static Member member(String groupId, String artifactId, String fromVersion, String property) {
		Member member = new Member();
		member.groupId = groupId;
		member.artifactId = artifactId;
		member.fromVersion = fromVersion;
		member.assistant = TestInterfaceAssistant.class.getName();
		member.declarationSources.add(new DeclarationSourceState(DeclarationKind.DEPENDENCY, null));
		member.versionSources
				.add(new VersionSourceState(VersionSourceKind.PROPERTY, property, null, null));
		return member;
	}

}
