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

package biz.paluch.dap.support;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.VersionProperty;
import com.intellij.mock.MockProjectEx;
import com.intellij.mock.MockPsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VersionUpgradeLookup}.
 *
 * @author Mark Paluch
 */
class VersionUpgradeLookupUnitTests {

	ArtifactId artifactId = ArtifactId.of("foo", "bar");

	PsiElement element = new MockPsiElement(() -> {
	});

	ArtifactReference tieringReference = ArtifactReference
			.from(it -> it.artifact(artifactId).versionSource(VersionSource.none())
					.declarationSource(DeclarationSource.dependency())
					.declarationElement(element));

	@Test
	void suggestUpgradesResolvesReferenceAgainstCache() {

		Cache cache = new Cache();
		cache.putVersionOptions(artifactId, List.of(Release.of("2.0.0"), Release.of("1.0.0")));

		VersionUpgradeLookup lookup = lookup(cache, null, declaredReference("1.0.0"));

		UpgradeSuggestion result = lookup.suggestUpgrades(element).getUpgradeSuggestion();

		assertThat(result.getStrategy()).isEqualTo(UpgradeStrategy.MAJOR);
		assertThat(result.getRelease().version().toString()).isEqualTo("2.0.0");
	}

	@Test
	void suggestUpgradesStayWithinDeclaredVersioningScheme() {

		Cache cache = new Cache();
		cache.putVersionOptions(artifactId, List.of( //
				Release.from("2025.0.6", "2026-06-08"), //
				Release.from("2020.0.0", "2020-10-26"), //
				Release.from("Dysprosium-SR25", "2021-11-09"), //
				Release.from("Aluminium-RELEASE", "2017-02-22")));

		VersionUpgradeLookup lookup = lookup(cache, null, declaredReference("Aluminium-RELEASE"));

		AvailableUpgrades upgrades = lookup.suggestUpgrades(element);

		assertThat(upgrades.getUpgrades().values()).extracting(it -> it.getRelease().version().toString())
				.containsOnly("Dysprosium-SR25");
	}

	@Test
	void suggestUpgradesReturnsNoneWhenCacheHasNoReleases() {

		VersionUpgradeLookup lookup = lookup(new Cache(), null, declaredReference("1.0.0"));

		assertThat(lookup.suggestUpgrades(element).isPresent()).isFalse();
	}

	@Test
	void suggestUpgradesReturnsNoneForUnresolvedReference() {

		Cache cache = new Cache();
		cache.putVersionOptions(artifactId, List.of(Release.of("2.0.0")));

		VersionUpgradeLookup lookup = lookup(cache, null, ArtifactReference.unresolved());

		assertThat(lookup.suggestUpgrades(element).isPresent()).isFalse();
	}

	@Test
	void suggestUpgradesReturnsNoneWhenAlreadyAtLatest() {

		Cache cache = new Cache();
		cache.putVersionOptions(artifactId, List.of(Release.of("1.5.0"), Release.of("1.0.0")));

		VersionUpgradeLookup lookup = lookup(cache, null, declaredReference("2.0.0"));

		assertThat(lookup.suggestUpgrades(element).isPresent()).isFalse();
	}

	@Test
	void getCurrentVersionReturnsProjectStateVersionWhenPresent() {

		ProjectState projectState = new StubProjectState().withDependency(artifactId, ArtifactVersion.of("1.5.0"));
		VersionUpgradeLookup lookup = lookup(new Cache(), projectState, declaredReference("1.0.0"));

		ArtifactVersion current = lookup.getCurrentVersion(declaredReference("1.0.0"));

		assertThat(current).isEqualTo(ArtifactVersion.of("1.5.0"));
	}

	@Test
	void getCurrentVersionFallsBackToDeclaredVersionWhenProjectStateMisses() {

		ProjectState emptyState = new StubProjectState();
		VersionUpgradeLookup lookup = lookup(new Cache(), emptyState, declaredReference("1.0.0"));

		ArtifactVersion current = lookup.getCurrentVersion(declaredReference("1.0.0"));

		assertThat(current).isEqualTo(ArtifactVersion.of("1.0.0"));
	}

	@Test
	void getCurrentVersionReturnsNullForUnresolvedReference() {

		VersionUpgradeLookup lookup = lookup(new Cache(), null, ArtifactReference.unresolved());

		assertThat(lookup.getCurrentVersion(ArtifactReference.unresolved())).isNull();
	}

	@Test
	void determineUpgradeReturnsNoneWhenNoReleasesAvailable() {
		assertThat(determineUpgrade(tieringReference, ArtifactVersion.of("1.0.0"), List.of())
				.isPresent()).isFalse();
	}

	@Test
	void determineUpgradeReturnsNoneWhenCurrentIsAlreadyLatest() {

		List<Release> releases = List.of(Release.of("1.5.0"), Release.of("1.0.0"));

		assertThat(determineUpgrade(tieringReference, ArtifactVersion.of("2.0.0"), releases)
				.isPresent()).isFalse();
	}

