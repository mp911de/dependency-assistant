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

import java.util.List;

import biz.paluch.dap.fixtures.TestVulnerabilities;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Vulnerabilities}.
 *
 * @author Mark Paluch
 */
class VulnerabilitiesUnitTests {

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

		Vulnerabilities vulnerable = Vulnerabilities.of(List.of(TestVulnerabilities.CRITICAL_VULNERABILITY));

		assertThat(vulnerable.isUnknown()).isFalse();
		assertThat(vulnerable.isClean()).isFalse();
		assertThat(vulnerable.isVulnerable()).isTrue();
		assertThat(vulnerable).containsExactly(TestVulnerabilities.CRITICAL_VULNERABILITY);
	}

	@Test
	void ofEmptyListIsClean() {
		assertThat(Vulnerabilities.of(List.of()).isClean()).isTrue();
	}

	@Test
	void highestSeverityPicksMostSevereAdvisory() {

		Vulnerabilities vulnerable = TestVulnerabilities.CRITICAL_HIGH_LOW;

		assertThat(vulnerable.getHighestSeverity()).isEqualTo(CvssSeverity.CRITICAL);
	}

	@Test
	void highestSeverityFallsBackToUnknownForUnratedAdvisory() {

		Vulnerabilities vulnerable = TestVulnerabilities.UNKNOWN;

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

}
