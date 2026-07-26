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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.Generations;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.upgrade.UpgradeSuggestionsFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link UpgradeSuggestionsFactory}.
 *
 * @author Mark Paluch
 */
class UpgradeSuggestionsFactoryUnitTests {

	private static final ArtifactId ARTIFACT = ArtifactId.of("com.example", "demo");

	private static final PackageIdentity PKG = PackageIdentity.of(ARTIFACT, PackageSystem.MAVEN);

	@Test
	void addsSafeSuggestionFromVulnerabilities() {

		Dependency dependency = dependency("1.0.0");
		Releases releases = TestReleases.from("1.0.0", "1.0.1", "1.0.2", "1.1.0");
		VulnerabilityRepository vulnerabilities = TestVulnerabilities.from("""
				1.0.0 CVE-2026-2
				1.0.1 CVE-2026-2
				1.0.2 CLEAN
				""");

		UpgradeSuggestions suggestions = UpgradeSuggestionsFactory.createSuggestions(dependency, releases,
				vulnerabilities,
				DependencyRule.absent());

		assertThat(suggestions.get(UpgradeStrategy.SAFE).getRelease()).hasVersion("1.0.2");
	}

	@Test
	void omitsSafeSuggestionWhenCurrentVersionIsNotVulnerable() {

		Dependency dependency = dependency("1.0.0");
		Releases releases = TestReleases.from("1.0.0", "1.0.1");
		VulnerabilityRepository vulnerabilities = TestVulnerabilities.from("""
				1.0.0 CLEAN
				1.0.1 CLEAN
				""");

		UpgradeSuggestions suggestions = UpgradeSuggestionsFactory.createSuggestions(dependency, releases,
				vulnerabilities,
				DependencyRule.absent());

		assertThat(suggestions.contains(UpgradeStrategy.SAFE)).isFalse();
	}

	@Test
	void ruleFilteringKeepsSafeSuggestionAndDropsDisabledTiers() {

		Dependency dependency = dependency("1.0.0");
		Releases releases = TestReleases.from("1.0.0", "1.0.1", "1.1.0");
		VulnerabilityRepository vulnerabilities = TestVulnerabilities.from("""
				1.0.0 CVE-2026-2
				1.0.1 CLEAN
				""");

		UpgradeSuggestions suggestions = UpgradeSuggestionsFactory.createSuggestions(dependency, releases,
				vulnerabilities,
				new PatchOnlyRule());

		assertThat(suggestions.contains(UpgradeStrategy.SAFE)).isTrue();
		assertThat(suggestions.contains(UpgradeStrategy.PATCH)).isTrue();
		assertThat(suggestions.contains(UpgradeStrategy.MINOR)).isFalse();
	}

	@Test
	void addsRuleSuggestionWhenCurrentVersionViolatesRule() {

		Dependency dependency = dependency("3.0.0");
		Releases releases = TestReleases.from("3.0.0", "2.0.1", "2.0.0", "1.0.0");

		UpgradeSuggestions suggestions = UpgradeSuggestionsFactory.createSuggestions(dependency, releases,
				VulnerabilityRepository.empty(), new GenerationTwoRule());

		assertThat(suggestions.get(UpgradeStrategy.RULE).getRelease()).hasVersion("2.0.1");
	}

	private static Dependency dependency(String version) {
		return new Dependency(PKG, ArtifactVersion.of(version));
	}

	private static class PatchOnlyRule implements DependencyRule {

		@Override
		public boolean isPresent() {
			return true;
		}

		@Override
		public boolean isSemanticUpgradingEnabled() {
			return true;
		}

		@Override
		public Generations getGenerations() {
			return Generations.unconstrained();
		}

		@Override
		public String getDependencyName() {
			return "";
		}

		@Override
		public boolean isEnabled(UpgradeStrategy upgradeStrategy) {
			return upgradeStrategy == UpgradeStrategy.PATCH;
		}

		@Override
		public boolean test(ArtifactVersion version) {
			return true;
		}

		@Override
		public @Nullable Release suggestRemediation(Releases releases) {
			return null;
		}

	}

	private static class GenerationTwoRule extends PatchOnlyRule {

		@Override
		public boolean test(ArtifactVersion version) {
			return version.toString().startsWith("2.");
		}

	}

}
