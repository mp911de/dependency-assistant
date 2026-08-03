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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.fixtures.Coordinates;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.fixtures.TestProjectDependencyContext;
import biz.paluch.dap.fixtures.TestProjects;
import biz.paluch.dap.fixtures.TestReleaseSource;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;
import static biz.paluch.dap.fixtures.Releases.*;

/**
 * Unit tests for {@link DependencyCheckAggregator}.
 *
 * @author Mark Paluch
 */
class DependencyCheckAggregatorTests {

	@Test
	void loadsEachContextReleaseSourceOnceAndSharesItAcrossArtifacts() {

		AtomicInteger loads = new AtomicInteger();
		ReleaseSource source = new TestReleaseSource("context");
		TestProjectDependencyContext context = new TestProjectDependencyContext(ACME_APP,
				new TestInterfaceAssistant()) {

			@Override
			public List<ReleaseSource> getReleaseSources() {
				loads.incrementAndGet();
				return List.of(source);
			}

		};

		aggregator.add(dependency(LETTUCE_CURRENT), context, buildFile("pom.xml"), List.of());
		aggregator.add(dependency(VAVR_CURRENT), context, buildFile("pom.xml"), List.of());
		aggregator.addContextReleaseSources();

		List<Collection<ReleaseSource>> releaseSources = new ArrayList<>();
		aggregator.forEachArtifact((artifact, sources) -> releaseSources.add(sources));
		assertThat(loads).hasValue(1);
		assertThat(releaseSources).allSatisfy(sources -> assertThat(sources).contains(source));
	}

	private static final Coordinates LETTUCE_CURRENT = Coordinates.of(LETTUCE_CORE, "7.4.1.RELEASE");

	private static final Coordinates LETTUCE_UPDATE = Coordinates.of(LETTUCE_CORE, "7.5.0.RELEASE");

	private static final Coordinates VAVR_CURRENT = Coordinates.of(VAVR, "0.11.0");

	private static final PackageIdentity BROKEN_ARTIFACT = PackageIdentity.of(ArtifactId.of("broken", "artifact"),
			PackageSystem.MAVEN);

	private static final ProjectId ACME_APP = ProjectId.of("com.acme", "app");

	private static final ProjectId ACME_LIB = ProjectId.of("com.acme", "lib");

	private static final String BROKEN_ARTIFACT_ERROR = "broken: unavailable";

	private final DependencyCheckAggregator aggregator = new DependencyCheckAggregator(
			TestProjects.PROJECT, new StateService());

	@Test
	void groupsDeclarationsByArtifact() {

		VirtualFile a = buildFile("aggregate-a/build.gradle");
		VirtualFile b = buildFile("aggregate-b/build.gradle");
		ReleaseSource mavenCentral = new TestReleaseSource("mavenCentral");
		ReleaseSource pluginPortal = new TestReleaseSource("pluginPortal");

		aggregator.add(dependency(LETTUCE_CURRENT), context(ACME_APP), a, List.of(mavenCentral));
		aggregator.add(dependency(LETTUCE_UPDATE), context(ACME_LIB), b, List.of(pluginPortal));

		List<ArtifactId> artifacts = new ArrayList<>();
		aggregator.forEach(pkg -> artifacts.add(pkg.getArtifactId()));
		List<Collection<ReleaseSource>> releaseSources = new ArrayList<>();
		aggregator.forEachArtifact((artifactId, sources) -> releaseSources.add(sources));

		assertThat(artifacts).containsExactly(LETTUCE_CURRENT.getArtifactId());
		assertThat(releaseSources).singleElement().satisfies(sources -> assertThat(sources)
				.containsExactlyInAnyOrder(mavenCentral, pluginPortal));
		assertThat(aggregator.getFiles()).containsExactly(a, b);
	}

	@Test
	void createsSortedCandidatesAndCarriesReleaseErrors() {

		VirtualFile a = buildFile("result-a/build.gradle");
		VirtualFile b = buildFile("result-b/build.gradle");

		aggregator.add(dependency(VAVR_CURRENT), context(ACME_APP), a, List.of());
		aggregator.add(dependency(LETTUCE_CURRENT), context(ACME_LIB), b, List.of());

		Map<PackageIdentity, ReleaseLookupResult> releases = Map.of(VAVR_CURRENT.getPackageIdentity(), resolved(VAVR),
				LETTUCE_CURRENT.getPackageIdentity(), resolved(LETTUCE_CORE), BROKEN_ARTIFACT,
				ReleaseLookupResult.failed(BROKEN_ARTIFACT_ERROR));
		DependencyCheckResult result = aggregator.toDependencyCheckResult(releases);

		assertThat(result).extracting(upgrade -> upgrade.getArtifactId().artifactId())
				.containsExactly(LETTUCE_CURRENT.getArtifactId().artifactId(),
						VAVR_CURRENT.getArtifactId().artifactId());
		assertThat(result).extracting(DependencyUpgradeCandidate::getCurrentVersion)
				.containsExactly(LETTUCE_CURRENT.getVersion(), VAVR_CURRENT.getVersion());
		assertThat(result.errors()).containsExactly(BROKEN_ARTIFACT_ERROR);
		assertThat(result.scope().toList()).containsExactly(a, b);
	}

