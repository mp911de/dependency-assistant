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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.state.ProjectId;
import com.intellij.mock.MockVirtualFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradeGroup}.
 *
 * @author Mark Paluch
 */
class UpgradeGroupUnitTests {

	@Test
	void memberLabelKeepsBaseArtifactFirst() {

		UpgradeGroup group = group("httpcore5", "httpcore5-reactive");

		assertThat(group.getMemberLabel()).isEqualTo("httpcore5, reactive");
	}

	@Test
	void memberLabelKeepsShortestBaseArtifactFirst() {

		UpgradeGroup group = group("spring", "spring-core", "spring-core-test");

		assertThat(group.getMemberLabel()).isEqualTo("spring, core, core-test");
	}

	@Test
	void memberLabelRejectsDerivedArtifactWithoutSeparatorBoundary() {

		UpgradeGroup group = group("httpcore5", "httpcore5reactive");

		assertThat(group.getMemberLabel()).isEqualTo("2");
	}

	@Test
	void memberLabelRejectsPartialBaseMatch() {

		UpgradeGroup group = group("httpcore5", "httpcore5-reactive", "httpclient5");

		assertThat(group.getMemberLabel()).isEqualTo("3");
	}

	@Test
	void memberLabelUsesCommonSeparatorPrefix() {

		UpgradeGroup group = group("spring-core", "spring-test");

		assertThat(group.getMemberLabel()).isEqualTo("core, test");
	}

	@Test
	void memberLabelFallsBackToCountWhenTooLong() {

		UpgradeGroup group = group("library", "library-one-really-long-feature-name",
				"library-another-really-long-feature-name");

		assertThat(group.getMemberLabel()).isEqualTo("3");
	}

	private static UpgradeGroup group(String... artifactIds) {

		return UpgradeGroup.of(List.of(artifactIds).stream().map(UpgradeGroupUnitTests::member).toList());
	}

	private static UpgradeCandidate member(String artifactId) {

		ArtifactId id = ArtifactId.of("com.example", artifactId);
		ArtifactVersion version = ArtifactVersion.of("1.0.0");
		Dependency dependency = new Dependency(id, version);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.property("example.version"));
		DeclarationSite site = new DeclarationSite(new MockVirtualFile("pom.xml", "x"), ProjectId.of("com.example",
				"app"), new Dependency(id, version));
		return new UpgradeCandidate(new DependencyUpdateCandidate(dependency, Releases.of(Release.of("1.0.0"))),
				new TestInterfaceAssistant(), DeclaredVersions.from(List.of(site), it -> null, null),
				DependencyRule.absent());
	}

}
