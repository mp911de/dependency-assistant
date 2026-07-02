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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRules;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.UpgradeStrategy;
import com.intellij.mock.MockVirtualFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradeCandidate}.
 *
 * @author Mark Paluch
 */
class UpgradeCandidateUnitTests {

	@Test
	void ruleTargetIsNewestReleasePassingAnyGeneration() {

		DependencyRule rule = DependencyRules.builder()
				.artifact("com.example:*", "2.0", "1.0")
				.build()
				.resolve(ArtifactId.of("com.example", "demo"), null, null);

		UpgradeCandidate candidate = candidate("0.9.0", rule,
				Release.of("0.9.0"), Release.of("1.0.5"), Release.of("2.0.1"), Release.of("3.0.0"));

		assertThat(candidate.getUpdateCandidate().getTargets().get(UpgradeStrategy.RULE).getRelease())
				.isEqualTo(Release.of("2.0.1"));
	}

	@Test
	void derivesQueryFromPropertyBackedCandidate() {

		Dependency dependency = new Dependency(ArtifactId.of("org.springframework", "spring-core"),
				ArtifactVersion.of("6.1.0"));
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.property("springVersion"));
		UpgradeCandidate candidate = new UpgradeCandidate(
				new DependencyUpdateCandidate(dependency, Releases.of(Release.of("6.1.0")),
						VulnerabilityRepository.empty()),
				new TestInterfaceAssistant(), DeclaredVersions.empty());

		DependencySiteQuery query = candidate.toQuery();

		assertThat(query.artifacts()).containsExactly(ArtifactId.of("org.springframework", "spring-core"));
		assertThat(query.versionProperties()).containsExactly("springVersion");
	}

	@Test
	void groupQueryUnionsMemberArtifacts() {

		ArtifactId core = ArtifactId.of("org.springframework", "spring-core");
		ArtifactId web = ArtifactId.of("org.springframework", "spring-web");
		UpgradeGroup group = UpgradeGroup.of(member(core), member(web));

		DependencySiteQuery query = group.toQuery();

		assertThat(query.artifacts()).containsExactlyInAnyOrder(core, web);
		assertThat(query.versionProperties()).containsExactly("spring.version");
	}

	private static UpgradeCandidate member(ArtifactId artifactId) {

		ArtifactVersion version = ArtifactVersion.of("6.1.0");
		Dependency dependency = new Dependency(artifactId, version);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.property("spring.version"));
		DeclarationSite site = new DeclarationSite(new MockVirtualFile("pom.xml", "x"), ProjectId.of("com.acme", "app"),
				new Dependency(artifactId, version));
		return new UpgradeCandidate(
				new DependencyUpdateCandidate(dependency, Releases.of(Release.of("6.1.0")),
						VulnerabilityRepository.empty()),
				new TestInterfaceAssistant(), DeclaredVersions.from(List.of(site), it -> null, null));
	}

	private static UpgradeCandidate candidate(String currentVersion, DependencyRule rule, Release... releases) {

		Dependency dependency = new Dependency(ArtifactId.of("com.example", "demo"),
				ArtifactVersion.of(currentVersion));
		dependency.addDeclarationSource(DeclarationSource.dependency());
		DependencyUpdateCandidate option = new DependencyUpdateCandidate(dependency, Releases.of(releases),
				VulnerabilityRepository.empty(),
				rule);
		return new UpgradeCandidate(option, new TestInterfaceAssistant(), DeclaredVersions.empty());
	}

}
