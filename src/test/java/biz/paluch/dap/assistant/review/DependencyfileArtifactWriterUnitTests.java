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

package biz.paluch.dap.assistant.review;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.review.DependencyfileArtifactWriter.ArtifactEntry;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.fixtures.TestDependencyRule;
import biz.paluch.dap.upgrade.UpgradeDecision;
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

		UpgradeDecision candidate = candidate(ArtifactId.of("org.springframework", "spring-core"), "Spring Framework");

		assertThat(DependencyfileArtifactWriter.entries(List.of(candidate), List.of("Spring Framework"),
				"Spring Framework"))
				.containsExactly(new ArtifactEntry("org.springframework:spring-core", "Spring Framework"));
	}

	@Test
	void groupWithSharedGroupIdAndWordBoundaryPrefixYieldsWildcardEntry() {

		List<UpgradeDecision> group = List.of(
				candidate(ArtifactId.of("org.springframework.boot", "spring-boot-starter-web"), "Spring Boot"),
				candidate(ArtifactId.of("org.springframework.boot", "spring-boot-starter-data-jpa"), "Spring Boot"));

		assertThat(DependencyfileArtifactWriter.entries(group, List.of("Spring Boot", "Spring Boot"), "Spring Boot"))
				.containsExactly(
				new ArtifactEntry("org.springframework.boot:spring-boot-starter-*", "Spring Boot"));
	}

	@Test
	void groupWithDifferentGroupIdsFallsBackToPerMemberEntries() {

		List<UpgradeDecision> group = List.of(
				candidate(ArtifactId.of("org.postgresql", "postgresql"), "PostgreSQL"),
				candidate(ArtifactId.of("org.testcontainers", "postgresql"), "PostgreSQL"));

		assertThat(DependencyfileArtifactWriter.entries(group, List.of("PostgreSQL", "PostgreSQL"), "PostgreSQL"))
				.containsExactly(
				new ArtifactEntry("org.postgresql:postgresql", "PostgreSQL"),
				new ArtifactEntry("org.testcontainers:postgresql", "PostgreSQL"));
	}

	@Test
	void groupWithoutWordBoundaryPrefixFallsBackToPerMemberEntries() {

		List<UpgradeDecision> group = List.of(
				candidate(ArtifactId.of("com.example", "webflux"), "Web"),
				candidate(ArtifactId.of("com.example", "webmvc"), "Web"));

		assertThat(DependencyfileArtifactWriter.entries(group, List.of("Web", "Web"), "Web"))
				.containsExactly(
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

	private static UpgradeDecision candidate(ArtifactId artifactId, String dependencyName) {

		Dependency dependency = new Dependency(artifactId, CURRENT);
		dependency.addVersionSource(VersionSource.declared(CURRENT.toString()));
		return UpgradeDecision.create(dependency, Releases.of(Release.of(CURRENT)),
				VulnerabilityRepository.empty(), new TestDependencyRule(dependencyName));
	}

}
