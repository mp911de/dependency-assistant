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
import biz.paluch.dap.artifact.UpgradeStrategy;

/**
 * {@link DependencyRule} backed by a matched {@link ArtifactRule}, testing
 * versions against its {@link Generations}.
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
	public String toString() {
		return generations.toString();
	}

}
