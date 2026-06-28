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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.upgrade.UpgradeSuggestion;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradeSuggestions} tier selection and ordering.
 *
 * @author Mark Paluch
 */
class UpgradeSuggestionsUnitTests {

	@Test
	void detectsPatchUpgrade() {

		UpgradeSuggestion best = from("1.0.0", "1.0.1", "1.0.0").getSuggestion();

		assertThat(best.getStrategy()).isEqualTo(UpgradeStrategy.PATCH);
		assertThat(best.getRelease().version()).isEqualTo(version("1.0.1"));
	}

	@Test
	void detectsMinorUpgrade() {

		UpgradeSuggestion best = from("1.0.0", "1.1.0", "1.0.0").getSuggestion();

		assertThat(best.getStrategy()).isEqualTo(UpgradeStrategy.MINOR);
		assertThat(best.getRelease().version()).isEqualTo(version("1.1.0"));
	}

	@Test
	void detectsMajorUpgrade() {

		UpgradeSuggestion best = from("1.0.0", "2.0.0", "1.0.0").getSuggestion();

		assertThat(best.getStrategy()).isEqualTo(UpgradeStrategy.MAJOR);
		assertThat(best.getRelease().version()).isEqualTo(version("2.0.0"));
	}

	@Test
	void ordersTiersPatchMinorMajorLatest() {

		UpgradeSuggestions suggestions = from("1.0.0", "3.0.0", "1.5.0", "1.0.5", "1.0.0");

		assertThat(suggestions.getSuggestions()).extracting(UpgradeSuggestion::getStrategy).containsExactly(
				UpgradeStrategy.PATCH, UpgradeStrategy.MINOR, UpgradeStrategy.MAJOR, UpgradeStrategy.LATEST);
	}

	@Test
	void staysWithinDeclaredVersioningScheme() {

		UpgradeSuggestions suggestions = from("Aluminium-RELEASE", "2025.0.6", "2020.0.0", "Dysprosium-SR25",
				"Aluminium-RELEASE");

		assertThat(suggestions.getSuggestions()).extracting(it -> it.getRelease().version().toString())
				.containsOnly("Dysprosium-SR25");
	}

	@Test
	void emptyWhenNoNewerRelease() {
		assertThat(from("2.0.0", "1.5.0", "1.0.0").isEmpty()).isTrue();
	}

	private static UpgradeSuggestions from(String current, String... versions) {
		return UpgradeSuggestions.from(version(current), TestReleases.from(versions));
	}

	private static ArtifactVersion version(String version) {
		return ArtifactVersion.of(version);
	}

}
