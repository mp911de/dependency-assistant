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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.Generations;
import biz.paluch.dap.support.UpgradeStrategy;
import org.jspecify.annotations.Nullable;

/**
 * Present, permissive {@link DependencyRule} fixture carrying only a dependency
 * name.
 *
 * @author Mark Paluch
 */
public record TestDependencyRule(String dependencyName) implements DependencyRule {

	@Override
	public boolean isPresent() {
		return true;
	}

	@Override
	public boolean isSemanticUpgradingEnabled() {
		return true;
	}

	@Override
	public Generations getGenerations() {
		return Generations.unconstrained();
	}

	@Override
	public String getDependencyName() {
		return dependencyName;
	}

	@Override
	public boolean isEnabled(UpgradeStrategy upgradeStrategy) {
		return true;
	}

	@Override
	public boolean test(ArtifactVersion version) {
		return true;
	}

	@Override
	public @Nullable Release suggestRemediation(Releases releases) {
		return null;
	}

}
