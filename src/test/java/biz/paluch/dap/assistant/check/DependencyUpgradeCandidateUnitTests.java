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

package biz.paluch.dap.assistant.check;

import biz.paluch.dap.fixtures.TestVulnerabilities;
import biz.paluch.dap.support.UpgradeStrategy;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;
import static biz.paluch.dap.fixtures.TestCandidates.*;

/**
 * Unit tests for {@link DependencyUpgradeCandidate}.
 *
 * @author Mark Paluch
 */
class DependencyUpgradeCandidateUnitTests {

	@Test
	void keepsRemediationTargetInDisplayReleaseUniverse() {

		DependencyUpgradeCandidate upgrade = candidate("com.example:demo:1.0.0",
				it -> it.releases("1.1.0-RC1").vulnerable("1.0.0", TestVulnerabilities.HIGH));

		assertThat(upgrade.getReleases()).containsRelease("1.0.0");
		assertThat(upgrade.getDisplayReleases()).containsRelease("1.1.0-RC1");
		assertThat(upgrade.findCuratedRelease(UpgradeStrategy.SAFE)).hasVersion("1.1.0-RC1");
	}

}
