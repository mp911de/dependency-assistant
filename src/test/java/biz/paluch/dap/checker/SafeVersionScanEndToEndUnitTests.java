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

package biz.paluch.dap.checker;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.TestCache;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.state.VulnerabilityScannerPolicy;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.upgrade.UpgradeSuggestionsFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end tests.
 *
 * @author Mark Paluch
 */
class SafeVersionScanEndToEndUnitTests {

	Vulnerability CVE = TestVulnerabilities.CRITICAL_VULNERABILITY;

	ArtifactId ARTIFACT = ArtifactId.of("io.example", "core");

	Instant NOW = Instant.parse("2026-06-22T00:00:00Z");

	TestCache cache = new TestCache(Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void scanResultsCanProduceSafeVersion() {

		Releases releases = TestReleases.from("5.0.10", "5.0.11", "5.0.12", "5.1.0", "5.1.1", "6.0.0");
		ArtifactVersion current = ArtifactVersion.of("5.0.10");

		scanCandidates(releases, current, vulnerableVersions("5.0.10", "5.0.11", "5.0.12", "5.1.0", "5.1.1"));

		UpgradeSuggestions suggestions = UpgradeSuggestionsFactory.createSuggestions(
				new Dependency(ARTIFACT, current), releases,
				VulnerabilityRepository.of(cache.getVulnerabilities(ARTIFACT)),
				DependencyRule.absent());

		assertThat(suggestions.contains(UpgradeStrategy.SAFE)).isTrue();
		assertThat(suggestions.get(UpgradeStrategy.SAFE).getVersion()).isEqualTo(ArtifactVersion.of("6.0.0"));
	}

	private void scanCandidates(Releases releases, ArtifactVersion current, Predicate<String> vulnerableVersions) {

		cache.putVersionOptions(ARTIFACT, releases);
		VulnerabilityScannerPolicy policy = new VulnerabilityScannerPolicy(cache.getClock());
		for (Release candidate : policy.sweepVersions(List.of(current), releases.stream().toList(), version -> true)) {

			List<Vulnerability> found = new ArrayList<>();
			if (vulnerableVersions.test(candidate.version().toString())) {
				found.add(CVE);
			}
			cache.addVulnerabilities(ARTIFACT, candidate.version(), found);
		}
	}

	private static Predicate<String> vulnerableVersions(String... versions) {
		Set<String> vulnerableVersions = Set.of(versions);
		return vulnerableVersions::contains;
	}

}
