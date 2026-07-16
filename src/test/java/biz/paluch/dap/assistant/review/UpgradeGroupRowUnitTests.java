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

package biz.paluch.dap.assistant.review;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.check.DeclarationSite;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.fixtures.TestVulnerabilities;
import biz.paluch.dap.state.ProjectId;
import com.intellij.mock.MockVirtualFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GroupRow}.
 *
 * @author Mark Paluch
 */
class UpgradeGroupRowUnitTests {

	@Test
	void memberLabelKeepsBaseArtifactFirst() {

		GroupRow group = group("httpcore5", "httpcore5-reactive");

		assertThat(group.getMemberLabel()).isEqualTo("httpcore5, reactive");
	}

	@Test
	void memberLabelKeepsShortestBaseArtifactFirst() {

		GroupRow group = group("spring", "spring-core", "spring-core-test");

		assertThat(group.getMemberLabel()).isEqualTo("spring, core, core-test");
	}

	@Test
	void memberLabelRejectsDerivedArtifactWithoutSeparatorBoundary() {

		GroupRow group = group("httpcore5", "httpcore5reactive");

		assertThat(group.getMemberLabel()).isEqualTo("2");
	}

	@Test
	void memberLabelRejectsPartialBaseMatch() {

		GroupRow group = group("httpcore5", "httpcore5-reactive", "httpclient5");

		assertThat(group.getMemberLabel()).isEqualTo("3");
	}

	@Test
	void memberLabelUsesCommonSeparatorPrefix() {

		GroupRow group = group("spring-core", "spring-test");

		assertThat(group.getMemberLabel()).isEqualTo("core, test");
	}

	@Test
	void memberLabelUsesCommonSeparatorSuffix() {

		GroupRow group = group("bcpg-jdk18on", "bcpkix-jdk18on", "bcprov-jdk18on");

		assertThat(group.getMemberLabel()).isEqualTo("bcpg, bcpkix, bcprov");
	}

	@Test
	void memberLabelRejectsCommonSuffixWithoutSeparatorBoundary() {

		GroupRow group = group("bcpgjdk18on", "bcpkixjdk18on");

		assertThat(group.getMemberLabel()).isEqualTo("2");
	}

	@Test
	void memberLabelFallsBackToCountWhenTooLong() {

		GroupRow group = group("library", "library-one-really-long-feature-name",
				"library-another-really-long-feature-name");

		assertThat(group.getMemberLabel()).isEqualTo("3");
	}

	@Test
	void vulnerabilitiesMergeMemberRepositoriesByIdentifier() {

		ArtifactVersion vulnerableVersion = ArtifactVersion.of("1.0.0");
		ArtifactVersion cleanVersion = ArtifactVersion.of("1.0.1");
		VulnerabilityRepository first = VulnerabilityRepository.of(Map.of(vulnerableVersion,
				TestVulnerabilities.CRITICAL_AND_HIGH, cleanVersion, Vulnerabilities.clean()));
		VulnerabilityRepository second = VulnerabilityRepository.of(Map.of(vulnerableVersion,
				TestVulnerabilities.CRITICAL));
		GroupRow group = GroupRow.governed(member("core", first), member("support", second));

		assertThat(group.getVulnerabilities(vulnerableVersion)).extracting(Vulnerability::getIdentifier)
				.containsExactly("CVE-2026-1", "CVE-2026-2");
		assertThat(group.getVulnerabilities(cleanVersion).isClean()).isTrue();
		assertThat(group.getVulnerabilities(ArtifactVersion.of("2.0.0")).isUnknown()).isTrue();
	}

	@Test
	void capturesMemberDecisionsAndDerivedNameForPlanPersistence() {

		TableRow core = member("core");
		TableRow support = member("support");
		GroupRow group = GroupRow.inferred(List.of(core, support), "Example");

		assertThat(group.getUpgrades()).containsExactly(core.getUpgrade(), support.getUpgrade());
		assertThat(group.getName()).isEqualTo("Example");
	}

	private static GroupRow group(String... artifactIds) {
		return GroupRow.governed(Arrays.stream(artifactIds).map(UpgradeGroupRowUnitTests::member).toList());
	}

	private static TableRow member(String artifactId) {
		return member(artifactId, VulnerabilityRepository.empty());
	}

	private static TableRow member(String artifactId, VulnerabilityRepository vulnerabilities) {

		ArtifactId id = ArtifactId.of("com.example", artifactId);
		ArtifactVersion version = ArtifactVersion.of("1.0.0");
		Dependency dependency = new Dependency(id, version);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.property("example.version"));
		DeclarationSite site = new DeclarationSite(new MockVirtualFile("pom.xml", "x"), ProjectId.of("com.example",
				"app"), new Dependency(id, version));
		return new TableRow(
				DependencyUpgradeCandidate.create(dependency, Releases.of(Release.of("1.0.0")), vulnerabilities,
						new TestInterfaceAssistant(), DeclaredVersions.from(List.of(site), it -> null, null)));
	}

}
