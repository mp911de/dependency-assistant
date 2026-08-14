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

package biz.paluch.dap.assistant.review;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.fixtures.TestCandidates;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the strategy-option logic of {@link DependencyCheckDialog}.
 *
 * @author Mark Paluch
 */
class DependencyCheckDialogUnitTests {

	private static final ArtifactId SPRING_CORE = ArtifactId.of("org.springframework", "spring-core");

	@Test
	void strategyOptionsOmitSafeWhenNoCandidateIsVulnerable() {

		UpgradeReview review = new UpgradeReview(candidate(SPRING_CORE));

		assertThat(DependencyCheckDialog.DependencyCheckComponents.strategyOptions(review)).containsExactly(
				UpgradeReview.StrategySelection.MANUAL, UpgradeReview.StrategySelection.BUGFIX,
				UpgradeReview.StrategySelection.MINOR, UpgradeReview.StrategySelection.LATEST);
	}

	@Test
	void strategyOptionsOfferSafeWhenACandidateIsVulnerable() {

		TableRow vulnerable = new TableRow(TestCandidates.candidate(SPRING_CORE, ArtifactVersion.of("6.0.0"),
				it -> it.releases("6.0.0", "6.0.1")
						.vulnerabilities(TestVulnerabilities.from("6.0.0 CVE-2026-1\n6.0.1 CLEAN\n"))));

		UpgradeReview review = new UpgradeReview(vulnerable);

		assertThat(DependencyCheckDialog.DependencyCheckComponents.strategyOptions(review))
				.contains(UpgradeReview.StrategySelection.SAFE);
	}

	private static TableRow candidate(ArtifactId artifactId) {
		return new TableRow(TestCandidates.candidate(artifactId, "6.2.0", it -> it.releases("6.2.0", "6.2.1")));
	}

}
