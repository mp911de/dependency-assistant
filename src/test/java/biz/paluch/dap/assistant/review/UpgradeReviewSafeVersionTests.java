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
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.fixtures.TestCandidates;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for the Safe Version strategy entry and vulnerable-row visibility
 * of {@link UpgradeReview}.
 *
 * <p>Vulnerability state and the Safe Version are read from the row model
 * ({@link DependencyUpgradeCandidate}); the review consults no vulnerabilities
 * on its own.
 *
 * @author Mark Paluch
 */
class UpgradeReviewSafeVersionTests {

	private static final ArtifactId LETTUCE = ArtifactId.of("io.lettuce", "lettuce-core");

	private static final ArtifactId SPRING = ArtifactId.of("org.springframework", "spring-core");

	private static final ArtifactVersion CURRENT = ArtifactVersion.of("6.0.0");

	@Test
	void safeStrategyHiddenWhenNoCandidateIsVulnerable() {

		TableRow clean = candidate(LETTUCE, CURRENT, VulnerabilityRepository.empty(), "6.0.0", "6.0.1");

		assertThat(new UpgradeReview(clean).isSafeStrategyAvailable()).isFalse();
	}

	@Test
	void safeStrategyShownWhenAnUnfilteredCandidateIsVulnerable() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults("6.0.1"), "6.0.0", "6.0.1");

		assertThat(new UpgradeReview(vulnerable).isSafeStrategyAvailable()).isTrue();
	}

	@Test
	void safeStrategyShownEvenWhenVulnerableRowIsFilteredOut() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults(), "6.0.0");

		UpgradeReview review = new UpgradeReview(vulnerable);
		review.setHideUpToDate(true);

		assertThat(review.isSafeStrategyAvailable()).isTrue();
	}

	@Test
	void applyStrategyToAllSafeSelectsVulnerableRowsWithSafeVersion() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults("6.0.1"), "6.0.0", "6.0.1");
		TableRow clean = candidate(SPRING, CURRENT, VulnerabilityRepository.empty(), "6.0.0", "6.0.1");

		UpgradeReview review = new UpgradeReview(vulnerable, clean);
		review.applyStrategyToAll(UpgradeReview.StrategySelection.SAFE);

		assertThat(review.getUpdateTo(vulnerable)).isEqualTo("6.0.1");
		assertThat(review.getUpdateTo(clean)).isEqualTo(CURRENT);
	}

	@Test
	void applyStrategyToAllSafeSkipsVulnerableRowWithoutSafeVersion() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults(), "6.0.0");

		UpgradeReview review = new UpgradeReview(vulnerable);
		review.applyStrategyToAll(UpgradeReview.StrategySelection.SAFE);

		assertThat(review.getUpdateTo(vulnerable)).isEqualTo(CURRENT);
	}

	@Test
	void hideUpToDateKeepsVulnerableRowWithoutRemediationVisible() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults(), "6.0.0");

		UpgradeReview review = new UpgradeReview(vulnerable);
		review.setHideUpToDate(true);

		assertThat(review.getCandidates()).containsExactly(vulnerable);
	}

	@Test
	void hideUpToDateHidesCleanRowWithNoUpgrade() {

		TableRow clean = candidate(LETTUCE, CURRENT, VulnerabilityRepository.empty(), "6.0.0");

		UpgradeReview review = new UpgradeReview(clean);
		review.setHideUpToDate(true);

		assertThat(review.getCandidates()).isEmpty();
	}

	@Test
	void hideUpToDatePinsSafeTargetSoSafeStrategyResolves() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults("6.0.1"), "6.0.0", "6.0.1");

		UpgradeReview review = new UpgradeReview(vulnerable);
		review.setHideUpToDate(true);

		assertThat(review.getCandidates()).contains(vulnerable);
		assertThat(review.getReleases(vulnerable).getRelease(ArtifactVersion.of("6.0.1"))).isNotNull();
		review.applyStrategyToAll(UpgradeReview.StrategySelection.SAFE);
		assertThat(review.getUpdateTo(vulnerable)).isEqualTo("6.0.1");
	}

	/**
	 * Mark the current version vulnerable and each given newer version clean, so a
	 * Safe Version resolves to the lowest clean newer release.
	 */
	private static VulnerabilityRepository scanResults(String... cleanNewer) {

		StringBuilder scanReport = new StringBuilder(CURRENT + " CVE-2026-1\n");
		for (String version : cleanNewer) {
			scanReport.append(version).append(" CLEAN\n");
		}
		return TestVulnerabilities.from(scanReport.toString());
	}

	private static TableRow candidate(ArtifactId artifactId, ArtifactVersion current,
			VulnerabilityRepository vulnerabilities, String... versions) {
		return new SingleTableRow(TestCandidates.candidate(artifactId, current, it -> it.releases(versions)
				.vulnerabilities(vulnerabilities).rule(artifactId.artifactId()).declaredVersions(current.toString())));
	}

}
