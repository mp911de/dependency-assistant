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

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRule;

/**
 * Evaluate upgrade strategies, security remediation and dependency rules.
 * <p>Rule filtering retains remediation targets. A Safe Version requires
 * explicit clean vulnerability results and stays within the current versioning
 * scheme.
 *
 * @author Mark Paluch
 */
public class UpgradeSuggestionsFactory {

	private static final UpgradeSuggestionsFilter FILTERS = SuggestionFilters.of(new SafeUpgradeSuggestionsFilter(),
			new ComplianceUpgradeSuggestionsFilter());

	private UpgradeSuggestionsFactory() {
	}

	/**
	 * Create policy-filtered suggestions from materialized release and
	 * vulnerability facts. The current version participates even when absent from
	 * release history.
	 */
	public static UpgradeSuggestions createSuggestions(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities, DependencyRule rule) {

		Releases available = releases.withVersion(dependency.getCurrentVersion());
		if (available.isEmpty()) {
			return UpgradeSuggestions.empty();
		}

		UpgradeSuggestions suggestions = UpgradeSuggestions.from(dependency.getCurrentVersion(), available);
		return FILTERS.filter(dependency, available, vulnerabilities, rule, suggestions);
	}

}
