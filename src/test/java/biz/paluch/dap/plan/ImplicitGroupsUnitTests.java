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

package biz.paluch.dap.plan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.plan.UpgradePlanState.Member;
import biz.paluch.dap.plan.UpgradePlanState.VersionSourceKind;
import biz.paluch.dap.util.Sequence;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;
import static biz.paluch.dap.fixtures.TestCandidates.*;

/**
 * Unit tests for {@link ImplicitGroups}.
 *
 * @author Mark Paluch
 */
class ImplicitGroupsUnitTests {

	@Test
	void unrelatedCapturesStayTopLevel() {

		Map<PlannedUpgrade, ArtifactVersion> upgrades = new LinkedHashMap<>();
		upgrades.put(new TestPlannedUpgrade(
				candidate("org.springframework:spring-core:6.2.1", it -> it.versionProperty("spring.version"))),
				ArtifactVersion.of("6.2.2"));
		upgrades.put(new TestPlannedUpgrade(
				candidate("com.fasterxml.jackson.core:jackson-core:2.18.0",
						it -> it.versionProperty("jackson.version"))),
				ArtifactVersion.of("2.18.1"));

		Sequence<Item> items = ImplicitGroups.create(upgrades);

		assertThat(items).extracting(Item::getDisplayName).containsExactly("spring-core", "jackson-core");
		assertThat(items).allSatisfy(item -> assertThat(item.getMembers()).hasSize(1)
				.noneMatch(member -> member.implicit));
	}

	@Test
	void peerSharingPropertyFormsGroupNamedAfterProperty() {

		Map<PlannedUpgrade, ArtifactVersion> upgrades = new LinkedHashMap<>();
		upgrades.put(new TestPlannedUpgrade(
				candidate("org.springframework:spring-core:6.2.1", it -> it.versionProperty("spring.version"))),
				ArtifactVersion.of("6.2.2"));
		upgrades.put(new TestPlannedUpgrade(
				candidate("org.springframework:spring-context:6.2.1", it -> it.versionProperty("spring.version"))),
				ArtifactVersion.of("6.2.2"));

		Sequence<Item> items = ImplicitGroups.create(upgrades);

		assertThat(items).singleElement().satisfies(item -> {
			assertThat(item.getDisplayName()).isEqualTo("spring.version");
			assertThat(item.getMembers()).extracting(member -> member.artifactId)
					.containsExactly("spring-core", "spring-context");
			assertThat(item.getMembers()).extracting(member -> member.implicit).containsExactly(false, true);
		});
	}

	@Test
	void peerJoinsExistingGroupAsImplicitMember() {

		Map<PlannedUpgrade, ArtifactVersion> upgrades = new LinkedHashMap<>();
		upgrades.put(springGroup(), ArtifactVersion.of("6.2.2"));
		upgrades.put(new TestPlannedUpgrade(
				candidate("com.example:addon:6.2.1", it -> it.versionProperty("spring.version"))),
				ArtifactVersion.of("6.2.2"));

		Sequence<Item> items = ImplicitGroups.create(upgrades);

		assertThat(items).singleElement().satisfies(item -> {
			assertThat(item.getDisplayName()).isEqualTo("Spring");
			assertThat(item.getMembers()).extracting(member -> member.artifactId)
					.containsExactly("spring-core", "spring-beans", "addon");
			assertThat(item.getMembers()).extracting(member -> member.implicit)
					.containsExactly(false, true, true);
		});
	}

	@Test
	void groupIsPreferredOwnerOverEarlierTopLevelCapture() {

		Map<PlannedUpgrade, ArtifactVersion> upgrades = new LinkedHashMap<>();
		upgrades.put(new TestPlannedUpgrade(
				candidate("com.example:addon:6.2.1", it -> it.versionProperty("spring.version"))),
				ArtifactVersion.of("6.2.2"));
		upgrades.put(springGroup(), ArtifactVersion.of("6.2.2"));

		Sequence<Item> items = ImplicitGroups.create(upgrades);

		assertThat(items).singleElement().satisfies(item -> {
			assertThat(item.getDisplayName()).isEqualTo("Spring");
			assertThat(item.getMembers()).extracting(member -> member.artifactId)
					.containsExactly("spring-core", "spring-beans", "addon");
		});
	}

