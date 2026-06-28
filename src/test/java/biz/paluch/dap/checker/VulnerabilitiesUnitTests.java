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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Vulnerabilities}.
 *
 * @author Mark Paluch
 */
class VulnerabilitiesUnitTests {

	private static final Vulnerability CVE = new Vulnerability("GHSA-xxxx", "CVE-2026-1", "GHSA-xxxx",
			"Remote code execution",
			9.8, CvssSeverity.CRITICAL, "https://example.com/advisory");

	@Test
	void absentIsNeitherCleanNorVulnerable() {

		Vulnerabilities absent = Vulnerabilities.absent();

		assertThat(absent.isUnknown()).isTrue();
		assertThat(absent.isClean()).isFalse();
		assertThat(absent.isVulnerable()).isFalse();
	}

	@Test
	void cleanReportsCleanWithEmptyList() {

		Vulnerabilities clean = Vulnerabilities.clean();

		assertThat(clean.isUnknown()).isFalse();
		assertThat(clean.isClean()).isTrue();
		assertThat(clean.isVulnerable()).isFalse();
		assertThat(clean).isEmpty();
	}

	@Test
	void vulnerableExposesTheVulnerabilities() {

		Vulnerabilities vulnerable = Vulnerabilities.of(List.of(CVE));

		assertThat(vulnerable.isUnknown()).isFalse();
		assertThat(vulnerable.isClean()).isFalse();
		assertThat(vulnerable.isVulnerable()).isTrue();
		assertThat(vulnerable).containsExactly(CVE);
	}

	@Test
	void ofEmptyListIsClean() {
		assertThat(Vulnerabilities.of(List.of()).isClean()).isTrue();
	}

	@Test
	void highestSeverityPicksMostSevereAdvisory() {

		Vulnerabilities vulnerable = Vulnerabilities.of(List.of(cve(CvssSeverity.LOW), cve(CvssSeverity.HIGH),
				cve(CvssSeverity.MEDIUM)));

		assertThat(vulnerable.getHighestSeverity()).isEqualTo(CvssSeverity.HIGH);
	}

	@Test
	void highestSeverityRanksCriticalAboveHigh() {

		Vulnerabilities vulnerable = Vulnerabilities.of(List.of(cve(CvssSeverity.HIGH), cve(CvssSeverity.CRITICAL)));

		assertThat(vulnerable.getHighestSeverity()).isEqualTo(CvssSeverity.CRITICAL);
	}

	@Test
	void highestSeverityFallsBackToUnknownForUnratedAdvisory() {

		Vulnerabilities vulnerable = Vulnerabilities.of(List.of(cve(CvssSeverity.UNKNOWN)));

		assertThat(vulnerable.getHighestSeverity()).isEqualTo(CvssSeverity.UNKNOWN);
	}

	@Test
	void highestSeverityThrowsWhenClean() {
		assertThatIllegalStateException().isThrownBy(() -> Vulnerabilities.clean().getHighestSeverity());
	}

	@Test
	void highestSeverityThrowsWhenAbsent() {
		assertThatIllegalStateException().isThrownBy(() -> Vulnerabilities.absent().getHighestSeverity());
	}

	private static Vulnerability cve(CvssSeverity severity) {
		return new Vulnerability("GHSA-" + severity, "CVE-2026-1", "GHSA-" + severity, "Boom", 5.0, severity,
				"https://example.com");
	}

}
