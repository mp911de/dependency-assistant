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

import javax.swing.Icon;

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
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
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

	DependencyCheckAggregator aggregator = new DependencyCheckAggregator(new MockProjectEx(() -> {
	}), new StateService());

	@Test
	void groupsDeclarationsByArtifact() {

		VirtualFile a = new MockVirtualFile("aggregate-a/build.gradle", "// test");
		VirtualFile b = new MockVirtualFile("aggregate-b/build.gradle", "// test");
		ReleaseSource mavenCentral = new TestReleaseSource("mavenCentral");
		ReleaseSource pluginPortal = new TestReleaseSource("pluginPortal");

		aggregator.add(dependency("io.lettuce", "lettuce-core", "7.4.1.RELEASE"), context("com.acme", "app"),
				a, List.of(mavenCentral));
		aggregator.add(dependency("io.lettuce", "lettuce-core", "7.5.0.RELEASE"), context("com.acme", "lib"),
				b, List.of(pluginPortal));

		List<ArtifactId> artifacts = new ArrayList<>();
		aggregator.forEach(artifacts::add);
		List<Collection<ReleaseSource>> releaseSources = new ArrayList<>();
		aggregator.forEachArtifact((artifactId, sources) -> releaseSources.add(sources));

		assertThat(artifacts).containsExactly(ArtifactId.of("io.lettuce", "lettuce-core"));
		assertThat(releaseSources).singleElement().satisfies(sources -> assertThat(sources)
				.containsExactlyInAnyOrder(mavenCentral, pluginPortal));
		assertThat(aggregator.getFiles()).containsExactly(a, b);
	}

	@Test
	void createsSortedCandidatesAndCarriesReleaseErrors() {

		VirtualFile a = new MockVirtualFile("result-a/build.gradle", "// test");
		VirtualFile b = new MockVirtualFile("result-b/build.gradle", "// test");
		ArtifactId spring = ArtifactId.of("org.springframework", "spring-core");
		ArtifactId lettuce = ArtifactId.of("io.lettuce", "lettuce-core");

		aggregator.add(dependency(spring, "6.2.0"), context("com.acme", "app"), a, List.of());
		aggregator.add(dependency(lettuce, "7.4.1.RELEASE"), context("com.acme", "lib"), b, List.of());

		DependencyCheckResult result = aggregator.toDependencyCheckResult(java.util.Map.of(spring,
				new ReleaseLookupResult(null, List.of(release("6.2.1"))), lettuce,
				new ReleaseLookupResult(null, List.of(release("7.5.0.RELEASE"))),
				ArtifactId.of("broken", "artifact"), new ReleaseLookupResult("broken: unavailable", List.of())));

		assertThat(result.candidates()).extracting(candidate -> candidate.getArtifactId().artifactId())
				.containsExactly("lettuce-core", "spring-core");
		assertThat(result.candidates()).extracting(UpdateCandidate::currentVersion)
				.containsExactly(ArtifactVersion.of("7.4.1.RELEASE"), ArtifactVersion.of("6.2.0"));
		assertThat(result.errors()).containsExactly("broken: unavailable");
		assertThat(result.files()).containsExactly(a, b);
	}

	@Test
	void mergedDeclarationsCombineDeclarationSources() {

		VirtualFile file = new MockVirtualFile("sources/build.gradle", "// test");

		ArtifactId artifactId = ArtifactId.of("io.lettuce", "lettuce-core");
		Dependency first = dependency(artifactId, "7.4.1.RELEASE");
		Dependency second = dependency(artifactId, "7.5.0.RELEASE");

		aggregator.add(first, context("com.acme", "app"), file, List.of());
		aggregator.add(second, context("com.acme", "lib"), file, List.of());

		DependencyCheckAggregator.Entry entry = new DependencyCheckAggregator.Entry(List.of(), List.of(),
				List.of(new DeclarationSite(file, ProjectId.of("com.acme", "app"), first),
						new DeclarationSite(file, ProjectId.of("com.acme", "lib"), second)));
		DeclaredDependency dependency = aggregator.mergeDeclarations(artifactId, entry);

		assertThat(dependency.getVersionSources()).hasSize(2);
	}

	private static Dependency dependency(String groupId, String artifactId, String version) {
		return dependency(ArtifactId.of(groupId, artifactId), version);
	}

	private static Dependency dependency(ArtifactId artifactId, String version) {
		Dependency dependency = new Dependency(artifactId, ArtifactVersion.of(version));
		dependency.addVersionSource(biz.paluch.dap.artifact.VersionSource.declared(version));
		return dependency;
	}

	private static TestContext context(String groupId, String artifactId) {
		return new TestContext(ProjectId.of(groupId, artifactId), new TestInterfaceAssistant());
	}

	private static Release release(String version) {
		return Release.of(ArtifactVersion.of(version));
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

	private static class TestInterfaceAssistant implements InterfaceAssistant {

		@Override
		public String getDisplayName() {
			return "Test";
		}

		@Override
		public String getDisplayName(VirtualFile file) {
			return "Test";
		}

		@Override
		public Icon getGutterIcon(ArtifactDeclaration declaration) {
			return null;
		}

		@Override
		public Icon getNavigateIcon(ArtifactDeclaration declaration) {
			return null;
		}

		@Override
		public Icon getTableIcon(Dependency dependency) {
			return null;
		}

	}

}
