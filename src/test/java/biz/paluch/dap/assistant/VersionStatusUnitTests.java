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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.fixtures.TestDependencyRule;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VersionStatus}.
 *
 * @author Mark Paluch
 */
class VersionStatusUnitTests {

	private static final ArtifactVersion CURRENT = ArtifactVersion.of("6.0.0");

	private static final ArtifactVersion PATCH = ArtifactVersion.of("6.0.1");

	@Test
	void cleanCandidateKeepsVersionAgeIcon() {

		VersionStatus status = status(PATCH, Vulnerabilities.clean());

		assertThat(status.isVulnerable()).isFalse();
		assertThat(status.getVersionAge()).isEqualTo(VersionAge.NEWER_PATCH);
		assertThat(status.getVulnerabilityTailLabel()).isNull();
	}

	@Test
	void olderCandidateExposesAgeInformation() {

		VersionStatus status = status("5.3.0", Vulnerabilities.absent());

		assertThat(status.isOlder()).isTrue();
		assertThat(status.getVersionAge()).isEqualTo(VersionAge.OLDER);
	}

	@Test
	void absentCurrentVersionExposesUnknownAgeInformation() {

		VersionStatus status = VersionStatus.of(DependencyRuleEvaluator.absent(), null, PATCH,
				Vulnerabilities.absent());

		assertThat(status.isOlder()).isFalse();
		assertThat(status.getVersionAge()).isEqualTo(VersionAge.SAME_OR_UNKNOWN);
	}

	@Test
	void ruleViolationControlsIconWithoutErasingVersionAge() {

		VersionStatus status = VersionStatus.of(rejectingRule(), CURRENT, PATCH, Vulnerabilities.absent());

		assertThat(status.isRuleViolation()).isTrue();
		assertThat(status.getVersionAge()).isEqualTo(VersionAge.NEWER_PATCH);
	}

	@Test
	void tailLabelHeadsWithHighestSeverityAdvisory() {

		Vulnerabilities LOW_AND_CRITICAL = Vulnerabilities.of(TestVulnerabilities.LOW_VULNERABILITY,
				TestVulnerabilities.CRITICAL_VULNERABILITY);
		VersionStatus status = status(PATCH, LOW_AND_CRITICAL);

		assertThat(status.getVulnerabilityTailLabel()).isEqualTo("CVE-2026-1 + 1");
	}

	@Test
	void tailLabelSuffixesRemainingAdvisoryCount() {

		VersionStatus status = status(PATCH, TestVulnerabilities.CRITICAL_HIGH_LOW);

		assertThat(status.getVulnerabilityTailLabel()).isEqualTo("CVE-2026-1 + 2");
	}

	@Test
	void tailLabelFallsBackToGhsaIdWhenNoCveId() {

		VersionStatus status = status(PATCH,
				Vulnerabilities.of(TestVulnerabilities.create("ADV-GHSA-xyz", null, "GHSA-xyz",
						CvssSeverity.HIGH)));

		assertThat(status.getVulnerabilityTailLabel()).isEqualTo("GHSA-xyz");
	}

	private static VersionStatus status(String candidate, Vulnerabilities vulnerabilities) {
		return status(ArtifactVersion.of(candidate), vulnerabilities);
	}

	private static VersionStatus status(ArtifactVersion candidate, Vulnerabilities vulnerabilities) {
		return VersionStatus.of(DependencyRuleEvaluator.absent(), CURRENT, candidate, vulnerabilities);
	}

	private static DependencyRuleEvaluator rejectingRule() {
		return DependencyRuleEvaluator.create(TestDependencyRule.rejecting(),
				CURRENT);
	}

}
