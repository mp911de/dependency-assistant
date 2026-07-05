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

package biz.paluch.dap.assistant.editor;

import java.util.stream.Stream;

import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import biz.paluch.dap.severity.DependencyAssistantSeverities;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static biz.paluch.dap.fixtures.TestVulnerabilities.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.*;

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

		Vulnerabilities vulnerabilities = TestVulnerabilities.CRITICAL;

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getText())
				.isEqualTo("Vulnerable: Critical (1 advisory)");
	}

	@ParameterizedTest
	@MethodSource
	void detailSummarizesRatedVulnerabilities(Vulnerabilities vulnerabilities, String expectedDetail) {
		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getDetail()).isEqualTo(expectedDetail);
	}

	static Stream<Arguments> detailSummarizesRatedVulnerabilities() {

		Vulnerabilities TWO_CRITICAL = Vulnerabilities.of(TestVulnerabilities.CRITICAL_VULNERABILITY,
				TestVulnerabilities.SECOND_CRITICAL_VULNERABILITY);

		Vulnerabilities CRITICAL_WITH_UNRATED = Vulnerabilities.of(TestVulnerabilities.CRITICAL_VULNERABILITY,
				TestVulnerabilities.UNKNOWN_VULNERABILITY, NONE_VULNERABILITY);

		Vulnerabilities UNKNOWN_AND_NONE = Vulnerabilities.of(TestVulnerabilities.UNKNOWN_VULNERABILITY,
				TestVulnerabilities.NONE_VULNERABILITY);

		return Stream.of(arguments(TestVulnerabilities.HIGH, "High: CVE-2026-2"),
				arguments(TWO_CRITICAL, "2 Critical"),
				arguments(TestVulnerabilities.CRITICAL_AND_HIGH, "1 Critical and 1 other"),
				arguments(TestVulnerabilities.CRITICAL_HIGH_LOW, "1 Critical and 2 others"),
				arguments(CRITICAL_WITH_UNRATED, "Critical: CVE-2026-1"), // unrated skipped
				arguments(UNKNOWN_AND_NONE, "known vulnerabilities")); // nothing rated
	}

	@ParameterizedTest
	@MethodSource
	void exposesSeverityTextAttributes(Vulnerabilities vulnerabilities, TextAttributesKey expectedKey) {

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getTextAttributes())
				.isSameAs(expectedKey);
	}

	static Stream<Arguments> exposesSeverityTextAttributes() {
		return Stream.of(arguments(TestVulnerabilities.LOW, DependencyAssistantSeverities.VULNERABLE_LOW_KEY),
				arguments(TestVulnerabilities.MEDIUM, DependencyAssistantSeverities.VULNERABLE_MEDIUM_KEY),
				arguments(TestVulnerabilities.HIGH, DependencyAssistantSeverities.VULNERABLE_HIGH_KEY),
				arguments(TestVulnerabilities.CRITICAL, DependencyAssistantSeverities.VULNERABLE_CRITICAL_KEY));
	}

	@Test
	void unratedSeverityUsesWarningTextAttributes() {

		Vulnerabilities vulnerabilities = TestVulnerabilities.UNKNOWN;

		assertThat(VulnerabilitiesPresentation.of(vulnerabilities).getTextAttributes())
				.isSameAs(HighlightInfoType.WEAK_WARNING.getAttributesKey());
	}

}
