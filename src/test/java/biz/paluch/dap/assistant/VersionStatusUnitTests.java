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

import java.util.List;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.checker.CheckerIcons;
import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.checker.ShieldStyle;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.rule.Generations;
import biz.paluch.dap.support.UpgradeStrategy;
import org.jspecify.annotations.Nullable;
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
	void vulnerableCandidateUsesRequestedShieldStyle() {

		VersionStatus status = status(PATCH, Vulnerabilities.of(List.of(cve(CvssSeverity.HIGH))));

		assertThat(status.isVulnerable()).isTrue();
		assertThat(status.getVersionAge()).isEqualTo(VersionAge.NEWER_PATCH);
		assertThat(status.getIcon(ShieldStyle.FILLED)).isSameAs(CheckerIcons.HIGH);
		assertThat(status.getVulnerabilityTailLabel()).isEqualTo("CVE-1");
	}

	@Test
	void cleanCandidateKeepsVersionAgeIcon() {

		VersionStatus status = status(PATCH, Vulnerabilities.clean());

		assertThat(status.isVulnerable()).isFalse();
		assertThat(status.getVersionAge()).isEqualTo(VersionAge.NEWER_PATCH);
		assertThat(status.getIcon(ShieldStyle.FILLED))
				.isSameAs(DependencyUpgradeIcons.resolve(VersionAge.NEWER_PATCH).getIcon());
		assertThat(status.getVulnerabilityTailLabel()).isNull();
	}

	@Test
	void olderCandidateExposesAgeInformation() {

		VersionStatus status = status(ArtifactVersion.of("5.3.0"), Vulnerabilities.absent());

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
		assertThat(status.getIcon(ShieldStyle.FILLED)).isSameAs(DependencyAssistantIcons.DEPENDENCY_RULE_WARN);
	}

	@Test
	void tailLabelHeadsWithHighestSeverityAdvisory() {

		VersionStatus status = status(PATCH, Vulnerabilities.of(List.of(cve("CVE-LOW", "GHSA-LOW", CvssSeverity.LOW),
				cve("CVE-CRIT", "GHSA-CRIT", CvssSeverity.CRITICAL))));

		assertThat(status.getVulnerabilityTailLabel()).isEqualTo("CVE-CRIT + 1");
	}

	@Test
	void tailLabelSuffixesRemainingAdvisoryCount() {

		VersionStatus status = status(PATCH, Vulnerabilities.of(List.of(cve("CVE-A", "GHSA-A", CvssSeverity.CRITICAL),
				cve("CVE-B", "GHSA-B", CvssSeverity.HIGH), cve("CVE-C", "GHSA-C", CvssSeverity.LOW))));

		assertThat(status.getVulnerabilityTailLabel()).isEqualTo("CVE-A + 2");
	}

	@Test
	void tailLabelFallsBackToGhsaIdWhenNoCveId() {

		VersionStatus status = status(PATCH, Vulnerabilities.of(List.of(cve(null, "GHSA-xyz", CvssSeverity.HIGH))));

		assertThat(status.getVulnerabilityTailLabel()).isEqualTo("GHSA-xyz");
	}

	private static VersionStatus status(ArtifactVersion candidate, Vulnerabilities vulnerabilities) {
		return VersionStatus.of(DependencyRuleEvaluator.absent(), CURRENT, candidate, vulnerabilities);
	}

	private static Vulnerability cve(CvssSeverity severity) {
		return new Vulnerability("ADV-1", "CVE-1", "GHSA-1", "title", 9.0, severity, "https://example.com");
	}

	private static Vulnerability cve(@Nullable String cveId, String ghsaId, CvssSeverity severity) {
		return new Vulnerability("ADV-" + ghsaId, cveId, ghsaId, "title", 9.0, severity, "https://example.com");
	}

	private static DependencyRuleEvaluator rejectingRule() {
		DependencyRule rule = new DependencyRule() {

			@Override
			public boolean test(ArtifactVersion version) {
				return false;
			}

			@Override
			public boolean isPresent() {
				return true;
			}

			@Override
			public boolean isSemanticUpgradingEnabled() {
				return false;
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
				return true;
			}

			@Override
			public Release suggestRemediation(Releases releases) {
				return null;
			}

		};
		return DependencyRuleEvaluator.create(rule, ArtifactId.of("com.example", "demo"), CURRENT,
				TestInterfaceAssistant.INSTANCE);
	}

}
