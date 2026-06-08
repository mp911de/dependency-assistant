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

package biz.paluch.dap.assistant;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.rule.DependencyRule;

/**
 * User-interface update candidate enriched with assistant context and version-drift
 * details.
 *
 * @author Mark Paluch
 */
class UpdateCandidate implements HasArtifactId {

	private final DependencyUpdateCandidate option;

	private final InterfaceAssistant interfaceAssistant;

	private final DeclaredVersions declaredVersions;

	private final DependencyRule rule;

	private final EvaluatedDependencyRule ruleResult;

	/**
	 * Create an update candidate.
	 *
	 * @param option the dependency update option to offer.
	 * @param assistant the format-specific assistant that can apply the update.
	 * @param declaredVersions the versions the artifact is declared at, used for
	 * drift reporting.
	 * @param rule the associated dependency rule.
	 */
	public UpdateCandidate(DependencyUpdateCandidate option, InterfaceAssistant assistant, DeclaredVersions declaredVersions, DependencyRule rule) {

		this.option = option;
		this.interfaceAssistant = assistant;
		this.declaredVersions = declaredVersions;
		this.rule = rule;

		if (rule.isDefined()) {
			for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
				if (!rule.isEnabled(strategy)) {
					option.getTargets().remove(strategy);
				}
			}

			if (!rule.test(option.currentVersion())) {
				filterUpgrades(option, rule);
			}
		}

		this.ruleResult = EvaluatedDependencyRule.of(rule, getArtifactId(), currentVersion(), assistant);
	}

	private void filterUpgrades(DependencyUpdateCandidate option, DependencyRule rule) {

		ArtifactVersion generation = ArtifactVersion.of(rule.getGeneration());

		for (UpgradeStrategy strategy : UpgradeStrategy.values()) {

			if (!rule.isEnabled(strategy)) {
				continue;
			}

			Release release = strategy.select(generation, this.option.versionOptions());
			if (release != null && rule.test(release.version())) {
				option.getTargets().put(UpgradeStrategy.RULE, release);
			}
		}
	}

	/**
	 * Return the declared versions for this candidate, carrying drift information.
	 *
	 * @return the declared versions associated with the candidate.
	 */
	public DeclaredVersions getDeclaredVersions() {
		return declaredVersions;
	}

	@Override
	public ArtifactId getArtifactId() {
		return option.getArtifactId();
	}

	/**
	 * Return the currently declared artifact version.
	 *
	 * @return the current version from the wrapped update option.
	 */
	public ArtifactVersion currentVersion() {
		return option.currentVersion();
	}

	/**
	 * Return the wrapped dependency update option.
	 *
	 * @return the update option used to render and apply the candidate.
	 */
	public DependencyUpdateCandidate option() {
		return option;
	}

	/**
	 * Return the format-specific assistant for this candidate.
	 *
	 * @return the assistant that can apply updates for the candidate's project
	 * format.
	 */
	public InterfaceAssistant interfaceAssistant() {
		return interfaceAssistant;
	}

	public DependencyRule rule() {
		return rule;
	}

	public EvaluatedDependencyRule ruleResult() {
		return ruleResult;
	}

	@Override
	public String toString() {
		return getArtifactId() + "@" + currentVersion();
	}

}
