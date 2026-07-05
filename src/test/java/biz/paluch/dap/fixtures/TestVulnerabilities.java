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

package biz.paluch.dap.fixtures;

import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import org.jspecify.annotations.Nullable;

import static biz.paluch.dap.checker.Vulnerabilities.*;

/**
 * Test fixtures for vulnerability scan results.
 *
 * @author Mark Paluch
 */
public class TestVulnerabilities {

	public static final Vulnerability LOW_VULNERABILITY = cve(CvssSeverity.LOW);

	public static final Vulnerability MEDIUM_VULNERABILITY = cve(CvssSeverity.MEDIUM);

	public static final Vulnerability HIGH_VULNERABILITY = cve(CvssSeverity.HIGH);

	public static final Vulnerability CRITICAL_VULNERABILITY = cve(CvssSeverity.CRITICAL);

	public static final Vulnerability NONE_VULNERABILITY = cve(CvssSeverity.NONE);

	public static final Vulnerability UNKNOWN_VULNERABILITY = cve(CvssSeverity.UNKNOWN);

	public static final Vulnerability SECOND_CRITICAL_VULNERABILITY = create("CVE-2026-7", CvssSeverity.CRITICAL);

	public static final Vulnerabilities LOW = of(LOW_VULNERABILITY);

	public static final Vulnerabilities MEDIUM = of(MEDIUM_VULNERABILITY);

	public static final Vulnerabilities HIGH = of(HIGH_VULNERABILITY);

	public static final Vulnerabilities CRITICAL = of(CRITICAL_VULNERABILITY);

	public static final Vulnerabilities UNKNOWN = of(UNKNOWN_VULNERABILITY);

	public static final Vulnerabilities CRITICAL_AND_HIGH = of(CRITICAL_VULNERABILITY,
			HIGH_VULNERABILITY);

	public static final Vulnerabilities CRITICAL_HIGH_LOW = of(CRITICAL_VULNERABILITY,
			HIGH_VULNERABILITY, LOW_VULNERABILITY);

	private TestVulnerabilities() {
	}

	public static Vulnerability cve(CvssSeverity severity) {
		int counter = severity.ordinal() + 1;
		String advisoryId = "GHSA-2026-" + counter;
		return create(advisoryId, "CVE-2026-" + counter, advisoryId, severity);
	}

	public static Vulnerability ghsa(CvssSeverity severity) {
		int counter = severity.ordinal() + 1;
		String ghsa = "GHSA-2026-" + counter;
		return create(ghsa, null, ghsa, severity);
	}

	public static Vulnerability critical(String identifier) {
		return create(identifier, identifier, identifier, CvssSeverity.CRITICAL);
	}

	public static Vulnerability create(String identifier, CvssSeverity severity) {
		return create(identifier, identifier, identifier, severity);
	}

	public static Vulnerability create(String advisoryId, @Nullable String cveId, @Nullable String ghsaId,
			CvssSeverity severity) {
		return cve(advisoryId, cveId, ghsaId, "Remote code execution", score(severity), severity,
				"https://example.com/" + advisoryId);
	}

	public static Vulnerability cve(String advisoryId, @Nullable String cveId, @Nullable String ghsaId,
			String title, double cvssScore, CvssSeverity severity, String sourceUrl) {
		return new Vulnerability(advisoryId, cveId, ghsaId, title, cvssScore, severity, sourceUrl);
	}

	private static double score(CvssSeverity severity) {
		return switch (severity) {
		case CRITICAL -> 9.8;
		case HIGH -> 8.1;
		case MEDIUM -> 5.0;
		case LOW -> 2.5;
		case NONE -> 0.0;
		case UNKNOWN -> -1.0;
		};
	}

}
