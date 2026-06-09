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
 * Resolved rule for one governed dependency.
 *
 * @author Mark Paluch
 */
public interface DependencyRule extends Predicate<ArtifactVersion> {

	DependencyRule ABSENT = new DependencyRule() {

		@Override
		public boolean test(ArtifactVersion version) {
			return true;
		}

		@Override
		public String getGeneration() {
			return "";
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
	};

	/**
	 * Return an absent rule that allows every version.
	 */
	static DependencyRule absent() {
		return ABSENT;
	}

	/**
	 * If a rule is defined and present, returns {@code true}, otherwise {@code false}.
	 */
	boolean isPresent();

	/**
	 * Return the required generation.
	 */
	String getGeneration();

	/**
	 * Return the dependency name.
	 */
	String getDependencyName();

	/**
	 * Return whether the given upgrade strategy is enabled.
	 * @param upgradeStrategy the upgrade strategy.
	 */
	boolean isEnabled(UpgradeStrategy upgradeStrategy);

}
