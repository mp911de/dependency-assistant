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

package biz.paluch.dap.rule;

import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.UpgradeStrategy;
import org.jspecify.annotations.Nullable;

/**
 * Present {@link DependencyRule} testing versions against its
 * {@link Generations}. Backs matched {@link ArtifactRule artifact rules} and,
 * with unconstrained generations, the branch-level fallback rule.
 */
class ResolvedDependencyRule implements DependencyRule {

	private final Generations generations;

	private final String dependencyName;

	private final Predicate<UpgradeStrategy> upgradeStrategies;

	ResolvedDependencyRule(Generations generations, String dependencyName, Predicate<UpgradeStrategy> upgradeStrategies) {
		this.generations = generations;
		this.dependencyName = dependencyName;
		this.upgradeStrategies = upgradeStrategies;
	}

	@Override
	public boolean isPresent() {
		return true;
	}

	@Override
	public Generations getGenerations() {
		return this.generations;
	}

	@Override
	public String getDependencyName() {
		return dependencyName;
	}

	@Override
	public boolean isEnabled(UpgradeStrategy upgradeStrategy) {
		return this.upgradeStrategies.test(upgradeStrategy);
	}

	@Override
	public boolean test(ArtifactVersion version) {
		return this.generations.asVersionPredicate().test(version);
	}

	@Override
	public @Nullable Release suggestRemediation(Releases releases) {

		Release remediation = null;

		for (Generation generation : getGenerations().list()) {

			ArtifactVersion baseline = ArtifactVersion.of(generation.value());
			for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
				if (!isEnabled(strategy)) {
					continue;
				}
				Release release = strategy.select(baseline, releases);
				if (release != null && test(release.version())
						&& (remediation == null || release.compareTo(remediation) > 0)) {
					remediation = release;
				}
			}
		}

		return remediation;
	}

	@Override
	public String toString() {
		return generations.toString();
	}

}