	@Test
	void determineUpgradeDetectsPatchUpgrade() {

		List<Release> releases = List.of(Release.of("1.0.1"), Release.of("1.0.0"));

		UpgradeSuggestion result = determineUpgrade(tieringReference, ArtifactVersion.of("1.0.0"),
				releases);

		assertThat(result.getStrategy()).isEqualTo(UpgradeStrategy.PATCH);
		assertThat(result.getRelease().version().toString()).isEqualTo("1.0.1");
	}

	@Test
	void determineUpgradeDetectsMinorUpgrade() {

		List<Release> releases = List.of(Release.of("1.1.0"), Release.of("1.0.0"));

		UpgradeSuggestion result = determineUpgrade(tieringReference, ArtifactVersion.of("1.0.0"),
				releases);

		assertThat(result.getStrategy()).isEqualTo(UpgradeStrategy.MINOR);
		assertThat(result.getRelease().version().toString()).isEqualTo("1.1.0");
	}

	@Test
	void determineUpgradeDetectsMajorUpgrade() {

		List<Release> releases = List.of(Release.of("2.0.0"), Release.of("1.0.0"));

		UpgradeSuggestion result = determineUpgrade(tieringReference, ArtifactVersion.of("1.0.0"),
				releases);

		assertThat(result.getStrategy()).isEqualTo(UpgradeStrategy.MAJOR);
		assertThat(result.getRelease().version().toString()).isEqualTo("2.0.0");
	}

	@Test
	void determineUpgradeReturnsNoneWhenOnlyPreviewReleasesAvailable() {

		List<Release> releases = List.of(Release.of("2.0.0.M1"), Release.of("1.0.0"));

		assertThat(determineUpgrade(tieringReference, ArtifactVersion.of("1.0.0"), releases)
				.isPresent()).isFalse();
	}

	@Test
	void determineUpgradeMessageIsNonNull() {

		List<Release> releases = List.of(Release.of("2.0.0"), Release.of("1.0.0"));

		UpgradeSuggestion result = determineUpgrade(tieringReference, ArtifactVersion.of("1.0.0"),
				releases);

		assertThat(result.getMessage()).isNotNull().isNotEmpty();
	}

	private VersionUpgradeLookup lookup(Cache cache, ProjectState projectState,
			ArtifactReference reference) {

		Project project = new MockProjectEx(() -> {
		});
		LookupContext context = new LookupContext(project, availableContext(), cache, projectState,
				new GitVersionResolver(cache));
		return new VersionUpgradeLookup(context, e -> reference);
	}

	private ArtifactReference declaredReference(String version) {
		return ArtifactReference.from(it -> it.artifact(artifactId)
				.versionSource(VersionSource.declared(version))
				.declarationSource(DeclarationSource.dependency())
				.version(ArtifactVersion.of(version)).declarationElement(element));
	}

	private static ProjectBuildContext availableContext() {
		return new ProjectBuildContext() {

			@Override
			public boolean isAvailable() {
				return true;
			}

			@Override
			public ProjectId getProjectId() {
				throw new UnsupportedOperationException();
			}

			@Override
			public List<biz.paluch.dap.artifact.ReleaseSource> getReleaseSources() {
				return List.of();
			}

		};
	}

	private UpgradeSuggestion determineUpgrade(ArtifactReference reference, ArtifactVersion version,
			List<Release> releases) {
		AvailableUpgrades availableUpgrades = AvailableUpgrades.determineUpgrades(reference, version,
				Releases.of(releases));
		return availableUpgrades.isPresent() ? availableUpgrades.getUpgradeSuggestion() : UpgradeSuggestion.none();
	}

	/**
	 * In-memory {@link ProjectState} double whose only meaningful operation is
	 * {@link #findDependency(ArtifactId)}.
	 */
	static class StubProjectState implements ProjectState {

		private final Map<ArtifactId, Dependency> dependencies = new HashMap<>();

		StubProjectState withDependency(ArtifactId artifactId, ArtifactVersion currentVersion) {
			dependencies.put(artifactId, new Dependency(artifactId, currentVersion));
			return this;
		}

		@Override
		public @Nullable Dependency findDependency(ArtifactId artifactId) {
			return dependencies.get(artifactId);
		}

		@Override
		public void setDependencies(DependencyCollector collector) {
		}

		@Override
		public boolean hasDependencies() {
			return !dependencies.isEmpty();
		}

		@Override
		public void invalidateDependencies() {
		}

		@Override
		public @Nullable VersionProperty findProperty(String propertyName, Predicate<VersionProperty> filter) {
			return null;
		}

		@Override
		public @Nullable ProjectProperty findProjectProperty(String propertyName, Predicate<VersionProperty> filter) {
			return null;
		}

	}

}
