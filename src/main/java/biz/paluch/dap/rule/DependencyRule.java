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
import biz.paluch.dap.support.UpgradeStrategy;
import org.jspecify.annotations.Nullable;

/**
 * Resolved rule for one governed dependency.
 *
 * <p>A rule {@linkplain #test(Object) tests} whether an {@link ArtifactVersion}
 * falls within the required generations; an {@linkplain #absent() absent} rule
 * accepts every version.
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

	/**
	 * Return an absent rule that allows every version.
	 *
	 * @return the shared absent rule.
	 */
	static DependencyRule absent() {
		return ABSENT;
	}

	/**
	 * Return whether a rule is defined for the dependency.
	 *
	 * @return {@literal true} if a rule is defined; {@literal false} for the
	 * {@linkplain #absent() absent} rule.
	 */
	boolean isPresent();

	/**
	 * Return whether semantic version upgrading governs this rule. This is the
	 * semVer governance mode (the cause), not the observable strategy narrowing
	 * (the effect): it is {@literal true} when semVer-based upgrading is active for
	 * a present, generation-unconstrained dependency, and {@literal false} for an
	 * {@linkplain #absent() absent} rule, a generation-locked rule, a rule with
	 * semVer disabled, and a rule whose semVer governance was lifted (a plugin).
	 * Use this instead of inferring the mode from {@link #getGenerations()} or
	 * {@link #isEnabled(UpgradeStrategy)}.
	 *
	 * @return {@literal true} if semVer upgrading governs this rule;
	 * {@literal false} otherwise.
	 */
	boolean isSemanticUpgradingEnabled();

	/**
	 * Return the required generations, or the
	 * {@linkplain Generations#unconstrained() unconstrained} instance if this rule
	 * is {@linkplain #absent() absent} or unconstrained.
	 */
	Generations getGenerations();

	/**
	 * Return the friendly dependency name, or an empty string if this rule is
	 * {@linkplain #absent() absent}.
	 */
	String getDependencyName();

	/**
	 * Return whether the given upgrade strategy is enabled.
	 * @param upgradeStrategy the upgrade strategy.
	 */
	boolean isEnabled(UpgradeStrategy upgradeStrategy);

	@Nullable
	Release suggestRemediation(Releases releases);

}
