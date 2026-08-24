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

package biz.paluch.dap.upgrade;

import java.util.LinkedHashMap;
import java.util.Map;

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.UpgradeStrategy;

/**
 * Applies {@link DependencyRule} governance to computed upgrade suggestions.
 *
 * <p>Suggestions for disabled non-remediation strategies are removed. Existing
 * remediation suggestions are retained. When the current version violates the
 * rule, the first compliant {@link Release} in artifact-level release order is
 * prepended as the {@link UpgradeStrategy#RULE} target. If no compliant release
 * exists, only strategy filtering is applied.
 *
 * @author Mark Paluch
 */
class ComplianceUpgradeSuggestionsFilter implements UpgradeSuggestionsFilter {

	@Override
	public UpgradeSuggestions filter(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities, DependencyRule rule, UpgradeSuggestions suggestions) {
		UpgradeSuggestions filtered = suggestions
				.filter(strategy -> strategy.isRemediation() || rule.isEnabled(strategy));

		if (!rule.test(dependency.getCurrentVersion())) {

			Release first = releases.stream()
					.filter(it -> rule.test(it.getVersion())).findFirst().orElse(null);
			if (first != null) {
				Map<UpgradeStrategy, UpgradeSuggestion> newSuggestions = new LinkedHashMap<>();
				newSuggestions.put(UpgradeStrategy.RULE, UpgradeSuggestion.of(UpgradeStrategy.RULE, first));
				newSuggestions.putAll(filtered.toMap());
				return UpgradeSuggestions.of(newSuggestions);
			}
		}

		return filtered;
	}

}
