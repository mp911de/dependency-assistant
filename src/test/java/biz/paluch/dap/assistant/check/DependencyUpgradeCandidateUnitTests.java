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

import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.UpgradeStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyUpgradeCandidate}.
 *
 * @author Mark Paluch
 */
class DependencyUpgradeCandidateUnitTests {

	@Test
	void keepsRemediationTargetInDisplayReleaseUniverse() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		ArtifactVersion remediation = ArtifactVersion.of("1.1.0-RC1");
		Dependency dependency = new Dependency(ArtifactId.of("com.example", "demo"), current);
		Releases releases = TestReleases.from("1.1.0-RC1");
		VulnerabilityRepository vulnerabilities = VulnerabilityRepository.of(Map.of(current,
				TestVulnerabilities.HIGH, remediation, Vulnerabilities.clean()));

		DependencyUpgradeCandidate upgrade = upgrade(dependency, releases, vulnerabilities);

		assertThat(upgrade.getReleases()).contains(Release.of("1.0.0"));
		assertThat(upgrade.getDisplayReleases()).contains(Release.of("1.1.0-RC1"));
		assertThat(upgrade.resolveDisplayTarget(UpgradeStrategy.SAFE)).isEqualTo(Release.of("1.1.0-RC1"));
	}

	@Test
	void omitsTargetsOutsideDisplayReleaseUniverse() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		Dependency dependency = new Dependency(ArtifactId.of("com.example", "demo"), current);

		DependencyUpgradeCandidate upgrade = upgrade(dependency, TestReleases.from("1.1.0-RC1"),
				VulnerabilityRepository.empty());

		assertThat(upgrade.getSuggestions().get(UpgradeStrategy.PREVIEW).isPresent()).isTrue();
		assertThat(upgrade.getDisplayReleases()).doesNotContain(Release.of("1.1.0-RC1"));
		assertThat(upgrade.getDisplaySuggestions().get(UpgradeStrategy.PREVIEW).isPresent()).isFalse();
		assertThat(upgrade.resolveDisplayTarget(UpgradeStrategy.PREVIEW)).isNull();
	}

	@Test
	void selectsOnlyTargetsFromItsReleaseUniverse() {

		Dependency dependency = new Dependency(ArtifactId.of("com.example", "demo"), ArtifactVersion.of("1.0.0"));
		DependencyUpgradeCandidate upgrade = upgrade(dependency, TestReleases.from("1.1.0"),
				VulnerabilityRepository.empty());

		assertThat(upgrade.selectTarget(ArtifactVersion.of("1.1.0"))).isEqualTo(Release.of("1.1.0"));
		assertThatThrownBy(() -> upgrade.selectTarget(ArtifactVersion.of("2.0.0")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("2.0.0");
	}

	private static DependencyUpgradeCandidate upgrade(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities) {
		return DependencyUpgradeCandidate.create(dependency, releases, vulnerabilities, DependencyRule.absent(),
				TestInterfaceAssistant.INSTANCE, DeclaredVersions.empty());
	}

}
