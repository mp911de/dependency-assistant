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

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.checker.CheckerIcons;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.ResolvableIcon;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyUpgradeIcons}.
 *
 * @author Mark Paluch
 */
class DependencyUpgradeIconsUnitTests {

	@Test
	void resolvesEveryVersionAgeIconAndReference() {

		for (VersionAge age : VersionAge.values()) {
			ResolvableIcon resolvable = DependencyUpgradeIcons.resolve(age);
			assertThat(resolvable.getIcon()).isNotNull();
			assertThat(resolvable.getReference()).isNotBlank();
		}
	}

	@Test
	void resolvesEveryUpgradeStrategyIconAndReference() {

		for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
			ResolvableIcon resolvable = DependencyUpgradeIcons.resolve(strategy);
			assertThat(resolvable.getIcon()).isNotNull();
			assertThat(resolvable.getReference()).isNotBlank();
		}

		ResolvableIcon safe = DependencyUpgradeIcons.resolve(UpgradeStrategy.SAFE);
		assertThat(safe.getIcon()).isSameAs(CheckerIcons.SAFE);
		assertThat(safe.getReference()).isEqualTo("biz.paluch.dap.checker.CheckerIcons.SAFE");
	}

	@Test
	void resolvesRuleWarningIconAndReference() {

		ResolvableIcon ruleWarning = DependencyUpgradeIcons.ruleWarning();
		assertThat(ruleWarning.getIcon()).isSameAs(DependencyAssistantIcons.DEPENDENCY_RULE_WARN);
		assertThat(ruleWarning.getReference())
				.isEqualTo("biz.paluch.dap.DependencyAssistantIcons.DEPENDENCY_RULE_WARN");
	}

}