	@Test
	void divergingPinnedTargetsStaySeparate() {

		Map<PlannedUpgrade, ArtifactVersion> upgrades = new LinkedHashMap<>();
		upgrades.put(new TestPlannedUpgrade(
				candidate("org.springframework:spring-core:6.2.1", it -> it.versionProperty("spring.version"))),
				ArtifactVersion.of("6.2.2"));
		upgrades.put(new TestPlannedUpgrade(
				candidate("com.example:addon:6.2.1", it -> it.versionProperty("spring.version"))),
				ArtifactVersion.of("7.0.0"));

		Sequence<Item> items = ImplicitGroups.create(upgrades);

		assertThat(items).extracting(Item::getDisplayName).containsExactly("spring-core", "addon");
		assertThat(items).allSatisfy(item -> assertThat(item.getMembers()).hasSize(1)
				.noneMatch(member -> member.implicit));
	}

	@Test
	void mixedSourcePeerRetainsInlineDeclaration() {

		Map<PlannedUpgrade, ArtifactVersion> upgrades = new LinkedHashMap<>();
		upgrades.put(new TestPlannedUpgrade(
				candidate("org.springframework:spring-core:6.2.1", it -> it.versionProperty("spring.version"))),
				ArtifactVersion.of("6.2.2"));
		upgrades.put(new TestPlannedUpgrade(candidate("com.example:addon:6.2.1",
				it -> it.versionSources(VersionSource.property("spring.version"), VersionSource.declared("6.2.1")))),
				ArtifactVersion.of("6.2.2"));

		Sequence<Item> items = ImplicitGroups.create(upgrades);

		assertThat(items).singleElement().satisfies(item -> {
			assertThat(item.getDisplayName()).isEqualTo("spring.version");

			Member retained = item.getMembers().getLast();
			assertThat(retained.implicit).isFalse();
			assertThat(retained.versionSources).extracting(source -> source.kind)
					.containsExactly(VersionSourceKind.DECLARED);
		});
	}

	@Test
	void multiPropertyPeerBelongsToFirstOwnerWithoutMergingGroups() {

		TestPlannedUpgrade jackson = new TestPlannedUpgrade(List.of(
				candidate("com.fasterxml.jackson.core:jackson-core:2.18.0",
						it -> it.versionProperty("jackson.version")),
				candidate("com.fasterxml.jackson.core:jackson-databind:2.18.0",
						it -> it.versionProperty("jackson.version"))),
				"Jackson");
		TestPlannedUpgrade micrometer = new TestPlannedUpgrade(List.of(
				candidate("io.micrometer:micrometer-core:2.18.0", it -> it.versionProperty("micrometer.version")),
				candidate("io.micrometer:micrometer-registry-prometheus:2.18.0",
						it -> it.versionProperty("micrometer.version"))),
				"Micrometer");

		Map<PlannedUpgrade, ArtifactVersion> upgrades = new LinkedHashMap<>();
		upgrades.put(jackson, ArtifactVersion.of("2.19.0"));
		upgrades.put(micrometer, ArtifactVersion.of("2.19.0"));
		upgrades.put(new TestPlannedUpgrade(candidate("com.example:addon:2.18.0",
				it -> it.versionSources(VersionSource.property("jackson.version"),
						VersionSource.property("micrometer.version")))),
				ArtifactVersion.of("2.19.0"));

		List<Item> items = ImplicitGroups.create(upgrades).toList();

		assertThat(items).extracting(Item::getDisplayName).containsExactly("Jackson", "Micrometer");
		assertThat(items.getFirst().getMembers()).extracting(member -> member.artifactId)
				.containsExactly("jackson-core", "jackson-databind", "addon");
		assertThat(items.getFirst().getMembers().getLast().implicit).isTrue();
		assertThat(items.getLast().getMembers()).hasSize(2);
	}

	private static TestPlannedUpgrade springGroup() {
		return new TestPlannedUpgrade(List.of(
				candidate("org.springframework:spring-core:6.2.1", it -> it.versionProperty("spring.version")),
				candidate("org.springframework:spring-beans:6.2.1", it -> it.versionProperty("spring.version"))),
				"Spring");
	}

}
