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

package biz.paluch.dap.assistant;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.fixtures.TestDependencyRule;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.ProjectId;
import com.intellij.mock.MockVirtualFile;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;
import static biz.paluch.dap.fixtures.Releases.*;

/**
 * Unit tests for {@link UpgradeGroups}.
 *
 * @author Mark Paluch
 */
class UpgradeGroupsUnitTests {

	private static final DependencyRule VAVR_RULE = new TestDependencyRule("Vavr");

	private static final DependencyRule SPRING = new TestDependencyRule("Spring Framework");

	@Test
	void collapsesAgreeingGovernedMembersIntoGroup() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(member(VAVR, "1.0.0", VAVR_RULE))
				.add(member(VAVR_MATCH, "1.0.0", VAVR_RULE))
				.build();

		assertThat(rows).singleElement().isInstanceOfSatisfying(UpgradeGroup.class, group -> {
			assertThat(group.getRowLabel()).isEqualTo("Vavr");
			assertThat(group.getCurrentVersion()).isEqualTo(ArtifactVersion.of("1.0.0"));
			assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId)
					.containsExactly(VAVR.toArtifactId(), VAVR_MATCH.toArtifactId());
		});
	}

	@Test
	void groupOffersIntersectionOfMemberReleases() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(member(VAVR, "1.0.0", VAVR_RULE))
				.add(member(VAVR_MATCH, "1.0.0", VAVR_RULE))
				.build();

		assertThat(rows).singleElement().isInstanceOfSatisfying(UpgradeGroup.class, group -> {
			Releases offered = group.getUpdateCandidate().getReleases();
			assertThat(offered).containsRelease("1.0.0");
			assertThat(offered).containsRelease("0.11.0");
			assertThat(offered).doesNotContainRelease("1.0.1");
			assertThat(offered).doesNotContainRelease("0.10.7");
		});
	}

	@Test
	void selectsLargestVersionAgreeingCohort() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(member(SPRING_BOOT, "3.0.0", SPRING))
				.add(member(SPRING_MODULITH_BOM, "3.0.0", SPRING))
				.add(member(SPRING_DEPENDENCY_MANAGEMENT, "2.0.0", SPRING))
				.build();

		assertThat(rows).hasSize(2);
		assertThat(rows).filteredOn(UpgradeGroup.class::isInstance).singleElement()
				.isInstanceOfSatisfying(UpgradeGroup.class, group -> assertThat(group.getMembers())
						.extracting(UpgradeCandidate::getArtifactId)
						.containsExactly(SPRING_BOOT.toArtifactId(), SPRING_MODULITH_BOM.toArtifactId()));
		assertThat(rows.toList().getLast()).isNotInstanceOf(UpgradeGroup.class)
				.extracting(UpgradeCandidate::getArtifactId).isEqualTo(SPRING_DEPENDENCY_MANAGEMENT.toArtifactId());
	}

	@Test
	void tieBreaksEqualCohortsToHigherVersion() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(member(SPRING_DEPENDENCY_MANAGEMENT, "1.0.0", SPRING))
				.add(member(REACTOR_BOM, "1.0.0", SPRING))
				.add(member(SPRING_BOOT, "2.0.0", SPRING))
				.add(member(SPRING_MODULITH_BOM, "2.0.0", SPRING))
				.build();

		assertThat(rows).hasSize(3);
		assertThat(rows).filteredOn(UpgradeGroup.class::isInstance).singleElement()
				.isInstanceOfSatisfying(UpgradeGroup.class, group -> {
					assertThat(group.getCurrentVersion()).isEqualTo(ArtifactVersion.of("2.0.0"));
					assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId)
							.containsExactly(SPRING_BOOT.toArtifactId(), SPRING_MODULITH_BOM.toArtifactId());
				});
	}

	@Test
	void driftingMemberMatchingCohortVersionJoinsGroupCarryingDrift() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(member(SPRING_BOOT, "3.0.0", SPRING))
				.add(member(SPRING_MODULITH_BOM, "3.0.0", SPRING))
				.add(driftingMember(SPRING_DEPENDENCY_MANAGEMENT, SPRING, "3.0.0", "2.0.0"))
				.build();

		assertThat(rows).singleElement().isInstanceOfSatisfying(UpgradeGroup.class, group -> {
			assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId).containsExactly(
					SPRING_BOOT.toArtifactId(), SPRING_MODULITH_BOM.toArtifactId(),
					SPRING_DEPENDENCY_MANAGEMENT.toArtifactId());
			assertThat(group.getCurrentVersion()).isEqualTo(ArtifactVersion.of("2.0.0"));
			assertThat(group.getDeclaredVersions().hasVersionDrift()).isTrue();
		});
	}

	@Test
	void driftingMemberSharingVersionPropertyJoinsGroup() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(propertyMember(SPRING_BOOT, SPRING, "spring.version", "3.0.0"))
				.add(propertyMember(SPRING_MODULITH_BOM, SPRING, "spring.version", "3.0.0"))
				.add(propertyMember(SPRING_DEPENDENCY_MANAGEMENT, SPRING, "spring.version", "2.0.0", "2.1.0"))
				.build();

		assertThat(rows).singleElement().isInstanceOfSatisfying(UpgradeGroup.class, group -> {
			assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId).containsExactly(
					SPRING_BOOT.toArtifactId(), SPRING_MODULITH_BOM.toArtifactId(),
					SPRING_DEPENDENCY_MANAGEMENT.toArtifactId());
			assertThat(group.getCurrentVersion()).isEqualTo(ArtifactVersion.of("2.0.0"));
		});
	}

	@Test
	void singleGovernedMemberDoesNotGroupButRelabelsByRuleName() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(member(VAVR, "1.0.0", VAVR_RULE))
				.add(member(SLF4J_API, "2.0.17", DependencyRule.absent()))
				.build();

		assertThat(rows).noneMatch(UpgradeGroup.class::isInstance);
		assertThat(rows).extracting(UpgradeCandidate::getRowLabel).containsExactly("Vavr", "slf4j-api");
	}

	@Test
	void leftoverGovernedMemberKeepsCoordinateLabelWhenGroupClaimsRuleName() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(member(SPRING_BOOT, "3.0.0", SPRING))
				.add(member(SPRING_MODULITH_BOM, "3.0.0", SPRING))
				.add(member(SPRING_DEPENDENCY_MANAGEMENT, "2.0.0", SPRING))
				.build();

		assertThat(rows).extracting(UpgradeCandidate::getRowLabel)
				.containsExactly("Spring Framework", SPRING_DEPENDENCY_MANAGEMENT.getArtifactId());
	}

	@Test
	void unnamedRuleFallsThroughToInferredGroup() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(ungoverned("io.vavr", "vavr", new TestDependencyRule(""), "1.0.0"))
				.add(ungoverned("io.vavr", "vavr-match", new TestDependencyRule(""), "1.0.0"))
				.build();

		assertThat(rows).singleElement().isInstanceOfSatisfying(UpgradeGroup.class,
				group -> assertThat(group.getRowLabel()).isEqualTo("vavr"));
	}

	@Test
	void absentRuleFallsThroughToInferredGroup() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(ungoverned("io.vavr", "vavr", DependencyRule.absent(), "1.0.0"))
				.add(ungoverned("io.vavr", "vavr-match", DependencyRule.absent(), "1.0.0"))
				.build();

		assertThat(rows).singleElement().isInstanceOfSatisfying(UpgradeGroup.class,
				group -> assertThat(group.getRowLabel()).isEqualTo("vavr"));
	}

	@Test
	void sameRuleNameAcrossEcosystemsNeverGroups() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(member(VAVR, "1.0.0", VAVR_RULE))
				.add(member(VAVR_MATCH, "1.0.0", VAVR_RULE, new OtherEcosystemAssistant()))
				.build();

		assertThat(rows).hasSize(2).noneMatch(UpgradeGroup.class::isInstance);
	}

	@Test
	void placesGroupAtFirstMemberPreservingSurroundingOrder() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(member(SLF4J_API, "2.0.17", DependencyRule.absent()))
				.add(member(SPRING_BOOT, "3.0.0", SPRING))
				.add(member(GUAVA, "33.6.0-jre", DependencyRule.absent()))
				.add(member(SPRING_MODULITH_BOM, "3.0.0", SPRING))
				.build();

		List<UpgradeCandidate> ordered = rows.toList();
		assertThat(ordered).hasSize(3);
		assertThat(ordered.get(0)).isNotInstanceOf(UpgradeGroup.class)
				.extracting(UpgradeCandidate::getArtifactId).isEqualTo(SLF4J_API.toArtifactId());
		assertThat(ordered.get(1)).isInstanceOf(UpgradeGroup.class);
		assertThat(ordered.get(2)).isNotInstanceOf(UpgradeGroup.class)
				.extracting(UpgradeCandidate::getArtifactId).isEqualTo(GUAVA.toArtifactId());
	}

	@Test
	void passesThroughUngovernedCandidatesUnchanged() {

		UpgradeCandidate slf4j = member(SLF4J_API, "2.0.17", DependencyRule.absent());
		UpgradeCandidate guava = member(GUAVA, "33.6.0-jre", DependencyRule.absent());

		UpgradeGroups rows = UpgradeGroups.builder().add(slf4j).add(guava).build();

		assertThat(rows).containsExactly(slf4j, guava);
	}

	@Test
	void collapsesUngovernedPrefixFamilyWithDerivedLabel() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(ungoverned("org.springframework.data", "spring-data-commons", "3.4.0"))
				.add(ungoverned("org.springframework.data", "spring-data-jpa", "3.4.0"))
				.build();

		assertThat(rows).singleElement().isInstanceOfSatisfying(UpgradeGroup.class, group -> {
			assertThat(group.getRowLabel()).isEqualTo("spring-data");
			assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId).containsExactly(
					ArtifactId.of("org.springframework.data", "spring-data-commons"),
					ArtifactId.of("org.springframework.data", "spring-data-jpa"));
		});
	}

	@Test
	void separatesSamePrefixAcrossDifferentGroupIds() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(ungoverned("org.junit.jupiter", "junit-jupiter-api", "5.11.0"))
				.add(ungoverned("org.junit.jupiter", "junit-jupiter-engine", "5.11.0"))
				.add(ungoverned("org.junit.platform", "junit-platform-launcher", "1.11.0"))
				.add(ungoverned("org.junit.platform", "junit-platform-runner", "1.11.0"))
				.build();

		assertThat(rows).hasSize(2).allMatch(UpgradeGroup.class::isInstance);
		assertThat(rows).extracting(UpgradeCandidate::getRowLabel)
				.containsExactly("junit-jupiter", "junit-platform");
	}

	@Test
	void inferredGroupSelectsLargestVersionAgreeingCohort() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(ungoverned("org.springframework.data", "spring-data-commons", "3.4.0"))
				.add(ungoverned("org.springframework.data", "spring-data-jpa", "3.4.0"))
				.add(ungoverned("org.springframework.data", "spring-data-mongodb", "3.3.0"))
				.build();

		assertThat(rows).hasSize(2);
		assertThat(rows).filteredOn(UpgradeGroup.class::isInstance).singleElement()
				.isInstanceOfSatisfying(UpgradeGroup.class, group -> assertThat(group.getMembers())
						.extracting(UpgradeCandidate::getArtifactId)
						.containsExactly(ArtifactId.of("org.springframework.data", "spring-data-commons"),
								ArtifactId.of("org.springframework.data", "spring-data-jpa")));
	}

	@Test
	void inferredGroupCollapsesCoReleasedMembers() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(released("io.example", "foo-core", "1.0.0", "1.0.0", "1.1.0"))
				.add(released("io.example", "foo-util", "1.0.0", "1.0.0", "1.1.0"))
				.build();

		assertThat(rows).singleElement().isInstanceOfSatisfying(UpgradeGroup.class,
				group -> assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId).containsExactly(
						ArtifactId.of("io.example", "foo-core"), ArtifactId.of("io.example", "foo-util")));
	}

	@Test
	void inferredGroupConsidersReleaseHistoryMembers() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(released("io.example", "foo-core", "1.1.0", "1.0.0", "1.1.0"))
				.add(released("io.example", "foo-util", "1.1.0", "1.0.0", "1.0.1", "1.1.0"))
				.build();

		assertThat(rows).hasSize(2);

		rows = UpgradeGroups.builder()
				.add(released("io.example", "foo-core", "1.2.0", "1.0.0", "1.1.0"))
				.add(released("io.example", "foo-util", "1.2.0", "1.0.0", "1.0.1", "1.1.0"))
				.build();

		assertThat(rows).hasSize(2);
	}

	@Test
	void inferredGroupRejectsMembersOffTheSameReleaseLine() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(released("io.example", "foo-core", "1.0.0", "1.0.0", "1.1.0"))
				.add(released("io.example", "foo-util", "1.0.0", "1.0.0"))
				.build();

		assertThat(rows).hasSize(2).noneMatch(UpgradeGroup.class::isInstance);
	}

	@Test
	void inferredReleaseLineIgnoresHistoryBelowCurrentVersion() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(released("io.example", "foo-core", "6.1.0", "5.0.0", "6.1.0", "6.1.1"))
				.add(released("io.example", "foo-late", "6.1.0", "6.1.0", "6.1.1"))
				.build();

		assertThat(rows).singleElement().isInstanceOf(UpgradeGroup.class);
	}

	@Test
	void inferredGroupSplitsDistinctReleaseLinesIntoSeparateGroups() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(released("io.example", "foo-client-core", "1.0.0", "1.0.0", "1.1.0"))
				.add(released("io.example", "foo-client-api", "1.0.0", "1.0.0", "1.1.0"))
				.add(released("io.example", "foo-server-core", "1.0.0", "1.0.0", "2.0.0"))
				.add(released("io.example", "foo-server-api", "1.0.0", "1.0.0", "2.0.0"))
				.build();

		assertThat(rows).hasSize(2).allMatch(UpgradeGroup.class::isInstance);
		assertThat(rows).extracting(UpgradeCandidate::getRowLabel).containsExactly("foo-client", "foo-server");
	}

	@Test
	void doesNotGroupSuffixOnlyFamily() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(ungoverned("com.autodesk.aps", "authentication-sdk", "1.0.0"))
				.add(ungoverned("com.autodesk.aps", "datamanagement-sdk", "1.0.0"))
				.build();

		assertThat(rows).hasSize(2).noneMatch(UpgradeGroup.class::isInstance);
	}

	@Test
	void singleUngovernedCandidateStaysIndividualRow() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(ungoverned("org.springframework.data", "spring-data-commons", "3.4.0"))
				.build();

		assertThat(rows).singleElement().isNotInstanceOf(UpgradeGroup.class);
	}

	@Test
	void governedAndInferredGroupsCoexist() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(member(VAVR, "1.0.0", VAVR_RULE))
				.add(member(VAVR_MATCH, "1.0.0", VAVR_RULE))
				.add(ungoverned("org.springframework.data", "spring-data-commons", "3.4.0"))
				.add(ungoverned("org.springframework.data", "spring-data-jpa", "3.4.0"))
				.build();

		assertThat(rows).hasSize(2).allMatch(UpgradeGroup.class::isInstance);
		assertThat(rows).extracting(UpgradeCandidate::getRowLabel).containsExactly("Vavr", "spring-data");
	}

	@Test
	void sharedVersionPropertyDoesNotPullDrifterIntoInferredGroup() {

		UpgradeGroups rows = UpgradeGroups.builder()
				.add(ungoverned("org.springframework.data", "spring-data-commons", "boot.version", "3.4.0"))
				.add(ungoverned("org.springframework.data", "spring-data-jpa", "boot.version", "3.4.0"))
				.add(ungoverned("org.springframework.data", "spring-data-mongodb", "boot.version", "3.3.0", "3.5.0"))
				.build();

		assertThat(rows).hasSize(2);
		assertThat(rows).filteredOn(UpgradeGroup.class::isInstance).singleElement()
				.isInstanceOfSatisfying(UpgradeGroup.class, group -> assertThat(group.getMembers())
						.extracting(UpgradeCandidate::getArtifactId)
						.containsExactly(ArtifactId.of("org.springframework.data", "spring-data-commons"),
								ArtifactId.of("org.springframework.data", "spring-data-jpa")));
	}

	private static UpgradeCandidate ungoverned(String groupId, String artifactId, String version) {
		return ungoverned(groupId, artifactId, DependencyRule.absent(), null, version);
	}

	private static UpgradeCandidate ungoverned(String groupId, String artifactId, @Nullable String property,
			String... versions) {
		return ungoverned(groupId, artifactId, DependencyRule.absent(), property, versions);
	}

	private static UpgradeCandidate ungoverned(String groupId, String artifactId, DependencyRule rule, String version) {
		return ungoverned(groupId, artifactId, rule, null, version);
	}

	private static UpgradeCandidate ungoverned(String groupId, String artifactId, DependencyRule rule,
			@Nullable String property, String... versions) {

		ArtifactId id = ArtifactId.of(groupId, artifactId);
		ArtifactVersion current = ArtifactVersion.of(versions[0]);

		Dependency dependency = new Dependency(id, current);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(property != null ? VersionSource.property(property)
				: VersionSource.declared(versions[0]));

		List<DeclarationSite> sites = new ArrayList<>();
		for (String version : versions) {
			sites.add(new DeclarationSite(new MockVirtualFile("pom.xml", "x"), ProjectId.of(groupId, artifactId),
					new Dependency(id, ArtifactVersion.of(version))));
		}

		return new UpgradeCandidate(new DependencyUpdateCandidate(dependency, Releases.of(Release.of(versions[0]))),
				TestInterfaceAssistant.INSTANCE, DeclaredVersions.from(sites, it -> null, null), rule);
	}

	private static UpgradeCandidate released(String groupId, String artifactId, String current,
			String... releaseVersions) {

		ArtifactId id = ArtifactId.of(groupId, artifactId);
		ArtifactVersion currentVersion = ArtifactVersion.of(current);

		Dependency dependency = new Dependency(id, currentVersion);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared(current));

		DeclarationSite site = new DeclarationSite(new MockVirtualFile("pom.xml", "x"),
				ProjectId.of(groupId, artifactId), new Dependency(id, currentVersion));

		List<Release> releases = new ArrayList<>();
		for (String version : releaseVersions) {
			releases.add(Release.of(version));
		}

		return new UpgradeCandidate(new DependencyUpdateCandidate(dependency, Releases.of(releases)),
				TestInterfaceAssistant.INSTANCE, DeclaredVersions.from(List.of(site), it -> null, null),
				DependencyRule.absent());
	}

	private static UpgradeCandidate member(CachedArtifact artifact, String version, DependencyRule rule) {
		return candidate(artifact, rule, TestInterfaceAssistant.INSTANCE, null, version);
	}

	private static UpgradeCandidate member(CachedArtifact artifact, String version, DependencyRule rule,
			InterfaceAssistant assistant) {
		return candidate(artifact, rule, assistant, null, version);
	}

	private static UpgradeCandidate driftingMember(CachedArtifact artifact, DependencyRule rule, String... versions) {
		return candidate(artifact, rule, TestInterfaceAssistant.INSTANCE, null, versions);
	}

	private static UpgradeCandidate propertyMember(CachedArtifact artifact, DependencyRule rule, String property,
			String... versions) {
		return candidate(artifact, rule, TestInterfaceAssistant.INSTANCE, property, versions);
	}

	private static UpgradeCandidate candidate(CachedArtifact artifact, DependencyRule rule,
			InterfaceAssistant assistant,
			@Nullable String property, String... declaredVersions) {

		ArtifactId id = artifact.toArtifactId();
		ArtifactVersion current = ArtifactVersion.of(declaredVersions[0]);

		Dependency dependency = new Dependency(id, current);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(property != null ? VersionSource.property(property)
				: VersionSource.declared(declaredVersions[0]));

		List<DeclarationSite> sites = new ArrayList<>();
		for (String version : declaredVersions) {
			sites.add(new DeclarationSite(new MockVirtualFile("pom.xml", "x"), ProjectId.of(id.groupId(),
					id.artifactId()), new Dependency(id, ArtifactVersion.of(version))));
		}

		Releases releases = Releases.of(artifact.getVersionOptions());
		return new UpgradeCandidate(new DependencyUpdateCandidate(dependency, releases), assistant,
				DeclaredVersions.from(sites, it -> null, null), rule);
	}

	static class OtherEcosystemAssistant extends TestInterfaceAssistant {

	}

}
