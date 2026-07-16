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

package biz.paluch.dap.assistant.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.fixtures.TestProjectDependencyContext;
import biz.paluch.dap.fixtures.TestProjects;
import biz.paluch.dap.fixtures.TestReleaseSource;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link DependencyCheckAggregator}.
 *
 * @author Mark Paluch
 */
// TODO: overly complex
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

	DependencyCheckAggregator aggregator = new DependencyCheckAggregator(TestProjects.PROJECT, new StateService());

	@Test
	void groupsDeclarationsByArtifact() {

		VirtualFile a = buildFile("aggregate-a/build.gradle");
		VirtualFile b = buildFile("aggregate-b/build.gradle");
		ReleaseSource mavenCentral = new TestReleaseSource("mavenCentral");
		ReleaseSource pluginPortal = new TestReleaseSource("pluginPortal");

		aggregator.add(dependency(LETTUCE_CORE, LETTUCE_CURRENT), context(ACME_APP), a, List.of(mavenCentral));
		aggregator.add(dependency(LETTUCE_CORE, LETTUCE_UPDATE), context(ACME_LIB), b, List.of(pluginPortal));

		List<ArtifactId> artifacts = new ArrayList<>();
		aggregator.forEach(pkg -> artifacts.add(pkg.getArtifactId()));
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

		assertThat(result).extracting(candidate -> candidate.getArtifactId().artifactId())
				.containsExactly(LETTUCE_CORE.artifactId(), SPRING_CORE.artifactId());
		assertThat(result).extracting(decision -> decision.getCurrentVersion())
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

		assertThat(result).singleElement().satisfies(candidate -> {
			DeclaredVersions declaredVersions = result.declaredVersions().get(candidate);
			assertThat(declaredVersions.hasVersionDrift()).isFalse();
			assertThat(declaredVersions.hasDeclarationDrift()).isTrue();
			assertThat(declaredVersions.hasDrift()).isTrue();
		});
	}

	@Test
	void keepsPresentationFactsSeparateForSameCoordinateAcrossPackageSystems() {

		TestProjectDependencyContext maven = context(ACME_APP);
		TestProjectDependencyContext npm = new TestProjectDependencyContext(ACME_LIB,
				new OtherEcosystemAssistant()) {

			@Override
			public PackageSystem getPackageSystem() {
				return PackageSystem.NPM;
			}

		};

		aggregator.add(dependency(LETTUCE_CORE, LETTUCE_CURRENT), maven, buildFile("pom.xml"), List.of());
		aggregator.add(dependency(LETTUCE_CORE, LETTUCE_CURRENT), npm, buildFile("package.json"), List.of());

		DependencyUpgradeCandidates result = aggregator.toDependencyCheckResult(
				Map.of(LETTUCE_CORE, resolved(LETTUCE_UPDATE)));

		assertThat(result.decisions()).hasSize(2);
		assertThat(result.assistants().keySet()).containsExactlyInAnyOrderElementsOf(result.decisions());
		assertThat(result.declaredVersions().keySet()).containsExactlyInAnyOrderElementsOf(result.decisions());
		assertThat(result.assistants().values()).anyMatch(OtherEcosystemAssistant.class::isInstance);
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
		return context -> rules.getOrDefault(context.getArtifactId(), DependencyRule.absent());
	}

	static class OtherEcosystemAssistant extends TestInterfaceAssistant {

	}


}
