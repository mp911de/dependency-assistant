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
import java.util.Collection;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.VersionUpgradeLookup;
import com.intellij.mock.MockProjectEx;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyCheckAggregator}.
 *
 * @author Mark Paluch
 */
class DependencyCheckAggregatorTests {

	ArtifactId LETTUCE_CORE = ArtifactId.of("io.lettuce", "lettuce-core");

	ArtifactId SPRING_CORE = ArtifactId.of("org.springframework", "spring-core");

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
		aggregator.forEach(artifacts::add);
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
				resolved(LETTUCE_UPDATE), BROKEN_ARTIFACT, lookupError(BROKEN_ARTIFACT_ERROR));
		DependencyCheckResult result = aggregator.toDependencyCheckResult(releases);

		assertThat(result.candidates()).extracting(candidate -> candidate.getArtifactId().artifactId())
				.containsExactly(LETTUCE_CORE.artifactId(), SPRING_CORE.artifactId());
		assertThat(result.candidates()).extracting(UpgradeCandidate::getCurrentVersion)
				.containsExactly(LETTUCE_CURRENT, SPRING_CURRENT);
		assertThat(result.errors()).containsExactly(BROKEN_ARTIFACT_ERROR);
		assertThat(result.files()).containsExactly(a, b);
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
		Dependency dependency = new Dependency(artifactId, version);
		dependency.addVersionSource(biz.paluch.dap.artifact.VersionSource.declared(version.toString()));
		return dependency;
	}

	private static TestContext context(ProjectId projectId) {
		return new TestContext(projectId, new TestInterfaceAssistant());
	}

	private static VirtualFile buildFile(String path) {
		return new MockVirtualFile(path, "// test");
	}

	private static ReleaseLookupResult resolved(ArtifactVersion version) {
		return new ReleaseLookupResult(null, Releases.just(Release.of(version)));
	}

	private static ReleaseLookupResult lookupError(String error) {
		return new ReleaseLookupResult(error, Releases.empty());
	}

	private record TestReleaseSource(String name) implements ReleaseSource {

		@Override
		public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) {
			return List.of();
		}

	}

	private record TestContext(ProjectId projectId, InterfaceAssistant interfaceAssistant)
			implements ProjectDependencyContext {

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return interfaceAssistant;
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isVersionElement(PsiElement element) {
			return false;
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public ProjectId getProjectId() {
			return projectId;
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return List.of();
		}

	}

}
