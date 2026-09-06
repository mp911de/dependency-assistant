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

package biz.paluch.dap.fixtures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.check.DeclarationSite;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.assistant.presentation.IconDependencyPresentation;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.state.ProjectId;
import com.intellij.mock.MockVirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Configurable upgrade candidates for tests.
 * <p>String coordinates use {@code group:artifact:version}. The default
 * candidate contains only its current release and has no recorded vulnerability
 * results.
 *
 * @author Mark Paluch
 */
public class TestCandidates {

	private TestCandidates() {
	}

	public static DependencyUpgradeCandidate candidate(String coordinates) {
		return candidate(coordinates, it -> {
		});
	}

	public static DependencyUpgradeCandidate candidate(String coordinates, Consumer<CandidateSpec> configurer) {
		Coordinates parsed = Coordinates.of(coordinates);
		return candidate(parsed.getArtifactId(), parsed.getVersion(), configurer);
	}

	public static DependencyUpgradeCandidate candidate(ArtifactId artifactId, String currentVersion,
			Consumer<CandidateSpec> configurer) {
		return candidate(artifactId, ArtifactVersion.of(currentVersion), configurer);
	}

	public static DependencyUpgradeCandidate candidate(ArtifactId artifactId, ArtifactVersion currentVersion,
			Consumer<CandidateSpec> configurer) {

		CandidateSpec spec = new CandidateSpec(artifactId, currentVersion);
		configurer.accept(spec);
		return spec.create();
	}

	/**
	 * Candidate customization. The current version remains in the release set even
	 * when other releases are supplied.
	 */
	public static class CandidateSpec {

		private final ArtifactId artifactId;

		private final ArtifactVersion currentVersion;

		private final Map<ArtifactVersion, Vulnerabilities> vulnerableVersions = new HashMap<>();

		private final List<String> declaredVersions = new ArrayList<>();

		private Releases releases;

		private @Nullable VulnerabilityRepository vulnerabilityRepository;

		private DependencyRule rule = DependencyRule.absent();

		private DependencyAssistant assistant = TestAssistant.INSTANCE;

		private final List<VersionSource> versionSources = new ArrayList<>();

		CandidateSpec(ArtifactId artifactId, ArtifactVersion currentVersion) {
			this.artifactId = artifactId;
			this.currentVersion = currentVersion;
			this.releases = Releases.just(currentVersion);
			this.versionSources.add(VersionSource.declared(currentVersion.toString()));
		}

		public CandidateSpec releases(String... versions) {
			this.releases = TestReleases.from(versions);
			return this;
		}

		public CandidateSpec releases(ArtifactVersion... versions) {
			this.releases = TestReleases.from(versions);
			return this;
		}

		public CandidateSpec releases(Releases releases) {
			this.releases = releases;
			return this;
		}

		/**
		 * Register vulnerabilities for a version. Unlisted current and release versions
		 * are then treated as clean.
		 */
		public CandidateSpec vulnerable(String version, Vulnerabilities vulnerabilities) {
			this.vulnerableVersions.put(ArtifactVersion.of(version), vulnerabilities);
			return this;
		}

		/**
		 * Use an explicit repository. It cannot be combined with {@link #vulnerable}
		 * entries.
		 */
		public CandidateSpec vulnerabilities(VulnerabilityRepository vulnerabilityRepository) {
			this.vulnerabilityRepository = vulnerabilityRepository;
			return this;
		}

		/**
		 * Use a present rule with the given dependency name.
		 */
		public CandidateSpec rule(String dependencyName) {
			return rule(new TestDependencyRule(dependencyName));
		}

		public CandidateSpec rule(DependencyRule rule) {
			this.rule = rule;
			return this;
		}

		public CandidateSpec assistant(DependencyAssistant assistant) {
			this.assistant = assistant;
			return this;
		}

		public CandidateSpec versionProperty(String propertyName) {
			return versionSource(VersionSource.property(propertyName));
		}

		/**
		 * Replace the default declared-version source.
		 */
		public CandidateSpec versionSource(VersionSource versionSource) {
			return versionSources(versionSource);
		}

		/**
		 * Replace all version sources.
		 */
		public CandidateSpec versionSources(VersionSource... versionSources) {
			this.versionSources.clear();
			this.versionSources.addAll(List.of(versionSources));
			return this;
		}

		/**
		 * Add declaration sites in separate files. These sites use plain declared
		 * versions, independently of the candidate version sources.
		 */
		public CandidateSpec declaredVersions(String... versions) {
			this.declaredVersions.addAll(List.of(versions));
			return this;
		}

		DependencyUpgradeCandidate create() {

			PackageIdentity pkg = PackageIdentity.of(artifactId, PackageSystem.MAVEN);
			Dependency dependency = new Dependency(pkg, currentVersion);
			dependency.addDeclarationSource(DeclarationSource.dependency());
			dependency.addAllVersionSources(versionSources);

			IconDependencyPresentation presentation = IconDependencyPresentation.from(dependency,
					TestInterfaceAssistant.INSTANCE);
			return DependencyUpgradeCandidate.create(dependency, assistant, releases,
					vulnerabilityRepository(), rule, presentation, declaredVersions(pkg));
		}

		private VulnerabilityRepository vulnerabilityRepository() {

			if (vulnerabilityRepository != null && !vulnerableVersions.isEmpty()) {
				throw new IllegalStateException(
						"Cannot declare vulnerable versions along with providing a vulnerabilityRepository");
			}

			if (vulnerabilityRepository != null) {
				return vulnerabilityRepository;
			}

			if (vulnerableVersions.isEmpty()) {
				return VulnerabilityRepository.empty();
			}

			Map<ArtifactVersion, Vulnerabilities> entries = new HashMap<>(vulnerableVersions);
			entries.putIfAbsent(currentVersion, Vulnerabilities.clean());
			for (Release release : releases) {
				entries.putIfAbsent(release.version(), Vulnerabilities.clean());
			}
			return new MapVulnerabilityRepository(entries);
		}

		private DeclaredVersions declaredVersions(PackageIdentity pkg) {

			if (declaredVersions.isEmpty()) {
				return DeclaredVersions.empty();
			}

			List<DeclarationSite> sites = new ArrayList<>();
			for (String version : declaredVersions) {

				Dependency declared = new Dependency(pkg, ArtifactVersion.of(version));
				sites.add(new DeclarationSite(new MockVirtualFile("declared-" + version + "/pom.xml", "// test"),
						ProjectId.of("com.example", "app"), declared));
			}
			return TestDeclaredVersions.from(sites);
		}

	}

}
