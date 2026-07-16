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

package biz.paluch.dap.assistant.check;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.UpgradeStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradeGroup}.
 *
 * @author Mark Paluch
 */
class UpgradeGroupUnitTests {

	@Test
	void recomputesUpgradeFromIntersectedMemberReleases() {

		DependencyUpgradeCandidate core = upgrade("core", "1.0.0", "1.0.0", "1.0.1", "1.1.0");
		DependencyUpgradeCandidate support = upgrade("support", "1.0.0", "1.0.0", "1.0.1", "1.2.0");

		UpgradeGroup group = UpgradeGroup.of(List.of(core, support));

		assertThat(group.getMembers()).containsExactly(core, support);
		assertThat(group.getUpgrade().getReleases()).containsExactlyElementsOf(TestReleases.from("1.0.0", "1.0.1"));
		assertThat(group.getUpgrade().getSuggestions().get(UpgradeStrategy.LATEST).getVersion())
				.isEqualTo(ArtifactVersion.of("1.0.1"));
		assertThat(group.getUpgrade().getSuggestions().get(UpgradeStrategy.MINOR).isPresent()).isFalse();
	}

	private static DependencyUpgradeCandidate upgrade(String artifactId, String current, String... releases) {
		Dependency dependency = new Dependency(ArtifactId.of("com.example", artifactId), ArtifactVersion.of(current));
		return DependencyUpgradeCandidate.create(dependency, TestReleases.from(releases),
				VulnerabilityRepository.empty(),
				DependencyRule.absent(), TestInterfaceAssistant.INSTANCE, DeclaredVersions.empty());
	}

}
