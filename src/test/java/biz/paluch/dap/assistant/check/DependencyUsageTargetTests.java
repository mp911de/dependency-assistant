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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.gradle.GradleFixtures;
import biz.paluch.dap.lookup.DependencySearchResults;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.DependencySiteSearchHit;
import biz.paluch.dap.lookup.SiteRole;
import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for {@link DependencyUsageTarget}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class DependencyUsageTargetTests {

	private @TestFixture Project project;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			spring = "6.1.0"

			[libraries]
			spring-core = { module = "org.springframework:spring-core", version.ref = "spring" }
			""")
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation libs.spring.core
			}
			""")
	void searchesTheCurrentFileScope(PsiFile toml, PsiFile buildFile) {

		GradleFixtures.analyze(toml, buildFile);

		DependencySiteQuery query = DependencySiteQuery.create(it -> it
				.artifact(ArtifactId.of("org.springframework", "spring-core"))
				.versionProperty("spring"));
		DependencyUsageTarget target = new DependencyUsageTarget(project, query, toml.getVirtualFile());

		Sequence<DependencySiteSearchHit> catalogSites = target.findSites();
		target = new DependencyUsageTarget(project, query, toml.getVirtualFile(), buildFile.getVirtualFile());
		Sequence<DependencySiteSearchHit> allSites = target.findSites();

		assertThat(catalogSites).isNotEmpty();
		assertThat(allSites).containsAll(catalogSites).doesNotHaveDuplicates();
		assertThat(allSites).extracting(DependencySiteSearchHit::role)
				.contains(SiteRole.DECLARATION, SiteRole.VERSION_USAGE);
		assertThat(allSites).hasSizeGreaterThan(1);
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation "org.springframework:spring-core:6.1.0"
			}
			""")
	void fallsBackToInlineDefinitions(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		DependencyUsageTarget target = new DependencyUsageTarget(project,
				DependencySiteQuery.ofArtifact("org.springframework", "spring-core"),
				buildFile.getVirtualFile());

		DependencySearchResults sites = target.findSites();

		assertThat(sites).singleElement().satisfies(site -> {
			assertThat(site.role()).isEqualTo(SiteRole.DECLARATION);
			assertThat(site.element()).containsText("6.1.0");
		});
	}

}
