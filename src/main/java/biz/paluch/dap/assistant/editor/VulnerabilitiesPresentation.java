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

package biz.paluch.dap.assistant.editor;

import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.severity.DependencyAssistantSeverities;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.editor.colors.TextAttributesKey;

import org.springframework.util.Assert;

/**
 * User-facing wording for a vulnerable dependency.
 *
 * @author Mark Paluch
 */
class VulnerabilitiesPresentation {

	private final Vulnerabilities vulnerabilities;

	private VulnerabilitiesPresentation(Vulnerabilities vulnerabilities) {
		this.vulnerabilities = vulnerabilities;
	}

	public static VulnerabilitiesPresentation of(Vulnerabilities vulnerabilities) {

		Assert.isTrue(vulnerabilities.isVulnerable(), "Vulnerabilities must be vulnerable");
		return new VulnerabilitiesPresentation(vulnerabilities);
	}

	public String getText() {
		return MessageBundle.message("gutter.vulnerable.tooltip", vulnerabilities.getHighestSeverity()
				.getLabel(),
				vulnerabilities.size());
	}

	public String getDetail() {

		Vulnerability single = null;
		CvssSeverity highest = null;
		int rated = 0;
		int highestCount = 0;

		for (Vulnerability vulnerability : vulnerabilities) {
			CvssSeverity severity = vulnerability.getSeverity();
			if (skip(severity)) {
				continue;
			}

			rated++;
			if (single == null) {
				single = vulnerability;
			}

			if (highest == null || severity.rank() > highest.rank()) {
				highest = severity;
				highestCount = 1;
			} else if (severity == highest) {
				highestCount++;
			}
		}

		if (rated == 0) {
			return MessageBundle.message("UpgradeDependencyAction.vulnerable-detail.generic");
		}

		if (rated == 1) {
			return "%s: %s".formatted(single.getSeverity().getLabel(), single.getIdentifier());
		}

		int others = rated - highestCount;
		if (others == 0) {
			return "%d %s".formatted(highestCount, highest.getLabel());
		}

		return MessageBundle.message("UpgradeDependencyAction.vulnerable-detail.severity-count-with-others",
				highestCount, highest.getLabel(), others);
	}

	public TextAttributesKey getTextAttributes() {
		return switch (vulnerabilities.getHighestSeverity()) {
		case LOW -> DependencyAssistantSeverities.VULNERABLE_LOW_KEY;
		case MEDIUM -> DependencyAssistantSeverities.VULNERABLE_MEDIUM_KEY;
		case HIGH -> DependencyAssistantSeverities.VULNERABLE_HIGH_KEY;
		case CRITICAL -> DependencyAssistantSeverities.VULNERABLE_CRITICAL_KEY;
		case NONE, UNKNOWN -> HighlightInfoType.WEAK_WARNING.getAttributesKey();
		};
	}

	private static boolean skip(CvssSeverity severity) {
		return severity == CvssSeverity.UNKNOWN || severity == CvssSeverity.NONE;
	}

}