	@Test
	void carriesDeclarationDriftForInlineAndPropertyDeclarationsAtSameVersion() {

		VirtualFile a = buildFile("declaration-a/build.gradle");
		VirtualFile b = buildFile("declaration-b/build.gradle");

		aggregator.add(dependency(VAVR_CURRENT, VersionSource.property("vavr.version")),
				context(ACME_APP), a, List.of());
		aggregator.add(dependency(VAVR_CURRENT), context(ACME_LIB), b, List.of());

		DependencyCheckResult result = aggregator
				.toDependencyCheckResult(Map.of(VAVR_CURRENT.getPackageIdentity(), resolved(VAVR)));

		assertThat(result).singleElement().satisfies(upgrade -> {
			DeclaredVersions declaredVersions = upgrade.getDeclaredVersions();
			assertThat(declaredVersions.hasVersionDrift()).isFalse();
			assertThat(declaredVersions.hasDeclarationDrift()).isTrue();
			assertThat(declaredVersions.hasDrift()).isTrue();
		});
	}

	@Test
	void keepsPresentationFactsSeparateForSameCoordinateAcrossPackageSystems() {

		TestProjectDependencyContext maven = context(ACME_APP);
		TestProjectDependencyContext npm = new TestProjectDependencyContext(ACME_LIB, new TestInterfaceAssistant()) {

			@Override
			public PackageSystem getPackageSystem() {
				return PackageSystem.NPM;
			}

		};

		Dependency mavenDependency = dependency(LETTUCE_CURRENT);
		Dependency npmDependency = dependency(LETTUCE_CURRENT, PackageSystem.NPM);

		aggregator.add(mavenDependency, maven, buildFile("pom.xml"), List.of());
		aggregator.add(npmDependency, npm, buildFile("package.json"), List.of());

		DependencyCheckResult result = aggregator.toDependencyCheckResult(
				Map.of(mavenDependency.getPackageIdentity(), resolved(LETTUCE_CORE),
						npmDependency.getPackageIdentity(), resolved(LETTUCE_CORE)));

		assertThat(result.upgrades())
				.extracting(upgrade -> upgrade.getPackageIdentity().getPackageSystem())
				.containsExactlyInAnyOrder(PackageSystem.MAVEN, PackageSystem.NPM);
	}

	@Test
	void mergedDeclarationsCombineDeclarationSources() {

		VirtualFile file = buildFile("sources/build.gradle");

		Dependency first = dependency(LETTUCE_CURRENT);
		Dependency second = dependency(LETTUCE_UPDATE);

		aggregator.add(first, context(ACME_APP), file, List.of());
		aggregator.add(second, context(ACME_LIB), file, List.of());

		DependencyCheckAggregator.Entry entry = new DependencyCheckAggregator.Entry(List.of(), List.of(),
				List.of(new DeclarationSite(file, ACME_APP, first), new DeclarationSite(file, ACME_LIB, second)));
		DeclaredDependency dependency = aggregator.mergeDeclarations(LETTUCE_CURRENT.getPackageIdentity(), entry);

		assertThat(dependency.getVersionSources()).hasSize(2);
	}

	private static Dependency dependency(Coordinates coordinates) {
		return dependency(coordinates, VersionSource.declared(coordinates.getVersion().toString()));
	}

	private static Dependency dependency(Coordinates coordinates, VersionSource versionSource) {
		Dependency dependency = new Dependency(coordinates.getPackageIdentity(), coordinates.getVersion());
		dependency.addVersionSource(versionSource);
		return dependency;
	}

	private static Dependency dependency(Coordinates coordinates, PackageSystem packageSystem) {

		Dependency dependency = new Dependency(PackageIdentity.of(coordinates.getArtifactId(), packageSystem),
				coordinates.getVersion());
		dependency.addVersionSource(VersionSource.declared(coordinates.getVersion().toString()));
		return dependency;
	}

	private static TestProjectDependencyContext context(ProjectId projectId) {
		return new TestProjectDependencyContext(projectId, new TestInterfaceAssistant());
	}

	private static VirtualFile buildFile(String path) {
		return new MockVirtualFile(path, "// test");
	}

	private static ReleaseLookupResult resolved(CachedArtifact artifact) {
		return ReleaseLookupResult.of(Releases.of(artifact.getVersionOptions()));
	}

}
