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

package biz.paluch.dap.upgrade;

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;

/**
 * Creates the upgrade suggestions surfaced for a dependency, including
 * tier-based suggestions, rule remediation, and the Safe Version remediation.
 *
 * @author Mark Paluch
 */
public class UpgradeSuggestionsFactory {

	private static final UpgradeSuggestionsFilter FILTERS = SuggestionFilters.of(new SafeUpgradeSuggestionsFilter(),
			new ComplianceUpgradeSuggestionsFilter());

	private final StateService stateService;

	public UpgradeSuggestionsFactory(StateService stateService) {
		this.stateService = stateService;
	}

	public UpgradeSuggestions createSuggestions(ArtifactReference artifactReference,
			DependencyRuleEvaluator evaluator) {

		if (!artifactReference.isResolved()) {
			return UpgradeSuggestions.empty();
		}

		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		if (!declaration.hasVersionSource() || !declaration.isVersionDefined()) {
			return UpgradeSuggestions.empty();
		}

		return createSuggestions(declaration.toDependency(), evaluator.getRule());
	}

	public UpgradeSuggestions createSuggestions(Dependency dependency, DependencyRule rule) {

		Releases releases = stateService.getCache().getReleases(dependency.getArtifactId());
		if (releases.isEmpty()) {
			return UpgradeSuggestions.empty();
		}

		VulnerabilityRepository vulnerabilities = version -> stateService.getVulnerabilities(dependency.getArtifactId(),
				version);
		return createSuggestions(dependency, releases, vulnerabilities, rule);
	}

	public UpgradeSuggestions createSuggestions(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities, DependencyRule rule) {
		return createSuggestions(DependencyUpgradeSubject.of(dependency, releases, vulnerabilities, rule));
	}

	/**
	 * Create the suggestions for a fully materialized subject. The subject carries
	 * releases, vulnerabilities, and rule, so no state lookup is involved.
	 */
	public static UpgradeSuggestions createSuggestions(DependencyUpgradeSubject subject) {

		Releases releases = subject.getReleases();
		if (releases.isEmpty()) {
			return UpgradeSuggestions.empty();
		}

		UpgradeSuggestions suggestions = UpgradeSuggestions.from(subject.getDependency().getCurrentVersion(), releases);
		return FILTERS.filter(subject, suggestions);
	}

}
