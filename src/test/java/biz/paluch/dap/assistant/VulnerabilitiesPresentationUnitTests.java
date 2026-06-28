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

package biz.paluch.dap.assistant;

import java.util.List;

import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.severity.DependencyAssistantSeverities;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VulnerabilitiesPresentation}.
 *
 * @author Mark Paluch
 */
class VulnerabilitiesPresentationUnitTests {

	@Test
	void rejectsCleanVulnerabilities() {
		assertThatIllegalArgumentException().isThrownBy(() -> VulnerabilitiesPresentation.of(Vulnerabilities.clean()));
	}

	@Test
	void rejectsUnknownVulnerabilities() {
		assertThatIllegalArgumentException().isThrownBy(() -> VulnerabilitiesPresentation.of(Vulnerabilities.absent()));
	}

	@Test
	void vulnerableDependencyTextUsesSeverityLabel() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(List.of(cve(CvssSeverity.CRITICAL)));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getText())
				.isEqualTo("Vulnerable: Critical (1 advisory)");
	}

	@Test
	void vulnerableSuggestionDetailUsesSeverityLabel() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(List.of(cve(CvssSeverity.HIGH)));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getDetail())
				.isEqualTo("High: CVE-2026-1");
	}

	@Test
	void exposesSeverityTextAttributes() {

		assertThat(VulnerabilitiesPresentation.of(Vulnerabilities.of(List.of(cve(CvssSeverity.LOW))))
				.getTextAttributes()).isSameAs(DependencyAssistantSeverities.VULNERABLE_LOW_KEY);
		assertThat(VulnerabilitiesPresentation.of(Vulnerabilities.of(List.of(cve(CvssSeverity.MEDIUM))))
				.getTextAttributes()).isSameAs(DependencyAssistantSeverities.VULNERABLE_MEDIUM_KEY);
		assertThat(VulnerabilitiesPresentation.of(Vulnerabilities.of(List.of(cve(CvssSeverity.HIGH))))
				.getTextAttributes()).isSameAs(DependencyAssistantSeverities.VULNERABLE_HIGH_KEY);
		assertThat(VulnerabilitiesPresentation.of(Vulnerabilities.of(List.of(cve(CvssSeverity.CRITICAL))))
				.getTextAttributes()).isSameAs(DependencyAssistantSeverities.VULNERABLE_CRITICAL_KEY);
	}

	@Test
	void unratedSeverityUsesWarningTextAttributes() {

		Vulnerabilities vulnerabilities = Vulnerabilities.of(List.of(cve(CvssSeverity.UNKNOWN)));

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getTextAttributes())
				.isSameAs(HighlightInfoType.WEAK_WARNING.getAttributesKey());
	}

	private static Vulnerability cve(CvssSeverity severity) {
		return new Vulnerability("GHSA-1", "CVE-2026-1", "GHSA-1", "Boom", 7.5, severity, "https://example.com");
	}

}
