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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.fixtures.TestDependencyRule;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.fixtures.TestProjectDependencyContext;
import biz.paluch.dap.fixtures.TestReleaseSource;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.rule.ResolutionContext;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.mock.MockProjectEx;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link DependencyCheckAggregator}.
 *
 * @author Mark Paluch
 */
class DependencyCheckAggregatorTests {

	ArtifactId LETTUCE_CORE = ArtifactId.of("io.lettuce", "lettuce-core");

	ArtifactId SPRING_CORE = ArtifactId.of("org.springframework", "spring-core");

	ArtifactId SPRING_TEST = ArtifactId.of("org.springframework", "spring-test");

	ArtifactId BROKEN_ARTIFACT = ArtifactId.of("broken", "artifact");

	ProjectId ACME_APP = ProjectId.of("com.acme", "app");

	ProjectId ACME_LIB = ProjectId.of("com.acme", "lib");

	ArtifactVersion LETTUCE_CURRENT = ArtifactVersion.of("7.4.1.RELEASE");

	ArtifactVersion LETTUCE_UPDATE = ArtifactVersion.of("7.5.0.RELEASE");

	ArtifactVersion SPRING_CURRENT = ArtifactVersion.of("6.2.0");

	ArtifactVersion SPRING_UPDATE = ArtifactVersion.of("6.2.1");

	String BROKEN_ARTIFACT_ERROR = "broken: unavailable";

	DependencyCheckAggregator aggregator = new DependencyCheckAggregator(new MockProjectEx(() -> {
	}), new StateService());

	@Test
	void groupsDeclarationsByArtifact() {

		VirtualFile a = buildFile("aggregate-a/build.gradle");
		VirtualFile b = buildFile("aggregate-b/build.gradle");
		ReleaseSource mavenCentral = new TestReleaseSource("mavenCentral");
		ReleaseSource pluginPortal = new TestReleaseSource("pluginPortal");

		aggregator.add(dependency(LETTUCE_CORE, LETTUCE_CURRENT), context(ACME_APP), a, List.of(mavenCentral));
		aggregator.add(dependency(LETTUCE_CORE, LETTUCE_UPDATE), context(ACME_LIB), b, List.of(pluginPortal));

		List<ArtifactId> artifacts = new ArrayList<>();
		aggregator.forEach(pkg -> artifacts.add(pkg.artifactId()));
		List<Collection<ReleaseSource>> releaseSources = new ArrayList<>();
		aggregator.forEachArtifact((artifactId, sources) -> releaseSources.add(sources));

		assertThat(artifacts).containsExactly(LETTUCE_CORE);
		assertThat(releaseSources).singleElement().satisfies(sources -> assertThat(sources)
				.containsExactlyInAnyOrder(mavenCentral, pluginPortal));
		assertThat(aggregator.getFiles()).containsExactly(a, b);
	}

