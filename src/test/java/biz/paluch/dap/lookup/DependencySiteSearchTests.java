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

package biz.paluch.dap.lookup;

import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.assistant.review.DependencySiteSearchFunction;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.gradle.GradleFixtures;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DependencySiteSearch}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class DependencySiteSearchTests {

	private @TestFixture Project project;

	DependencySiteSearch search;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(project);
		search = new DependencySiteSearch(new DependencySiteSearchFunction(project));
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
	void findsSitesThroughTheLiveDispatcher(PsiFile toml, PsiFile buildFile) {

		GradleFixtures.analyze(toml, buildFile);

		DependencySiteQuery query = DependencySiteQuery
				.create(it -> it.artifact(ArtifactId.of("org.springframework", "spring-core"))
						.versionProperty("spring"));
		DependencySearchResults results = search
				.find(query, List.of(toml.getVirtualFile(), buildFile.getVirtualFile()));

		assertThat(results).isNotEmpty();
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			application-insights = "2.6.4"

			[libraries]
			applicationInsights = { module = "com.microsoft.azure:applicationinsights-core", version.ref = "application-insights" }
			""")
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    api libs.applicationInsights
			}
			""")
	void orchestratorFindsCatalogDefinitionAndUsage(PsiFile toml, PsiFile buildFile) {

		GradleFixtures.analyze(toml, buildFile);

		DependencySiteQuery query = DependencySiteQuery.create(
				it -> it.artifact(ArtifactId.of("com.microsoft.azure", "applicationinsights-core"))
						.versionProperty("application-insights"));
		DependencySearchResults hits = search.find(query, List.of(toml.getVirtualFile(), buildFile.getVirtualFile()));

		assertThat(hits).as("orchestrator findings").isNotEmpty();
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			spring = "6.1.0"
			""")
	@ProjectFile(name = "build.gradle", content = "dependencies {}\n")
	void aggregatesFindingsAcrossFilesDeduplicatingSharedSites(PsiFile catalog, PsiFile buildFile) {

		DependencySiteSearchHit definition = DependencySiteSearchHit.declaration(catalog);
		DependencySiteSearchHit usage = DependencySiteSearchHit.usage(buildFile);
		Map<VirtualFile, DependencySearchResults> sites = Map.of(
				catalog.getVirtualFile(), DependencySearchResults.of(List.of(definition, usage)),
				buildFile.getVirtualFile(), DependencySearchResults.of(List.of(usage)));

		DependencySiteSearch search = DependencySiteSearch.create(
				(file, query) -> sites.getOrDefault(file, DependencySearchResults.empty()));

		DependencySearchResults findings = search.find(DependencySiteQuery.ofProperty("spring"),
				List.of(catalog.getVirtualFile(), buildFile.getVirtualFile()));

		assertThat(findings).containsExactly(definition, usage);
	}

}
