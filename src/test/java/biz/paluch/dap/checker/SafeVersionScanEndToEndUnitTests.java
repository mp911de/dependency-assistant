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

package biz.paluch.dap.checker;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.upgrade.UpgradeSuggestionsFactory;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * End-to-end tests.
 *
 * @author Mark Paluch
 */
class SafeVersionScanEndToEndUnitTests {

	ArtifactId ARTIFACT = ArtifactId.of("io.example", "core");

	@Test
	void scanResultsCanProduceSafeVersion() {

		Releases releases = TestReleases.from("5.0.10", "5.0.11", "5.0.12", "5.1.0", "5.1.1", "6.0.0");
		ArtifactVersion current = ArtifactVersion.of("5.0.10");

		UpgradeSuggestions suggestions = UpgradeSuggestionsFactory.createSuggestions(
				new Dependency(PackageIdentity.of(ARTIFACT, PackageSystem.MAVEN), current), releases,
				TestVulnerabilities.from("""
						5.0.10 CVE-2026-1
						5.0.11 CVE-2026-1
						5.0.12 CVE-2026-1
						5.1.0 CVE-2026-1
						5.1.1 CVE-2026-1
						6.0.0 CLEAN
						"""),
				DependencyRule.absent());

		assertThat(suggestions.contains(UpgradeStrategy.SAFE)).isTrue();
		assertThat(suggestions.get(UpgradeStrategy.SAFE).getVersion()).isEqualTo("6.0.0");
	}

}
