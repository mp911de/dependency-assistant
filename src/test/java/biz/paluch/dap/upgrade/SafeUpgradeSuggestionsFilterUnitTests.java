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

package biz.paluch.dap.upgrade;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.UpgradeStrategy;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link SafeUpgradeSuggestionsFilter}.
 *
 * @author Mark Paluch
 */
class SafeUpgradeSuggestionsFilterUnitTests {

	private static final ArtifactId ARTIFACT = ArtifactId.of("io.example", "core");

	private final SafeUpgradeSuggestionsFilter filter = new SafeUpgradeSuggestionsFilter();

	@Test
	void picksLowestCleanNewerRelease() {

		Releases releases = TestReleases.from("5.0.10", "5.0.11", "5.0.12", "5.1.0");

		UpgradeSuggestions filtered = safeVersions(releases, "5.0.10", TestVulnerabilities.from("""
				5.0.10 CVE-2026-1
				5.0.11 CVE-2026-1
				5.0.12 CLEAN
				5.1.0 CLEAN
				"""),
				UpgradeSuggestions.from(ArtifactVersion.of("5.0.10"), releases));

		assertThat(filtered.get(UpgradeStrategy.SAFE).getVersion()).isEqualTo("5.0.12");
		assertThat(filtered.get(UpgradeStrategy.MINOR).getVersion()).isEqualTo("5.1.0");
		assertThat(filtered.getSuggestions()).extracting(UpgradeSuggestion::getStrategy)
				.startsWith(UpgradeStrategy.SAFE);
	}

	@Test
	void picksCleanReleaseAcrossAMajorLine() {

		Releases releases = TestReleases.from("5.0.10", "5.0.11", "6.0.0");

		UpgradeSuggestion safe = safeVersion(releases, "5.0.10", TestVulnerabilities.from("""
				5.0.10 CVE-2026-1
				5.0.11 CVE-2026-1
				6.0.0 CLEAN
				"""));

		assertThat(safe.getVersion()).isEqualTo("6.0.0");
	}

	@Test
	void absentWhenNoNewerReleaseIsClean() {

		Releases releases = TestReleases.from("5.0.10", "5.0.11", "6.0.0");

		UpgradeSuggestion safe = safeVersion(releases, "5.0.10", TestVulnerabilities.from("""
				5.0.10 CVE-2026-1
				5.0.11 CVE-2026-1
				6.0.0 CVE-2026-1
				"""));

		assertThat(safe.isPresent()).isFalse();
	}

	@Test
	void absentWhenNewerReleaseHasNoVulnerabilityScan() {

		Releases releases = TestReleases.from("5.0.10", "5.0.11");

		UpgradeSuggestion safe = safeVersion(releases, "5.0.10", TestVulnerabilities.from("""
				5.0.10 CVE-2026-1
				5.0.11 ABSENT
				"""));

		assertThat(safe.isPresent()).isFalse();
	}

	@Test
	void absentWhenCurrentIsNotVulnerable() {

		Releases releases = TestReleases.from("5.0.10", "5.0.11");

		UpgradeSuggestion safe = safeVersion(releases, "5.0.10", TestVulnerabilities.from("""
				5.0.10 CLEAN
				5.0.11 CVE-2026-1
				"""));

		assertThat(safe.isPresent()).isFalse();
	}

	@Test
	void ignoresOlderAndEqualReleases() {

		Releases releases = TestReleases.from("5.0.8", "5.0.9", "5.0.10");

		UpgradeSuggestion safe = safeVersion(releases, "5.0.10", TestVulnerabilities.from("""
				5.0.8 CLEAN
				5.0.9 CLEAN
				5.0.10 CVE-2026-1
				"""));

		assertThat(safe.isPresent()).isFalse();
	}

	private UpgradeSuggestion safeVersion(Releases releases, String current, VulnerabilityRepository vulnerabilities) {
		return safeVersions(releases, current, vulnerabilities, UpgradeSuggestions.empty()).get(UpgradeStrategy.SAFE);
	}

	private UpgradeSuggestions safeVersions(Releases releases, String current, VulnerabilityRepository vulnerabilities,
			UpgradeSuggestions suggestions) {

		Dependency dependency = new Dependency(ARTIFACT, ArtifactVersion.of(current));
		return filter.filter(dependency, releases.withVersion(dependency.getCurrentVersion()), vulnerabilities,
				DependencyRule.absent(), suggestions);
	}

}
