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
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.DependencyfileArtifactWriter.ArtifactEntry;
import biz.paluch.dap.fixtures.TestDependencyRule;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.state.ProjectId;
import com.intellij.mock.MockVirtualFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyfileArtifactWriter} entry computation.
 *
 * @author Mark Paluch
 */
class DependencyfileArtifactWriterUnitTests {

	private static final ArtifactVersion CURRENT = ArtifactVersion.of("6.2.0");

	@Test
	void regularRowYieldsSinglePatternKeyedEntry() {

		UpgradeCandidate candidate = candidate(ArtifactId.of("org.springframework", "spring-core"), "Spring Framework");

		assertThat(DependencyfileArtifactWriter.entries(candidate))
				.containsExactly(new ArtifactEntry("org.springframework:spring-core", "Spring Framework"));
	}

	@Test
	void groupWithSharedGroupIdAndWordBoundaryPrefixYieldsWildcardEntry() {

		UpgradeGroup group = UpgradeGroup.of(List.of(
				candidate(ArtifactId.of("org.springframework.boot", "spring-boot-starter-web"), "Spring Boot"),
				candidate(ArtifactId.of("org.springframework.boot", "spring-boot-starter-data-jpa"), "Spring Boot")));

		assertThat(DependencyfileArtifactWriter.entries(group)).containsExactly(
				new ArtifactEntry("org.springframework.boot:spring-boot-starter-*", "Spring Boot"));
	}

	@Test
	void groupWithDifferentGroupIdsFallsBackToPerMemberEntries() {

		UpgradeGroup group = UpgradeGroup.of(List.of(
				candidate(ArtifactId.of("org.postgresql", "postgresql"), "PostgreSQL"),
				candidate(ArtifactId.of("org.testcontainers", "postgresql"), "PostgreSQL")));

		assertThat(DependencyfileArtifactWriter.entries(group)).containsExactly(
				new ArtifactEntry("org.postgresql:postgresql", "PostgreSQL"),
				new ArtifactEntry("org.testcontainers:postgresql", "PostgreSQL"));
	}

	@Test
	void groupWithoutWordBoundaryPrefixFallsBackToPerMemberEntries() {

		UpgradeGroup group = UpgradeGroup.of(List.of(
				candidate(ArtifactId.of("com.example", "webflux"), "Web"),
				candidate(ArtifactId.of("com.example", "webmvc"), "Web")));

		assertThat(DependencyfileArtifactWriter.entries(group)).containsExactly(
				new ArtifactEntry("com.example:webflux", "Web"),
				new ArtifactEntry("com.example:webmvc", "Web"));
	}

	@Test
	void templateEntriesKeyAndNameByPattern() {

		assertThat(DependencyfileArtifactWriter.templateEntries(List.of(
				ArtifactId.of("org.springframework", "spring-core"),
				ArtifactId.of("axios", "axios"),
				ArtifactId.of("org.springframework", "spring-core")))).containsExactly(
						new ArtifactEntry("axios", "axios"),
						new ArtifactEntry("org.springframework:spring-core", "org.springframework:spring-core"));
	}

	@Test
	void templateEntriesStripsLeadingScopeMarkerFromName() {

		assertThat(DependencyfileArtifactWriter.templateEntries(List.of(ArtifactId.of("@vue/cli", "@vue/cli"))))
				.containsExactly(new ArtifactEntry("@vue/cli", "vue/cli"));
	}

	private static UpgradeCandidate candidate(ArtifactId artifactId, String dependencyName) {

		Dependency dependency = new Dependency(artifactId, CURRENT);
		dependency.addVersionSource(VersionSource.declared(CURRENT.toString()));
		DeclarationSite site = new DeclarationSite(new MockVirtualFile("pom.xml", "// test"),
				ProjectId.of("com.acme", "app"), new Dependency(artifactId, CURRENT));
		return new UpgradeCandidate(new DependencyUpdateCandidate(dependency, Releases.of(Release.of(CURRENT))),
				new TestInterfaceAssistant(), DeclaredVersions.from(List.of(site), it -> null, null),
				new TestDependencyRule(dependencyName));
	}

}
