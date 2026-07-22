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
 * Present {@link DependencyRule} fixture carrying a dependency name. The
 * default form accepts every version; a {@link #rejecting()} rule rejects every
 * version and reports semantic upgrading as disabled.
 *
 * @author Mark Paluch
 */
public record TestDependencyRule(String dependencyName, boolean accepting) implements DependencyRule {

	public TestDependencyRule(String dependencyName) {
		this(dependencyName, true);
	}

	/**
	 * Create a rule that rejects every version.
	 */
	public static TestDependencyRule rejecting() {
		return new TestDependencyRule("", false);
	}

	@Override
	public boolean isPresent() {
		return true;
	}

	@Override
	public boolean isSemanticUpgradingEnabled() {
		return accepting;
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
		return accepting;
	}

	@Override
	public @Nullable Release suggestRemediation(Releases releases) {
		return null;
	}

}
