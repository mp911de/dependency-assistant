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

package biz.paluch.dap.assistant.review;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.check.DeclarationSite;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.fixtures.TestDependencyRule;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import biz.paluch.dap.state.ProjectId;
import com.intellij.mock.MockVirtualFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

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

		assertThat(review(List.of(clean)).isSafeStrategyAvailable()).isFalse();
	}

	@Test
	void safeStrategyShownWhenAnUnfilteredCandidateIsVulnerable() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults("6.0.1"), "6.0.0", "6.0.1");

		assertThat(review(List.of(vulnerable)).isSafeStrategyAvailable()).isTrue();
	}

	@Test
	void safeStrategyShownEvenWhenVulnerableRowIsFilteredOut() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults(), "6.0.0");

		UpgradeReview review = review(List.of(vulnerable));
		review.setHideUpToDate(true);

		assertThat(review.isSafeStrategyAvailable()).isTrue();
	}

	@Test
	void applyStrategyToAllSafeSelectsVulnerableRowsWithSafeVersion() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults("6.0.1"), "6.0.0", "6.0.1");
		TableRow clean = candidate(SPRING, CURRENT, VulnerabilityRepository.empty(), "6.0.0", "6.0.1");

		UpgradeReview review = review(List.of(vulnerable, clean));
		review.applyStrategyToAll(UpgradeReview.UpgradeStrategies.SAFE);

		assertThat(review.getUpdateTo(vulnerable)).isEqualTo(ArtifactVersion.of("6.0.1"));
		assertThat(review.getUpdateTo(clean)).isEqualTo(CURRENT);
	}

	@Test
	void applyStrategyToAllSafeSkipsVulnerableRowWithoutSafeVersion() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults(), "6.0.0");

		UpgradeReview review = review(List.of(vulnerable));
		review.applyStrategyToAll(UpgradeReview.UpgradeStrategies.SAFE);

		assertThat(review.getUpdateTo(vulnerable)).isEqualTo(CURRENT);
	}

	@Test
	void hideUpToDateKeepsVulnerableRowWithoutRemediationVisible() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults(), "6.0.0");

		UpgradeReview review = review(List.of(vulnerable));
		review.setHideUpToDate(true);

		assertThat(review.getCandidates()).containsExactly(vulnerable);
	}

	@Test
	void hideUpToDateHidesCleanRowWithNoUpgrade() {

		TableRow clean = candidate(LETTUCE, CURRENT, VulnerabilityRepository.empty(), "6.0.0");

		UpgradeReview review = review(List.of(clean));
		review.setHideUpToDate(true);

		assertThat(review.getCandidates()).isEmpty();
	}

	@Test
	void hideUpToDatePinsSafeTargetSoSafeStrategyResolves() {

		TableRow vulnerable = candidate(LETTUCE, CURRENT, scanResults("6.0.1"), "6.0.0", "6.0.1");

		UpgradeReview review = review(List.of(vulnerable));
		review.setHideUpToDate(true);

		assertThat(review.getCandidates()).contains(vulnerable);
		assertThat(review.getReleases(vulnerable).getRelease(ArtifactVersion.of("6.0.1"))).isNotNull();
		review.applyStrategyToAll(UpgradeReview.UpgradeStrategies.SAFE);
		assertThat(review.getUpdateTo(vulnerable)).isEqualTo(ArtifactVersion.of("6.0.1"));
	}

	private static UpgradeReview review(List<TableRow> candidates) {
		return new UpgradeReview(candidates, List.of());
	}

	/**
	 * Mark the current version vulnerable and each given newer version clean, so a
	 * Safe Version resolves to the lowest clean newer release.
	 */
	private static VulnerabilityRepository scanResults(String... cleanNewer) {

		Map<ArtifactVersion, Vulnerabilities> vulnerabilities = new HashMap<>();
		vulnerabilities.put(CURRENT, TestVulnerabilities.CRITICAL);
		for (String version : cleanNewer) {
			vulnerabilities.put(ArtifactVersion.of(version), Vulnerabilities.clean());
		}
		return VulnerabilityRepository.of(vulnerabilities);
	}

	private static TableRow candidate(ArtifactId artifactId, ArtifactVersion current,
			VulnerabilityRepository vulnerabilities, String... versions) {

		Dependency dependency = new Dependency(artifactId, current);
		dependency.addVersionSource(VersionSource.declared(current.toString()));
		Releases releases = TestReleases.from(versions);
		return new TableRow(DependencyUpgradeCandidate.create(dependency, releases, vulnerabilities,
				new TestDependencyRule(artifactId.artifactId()), new TestInterfaceAssistant(),
				DeclaredVersions.from(List.of(site(artifactId, current)), it -> null, null)));
	}

	private static DeclarationSite site(ArtifactId artifactId, ArtifactVersion version) {
		return new DeclarationSite(new MockVirtualFile("review/pom.xml", "// test"), ProjectId.of("com.acme", "app"),
				new Dependency(artifactId, version));
	}

}
