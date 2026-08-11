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
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.fixtures.TestAssistant;
import biz.paluch.dap.plan.UpgradePlanState.DeclarationKind;
import biz.paluch.dap.plan.UpgradePlanState.DeclarationSourceState;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.plan.UpgradePlanState.Member;
import biz.paluch.dap.plan.UpgradePlanState.VersionSourceKind;
import biz.paluch.dap.plan.UpgradePlanState.VersionSourceState;
import biz.paluch.dap.support.DependencyUpdate;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link UpgradePlanLoader}.
 *
 * @author Mark Paluch
 */
class UpgradePlanLoaderUnitTests {

	UpgradePlanLoader loader = new UpgradePlanLoader(TestAssistant.INSTANCE, null);

	@Test
	void reconstructsApplyAndNavigationFactsFromPersistedState() {

		Item stored = item("Spring Core", "6.2.2",
				member("org.springframework", "spring-core", "6.2.1", "spring.version"));

		UpgradePlanItem planItem = loader.create(stored);

		assertThat(planItem).isNotNull();
		assertThat(planItem.getDisplayName()).isEqualTo("Spring Core");
		assertThat(planItem.getFromVersion()).isEqualTo("6.2.1");
		assertThat(planItem.getToVersion()).isEqualTo("6.2.2");
		assertThat(planItem.isGroup()).isFalse();
		assertThat(planItem.getVersionPropertyNames()).containsExactly("spring.version");
		assertThat(planItem.toQuery().artifacts())
				.containsExactly(ArtifactId.of("org.springframework", "spring-core"));
		assertThat(planItem.toQuery().versionProperties()).containsExactly("spring.version");

		DependencyUpdate update = planItem.createUpdates().getFirst();
		assertThat(update.artifactId()).isEqualTo(ArtifactId.of("org.springframework", "spring-core"));
		assertThat(update.from().getVersion()).isEqualTo("6.2.1");
		assertThat(update.to()).isEqualTo("6.2.2");
		assertThat(update.declarationSources()).containsExactly(DeclarationSource.dependency());
		assertThat(update.versionSources()).containsExactly(VersionSource.property("spring.version"));
	}

	@Test
	void retainsUnresolvedStateWhenAssistantCannotBeResolved() {

		Item stored = item("Spring Core", "6.2.2",
				member("org.springframework", "spring-core", "6.2.1", "spring.version"));
		stored.getMembers().getFirst().assistant = "missing.Assistant";

		assertThat(loader.create(stored)).isNull();
	}

	@Test
	void implicitMemberRemainsVisibleWithoutCreatingAnUpdate() {

		Member owner = member("org.springframework", "spring-core", "6.2.1", "spring.version");
		Member implicit = member("com.example", "addon", "6.2.1", "spring.version");
		implicit.implicit = true;

		UpgradePlanItem planItem = loader.create(item("spring.version", "6.2.2", owner, implicit));

		assertThat(planItem).isNotNull();
		assertThat(planItem.getMembers()).hasSize(2);
		assertThat(planItem.getMembers()).extracting(ItemDependency::isImplicit).containsExactly(false, true);
		assertThat(planItem.createUpdates()).singleElement()
				.extracting(update -> update.artifactId().artifactId()).isEqualTo("spring-core");
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
		member.assistant = TestAssistant.INSTANCE.getId();
		member.declarationSources.add(new DeclarationSourceState(DeclarationKind.DEPENDENCY, null));
		member.versionSources
				.add(new VersionSourceState(VersionSourceKind.PROPERTY, property, null, null));
		return member;
	}

}
