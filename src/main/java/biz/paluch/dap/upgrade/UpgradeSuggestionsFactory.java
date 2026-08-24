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
 * Evaluates upgrade policy for a dependency and its materialized release,
 * vulnerability, and governance facts.
 *
 * <p>Evaluation first selects the non-remediation version tiers. It then adds a
 * Safe Version only for a vulnerable current version with an explicitly clean
 * newer target, and finally applies the governing rule. Rule governance removes
 * disabled version tiers and prepends a rule-remediation target, when
 * available, if the current version is noncompliant. Existing remediation
 * targets are retained.
 *
 * @author Mark Paluch
 */
public class UpgradeSuggestionsFactory {

	private static final UpgradeSuggestionsFilter FILTERS = SuggestionFilters.of(new SafeUpgradeSuggestionsFilter(),
			new ComplianceUpgradeSuggestionsFilter());

	private UpgradeSuggestionsFactory() {
	}

	/**
	 * Create suggestions from fully materialized upgrade facts.
	 *
	 * <p>The current dependency version is included in the release universe before
	 * tier, Safe Version, and rule-remediation policy is applied. Safe Version
	 * selection stays within the current versioning scheme but may cross major or
	 * minor version lines. A missing vulnerability result is not considered clean.
	 *
	 * @param dependency the dependency to inspect.
	 * @param releases the known releases for the dependency.
	 * @param vulnerabilities the vulnerability results for known versions.
	 * @param rule the governing dependency rule.
	 * @return the policy-filtered suggestions in evaluation priority order.
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
