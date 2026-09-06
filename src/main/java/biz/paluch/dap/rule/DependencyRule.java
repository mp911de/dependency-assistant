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

package biz.paluch.dap.rule;

import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.support.UpgradeStrategy;
import org.jspecify.annotations.Nullable;

/**
 * Resolved governance for one dependency.
 *
 * <p>A rule {@linkplain #test(Object) tests} whether an {@link ArtifactVersion}
 * falls within the required generations and exposes the permitted Upgrade
 * Strategies, optional Artifact Display Name, and any available
 * rule-remediation target. An {@linkplain #absent() absent} rule accepts every
 * version and enables every strategy.
 *
 * @author Mark Paluch
 * @see DependencyRules
 * @see Generations
 */
public interface DependencyRule extends Predicate<ArtifactVersion> {

	DependencyRule ABSENT = new DependencyRule() {

		@Override
		public boolean test(ArtifactVersion version) {
			return true;
		}

		@Override
		public Generations getGenerations() {
			return Generations.unconstrained();
		}

		@Override
		public String getDependencyName() {
			return "";
		}

		@Override
		public boolean isEnabled(UpgradeStrategy upgradeStrategy) {
			return true;
		}

		@Override
		public boolean isPresent() {
			return false;
		}

		@Override
		public boolean isSemanticUpgradingEnabled() {
			return false;
		}

		@Override
		public @Nullable Release suggestRemediation(Releases releases) {
			return null;
		}
	};

	static DependencyRule absent() {
		return ABSENT;
	}

	boolean isPresent();

	/**
	 * Return whether semantic upgrading governs this dependency. Generation locks
	 * and plugin-only declarations disable this mode.
	 *
	 * <p>Use this flag to identify the governance mode. Enabled strategies alone do
	 * not establish whether semantic upgrading is active.
	 */
	boolean isSemanticUpgradingEnabled();

	/**
	 * Return the permitted generations, or unconstrained generations for an absent
	 * rule.
	 */
	Generations getGenerations();

	/**
	 * Return the display name, or an empty string for an unnamed or absent rule.
	 */
	String getDependencyName();

	boolean isEnabled(UpgradeStrategy upgradeStrategy);

	/**
	 * Suggest a compliant release that realigns the dependency with this rule.
	 *
	 * @return {@code null} if no remediation target is available.
	 */
	@Nullable
	Release suggestRemediation(Releases releases);

}