	@Test
	void createsSortedCandidatesAndCarriesReleaseErrors() {

		VirtualFile a = buildFile("result-a/build.gradle");
		VirtualFile b = buildFile("result-b/build.gradle");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), a, List.of());
		aggregator.add(dependency(LETTUCE_CORE, LETTUCE_CURRENT), context(ACME_LIB), b, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), LETTUCE_CORE,
				resolved(LETTUCE_UPDATE), BROKEN_ARTIFACT, ReleaseLookupResult.failed(BROKEN_ARTIFACT_ERROR));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases);

		assertThat(result.candidates()).extracting(candidate -> candidate.getArtifactId().artifactId())
				.containsExactly(LETTUCE_CORE.artifactId(), SPRING_CORE.artifactId());
		assertThat(result.candidates()).extracting(UpgradeCandidate::getCurrentVersion)
				.containsExactly(LETTUCE_CURRENT, SPRING_CURRENT);
		assertThat(result.errors()).containsExactly(BROKEN_ARTIFACT_ERROR);
		assertThat(result.files()).containsExactly(a, b);
	}

	@Test
	void carriesDeclarationDriftForInlineAndPropertyDeclarationsAtSameVersion() {

		VirtualFile a = buildFile("declaration-a/build.gradle");
		VirtualFile b = buildFile("declaration-b/build.gradle");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT, VersionSource.property("spring.version")),
				context(ACME_APP), a, List.of());
		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_LIB), b, List.of());

		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(Map.of(SPRING_CORE,
				resolved(SPRING_UPDATE)));

		assertThat(result.candidates()).singleElement().satisfies(candidate -> {
			assertThat(candidate.getDeclaredVersions().hasVersionDrift()).isFalse();
			assertThat(candidate.getDeclaredVersions().hasDeclarationDrift()).isTrue();
			assertThat(candidate.getDeclaredVersions().hasDrift()).isTrue();
		});
	}

	@Test
	void collapsesGovernedAgreeingCandidatesIntoUpgradeGroup() {

		VirtualFile file = buildFile("group/pom.xml");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), file, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT), context(ACME_APP), file, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), SPRING_TEST,
				resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, new TestDependencyRule("Spring Framework"), SPRING_TEST,
						new TestDependencyRule("Spring Framework"))));

		assertThat(result.candidates()).singleElement().isInstanceOfSatisfying(UpgradeGroup.class, group -> {
			assertThat(group.getDependencyName()).isEqualTo("Spring Framework");
			assertThat(group.getCurrentVersion()).isEqualTo(SPRING_CURRENT);
			assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId).containsExactly(SPRING_CORE,
					SPRING_TEST);
		});
	}

	@Test
	void keepsDivergentGovernedArtifactAsOwnRow() {

		ArtifactId springJdbc = ArtifactId.of("org.springframework", "spring-jdbc");
		ArtifactVersion older = ArtifactVersion.of("6.0.9");
		VirtualFile file = buildFile("group/pom.xml");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), file, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT), context(ACME_APP), file, List.of());
		aggregator.add(dependency(springJdbc, older), context(ACME_APP), file, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), SPRING_TEST,
				resolved(SPRING_UPDATE), springJdbc, resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, new TestDependencyRule("Spring Framework"), SPRING_TEST,
						new TestDependencyRule("Spring Framework"), springJdbc,
						new TestDependencyRule("Spring Framework"))));

		assertThat(result.candidates()).hasSize(2);
		assertThat(result.getFirst()).isInstanceOfSatisfying(UpgradeGroup.class,
				group -> assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId)
						.containsExactly(SPRING_CORE, SPRING_TEST));
		assertThat(result.getLast()).isNotInstanceOf(UpgradeGroup.class)
				.extracting(UpgradeCandidate::getArtifactId).isEqualTo(springJdbc);
	}

	@Test
	void equalCohortsTieBreakToHigherVersion() {

		ArtifactId springAop = ArtifactId.of("org.springframework", "spring-aop");
		ArtifactId springBeans = ArtifactId.of("org.springframework", "spring-beans");
		ArtifactVersion older = ArtifactVersion.of("6.0.9");
		VirtualFile file = buildFile("group/pom.xml");
		TestDependencyRule rule = new TestDependencyRule("Spring Framework");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), file, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT), context(ACME_APP), file, List.of());
		aggregator.add(dependency(springAop, older), context(ACME_APP), file, List.of());
		aggregator.add(dependency(springBeans, older), context(ACME_APP), file, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), SPRING_TEST,
				resolved(SPRING_UPDATE), springAop, resolved(SPRING_UPDATE), springBeans, resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases, rules(
				Map.of(SPRING_CORE, rule, SPRING_TEST, rule, springAop, rule, springBeans, rule)));

		assertThat(result.candidates()).hasSize(3);
		assertThat(result.candidates()).filteredOn(UpgradeGroup.class::isInstance).singleElement()
				.isInstanceOfSatisfying(UpgradeGroup.class, group -> {
					assertThat(group.getCurrentVersion()).isEqualTo(SPRING_CURRENT);
					assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId)
							.containsExactly(SPRING_CORE, SPRING_TEST);
				});
	}

	@Test
	void driftingArtifactWithMatchingOccurrenceJoinsGroupCarryingDrift() {

		ArtifactId springJdbc = ArtifactId.of("org.springframework", "spring-jdbc");
		ArtifactVersion older = ArtifactVersion.of("6.0.9");
		VirtualFile a = buildFile("group-a/pom.xml");
		VirtualFile b = buildFile("group-b/pom.xml");
		TestDependencyRule rule = new TestDependencyRule("Spring Framework");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), a, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT), context(ACME_APP), a, List.of());
		aggregator.add(dependency(springJdbc, SPRING_CURRENT), context(ACME_APP), a, List.of());
		aggregator.add(dependency(springJdbc, older), context(ACME_LIB), b, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), SPRING_TEST,
				resolved(SPRING_UPDATE), springJdbc, resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, rule, SPRING_TEST, rule, springJdbc, rule)));

		assertThat(result.candidates()).singleElement().isInstanceOfSatisfying(UpgradeGroup.class, group -> {
			assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId).containsExactly(SPRING_CORE,
					springJdbc, SPRING_TEST);
			assertThat(group.getCurrentVersion()).isEqualTo(older);
			assertThat(group.getDeclaredVersions().hasVersionDrift()).isTrue();
		});
	}

	@Test
	void fullyDriftedArtifactBelowGroupStaysOwnRow() {

		ArtifactId springJdbc = ArtifactId.of("org.springframework", "spring-jdbc");
		VirtualFile a = buildFile("group-a/pom.xml");
		VirtualFile b = buildFile("group-b/pom.xml");
		TestDependencyRule rule = new TestDependencyRule("Spring Framework");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), a, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT), context(ACME_APP), a, List.of());
		aggregator.add(dependency(springJdbc, ArtifactVersion.of("6.0.9")), context(ACME_APP), a, List.of());
		aggregator.add(dependency(springJdbc, ArtifactVersion.of("6.1.0")), context(ACME_LIB), b, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), SPRING_TEST,
				resolved(SPRING_UPDATE), springJdbc, resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, rule, SPRING_TEST, rule, springJdbc, rule)));

		assertThat(result).hasSize(2);
		assertThat(result.getLast()).isNotInstanceOf(UpgradeGroup.class)
				.satisfies(candidate -> {
					assertThat(candidate.getArtifactId()).isEqualTo(springJdbc);
					assertThat(candidate.getDeclaredVersions().hasVersionDrift()).isTrue();
				});
	}

	@Test
	void unnamedRuleFallsThroughToInferredGroup() {

		VirtualFile file = buildFile("group/pom.xml");
		TestDependencyRule unnamed = new TestDependencyRule("");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), file, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT), context(ACME_APP), file, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), SPRING_TEST,
				resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, unnamed, SPRING_TEST, unnamed)));

		assertThat(result.candidates()).singleElement().isInstanceOfSatisfying(UpgradeGroup.class,
				group -> assertThat(group.getRowLabel()).isEqualTo("spring"));
	}

	@Test
	void rulesWithSameNameAcrossEcosystemsNeverGroup() {

		VirtualFile gradle = buildFile("eco-a/build.gradle");
		VirtualFile maven = buildFile("eco-b/pom.xml");
		TestDependencyRule rule = new TestDependencyRule("Spring Framework");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), gradle, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT),
				new TestProjectDependencyContext(ACME_LIB, new OtherEcosystemAssistant()), maven, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), SPRING_TEST,
				resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, rule, SPRING_TEST, rule)));

		assertThat(result.candidates()).hasSize(2).noneMatch(UpgradeGroup.class::isInstance);
	}

	@Test
	void groupOffersIntersectionOfMemberReleases() {

		ArtifactVersion next = ArtifactVersion.of("6.3.0");
		VirtualFile file = buildFile("group/pom.xml");
		TestDependencyRule rule = new TestDependencyRule("Spring Framework");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), file, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT), context(ACME_APP), file, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE, next),
				SPRING_TEST, resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, rule, SPRING_TEST, rule)));

		assertThat(result.candidates()).singleElement().isInstanceOfSatisfying(UpgradeGroup.class, group -> {
			Releases offered = group.getUpdateCandidate().getReleases();
			assertThat(offered).containsRelease(SPRING_UPDATE);
			assertThat(offered).doesNotContainRelease(next);
		});
	}

	@Test
	void inlineMemberAtAgreeingVersionJoinsPropertyBackedGroup() {

		ArtifactId springJdbc = ArtifactId.of("org.springframework", "spring-jdbc");
		VirtualFile file = buildFile("group/pom.xml");
		TestDependencyRule rule = new TestDependencyRule("Spring Framework");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT, VersionSource.property("spring.version")),
				context(ACME_APP), file, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT, VersionSource.property("spring.version")),
				context(ACME_APP), file, List.of());
		aggregator.add(dependency(springJdbc, SPRING_CURRENT), context(ACME_APP), file, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), SPRING_TEST,
				resolved(SPRING_UPDATE), springJdbc, resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, rule, SPRING_TEST, rule, springJdbc, rule)));

		assertThat(result.candidates()).singleElement().isInstanceOfSatisfying(UpgradeGroup.class, group -> {
			assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId).containsExactly(SPRING_CORE,
					springJdbc, SPRING_TEST);
			assertThat(group.getUpdateCandidate().hasPropertyVersion()).isTrue();
		});
	}

	@Test
	void driftingArtifactSharingVersionPropertyJoinsGroup() {

		ArtifactId springJdbc = ArtifactId.of("org.springframework", "spring-jdbc");
		VirtualFile a = buildFile("group-a/pom.xml");
		VirtualFile b = buildFile("group-b/pom.xml");
		TestDependencyRule rule = new TestDependencyRule("Spring Framework");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT, VersionSource.property("spring.version")),
				context(ACME_APP), a, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT, VersionSource.property("spring.version")),
				context(ACME_APP), a, List.of());
		aggregator.add(dependency(springJdbc, ArtifactVersion.of("6.1.5"), VersionSource.property("spring.version")),
				context(ACME_LIB), b, List.of());
		aggregator.add(dependency(springJdbc, ArtifactVersion.of("6.0.9")), context(ACME_LIB), b, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), SPRING_TEST,
				resolved(SPRING_UPDATE), springJdbc, resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, rule, SPRING_TEST, rule, springJdbc, rule)));

		assertThat(result.candidates()).singleElement().isInstanceOfSatisfying(UpgradeGroup.class, group -> {
			assertThat(group.getMembers()).extracting(UpgradeCandidate::getArtifactId).containsExactly(SPRING_CORE,
					springJdbc, SPRING_TEST);
			assertThat(group.getCurrentVersion()).isEqualTo(ArtifactVersion.of("6.0.9"));
			assertThat(group.getDeclaredVersions().hasVersionDrift()).isTrue();
		});
	}

	@Test
	void relabelsLoneGovernedArtifactWithRuleName() {

		VirtualFile file = buildFile("group/pom.xml");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), file, List.of());
		aggregator.add(dependency(LETTUCE_CORE, LETTUCE_CURRENT), context(ACME_APP), file, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), LETTUCE_CORE,
				resolved(LETTUCE_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, new TestDependencyRule("Spring Framework"))));

		assertThat(result.candidates()).extracting(UpgradeCandidate::getRowLabel).containsExactly("lettuce-core",
				"Spring Framework");
	}

	@Test
	void leftoverGovernedArtifactKeepsCoordinateLabelWhenGroupClaimsRuleName() {

		ArtifactId springJdbc = ArtifactId.of("org.springframework", "spring-jdbc");
		VirtualFile file = buildFile("group/pom.xml");
		TestDependencyRule rule = new TestDependencyRule("Spring Framework");

		aggregator.add(dependency(SPRING_CORE, SPRING_CURRENT), context(ACME_APP), file, List.of());
		aggregator.add(dependency(SPRING_TEST, SPRING_CURRENT), context(ACME_APP), file, List.of());
		aggregator.add(dependency(springJdbc, ArtifactVersion.of("6.0.9")), context(ACME_APP), file, List.of());

		Map<ArtifactId, ReleaseLookupResult> releases = Map.of(SPRING_CORE, resolved(SPRING_UPDATE), SPRING_TEST,
				resolved(SPRING_UPDATE), springJdbc, resolved(SPRING_UPDATE));
		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(releases,
				rules(Map.of(SPRING_CORE, rule, SPRING_TEST, rule, springJdbc, rule)));

		assertThat(result.candidates()).extracting(UpgradeCandidate::getRowLabel).containsExactly("Spring Framework",
				"spring-jdbc");
	}

	@Test
	void mergedDeclarationsCombineDeclarationSources() {

		VirtualFile file = buildFile("sources/build.gradle");

		Dependency first = dependency(LETTUCE_CORE, LETTUCE_CURRENT);
		Dependency second = dependency(LETTUCE_CORE, LETTUCE_UPDATE);

		aggregator.add(first, context(ACME_APP), file, List.of());
		aggregator.add(second, context(ACME_LIB), file, List.of());

		DependencyCheckAggregator.Entry entry = new DependencyCheckAggregator.Entry(List.of(), List.of(),
				List.of(new DeclarationSite(file, ACME_APP, first), new DeclarationSite(file, ACME_LIB, second)));
		DeclaredDependency dependency = aggregator.mergeDeclarations(LETTUCE_CORE, entry);

		assertThat(dependency.getVersionSources()).hasSize(2);
	}

	private static Dependency dependency(ArtifactId artifactId, ArtifactVersion version) {
		return dependency(artifactId, version, VersionSource.declared(version.toString()));
	}

	private static Dependency dependency(ArtifactId artifactId, ArtifactVersion version, VersionSource versionSource) {
		Dependency dependency = new Dependency(artifactId, version);
		dependency.addVersionSource(versionSource);
		return dependency;
	}

	private static TestProjectDependencyContext context(ProjectId projectId) {
		return new TestProjectDependencyContext(projectId, new TestInterfaceAssistant());
	}

	private static VirtualFile buildFile(String path) {
		return new MockVirtualFile(path, "// test");
	}

	private static ReleaseLookupResult resolved(ArtifactVersion... versions) {
		return ReleaseLookupResult.of(Releases.of(Arrays.stream(versions).map(Release::of).toList()));
	}

	private static DependencyRuleService rules(Map<ArtifactId, DependencyRule> rules) {

		return new DependencyRuleService() {

			@Override
			public DependencyRule resolve(ResolutionContext context) {
				return rules.getOrDefault(context.getArtifactId(), DependencyRule.absent());
			}

		};
	}

	static class OtherEcosystemAssistant extends TestInterfaceAssistant {

	}


}
