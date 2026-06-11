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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRules;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradeCandidate}.
 *
 * @author Mark Paluch
 */
class UpgradeCandidateUnitTests {

	@Test
	void ruleTargetIsNewestReleasePassingAnyGeneration() {

		DependencyRule rule = DependencyRules.builder()
				.artifact("com.example:*", "2.0", "1.0")
				.build()
				.resolve(ArtifactId.of("com.example", "demo"), null, null);

		UpgradeCandidate candidate = candidate("0.9.0", rule,
				Release.of("0.9.0"), Release.of("1.0.5"), Release.of("2.0.1"), Release.of("3.0.0"));

		assertThat(candidate.getUpdateCandidate().getTargets().get(UpgradeStrategy.RULE))
				.isEqualTo(Release.of("2.0.1"));
	}

	private static UpgradeCandidate candidate(String currentVersion, DependencyRule rule, Release... releases) {

		Dependency dependency = new Dependency(ArtifactId.of("com.example", "demo"),
				ArtifactVersion.of(currentVersion));
		dependency.addDeclarationSource(DeclarationSource.dependency());
		DependencyUpdateCandidate option = new DependencyUpdateCandidate(dependency, Releases.of(releases));
		return new UpgradeCandidate(option, new TestInterfaceAssistant(), DeclaredVersions.none(), rule);
	}

}
