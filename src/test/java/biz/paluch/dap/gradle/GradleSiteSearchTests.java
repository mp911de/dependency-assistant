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

package biz.paluch.dap.gradle;

import java.util.List;

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.DependencySiteSearchHit;
import biz.paluch.dap.lookup.SiteRole;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for Dependency Site Find in Gradle builds.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GradleSiteSearchTests {

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
	void findsVersionPropertyDefinition(PsiFile toml) {

		GradleFixtures.analyze(toml);

		List<DependencySiteSearchHit> definitions = search(toml, DependencySiteQuery.ofProperty("spring"),
				SiteRole.DECLARATION);

		assertThat(definitions).hasSize(1);
		assertThat(definitions).first().satisfies(it -> {
			assertThat(it.element()).containsText("\"6.1.0\"");
			assertThat(it.label()).isEqualTo("6.1.0");
		});
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation "org.springframework:spring-core:6.1.0"
			}
			""")
	void findsInlineCoordinateDefinition(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		List<DependencySiteSearchHit> definitions = search(buildFile,
				DependencySiteQuery.ofArtifact("org.springframework", "spring-core"), SiteRole.DECLARATION);

		assertThat(definitions).hasSize(1);
		assertThat(definitions).first().satisfies(it -> assertThat(it.element()).containsText("6.1.0"));
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = "springVersion=6.1.0\n")
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation "org.springframework:spring-core:$springVersion"
			}
			""")
	void findsPropertyInterpolationUsageInGroovyScript(PsiFile gradleProperties, PsiFile buildFile) {

		GradleFixtures.analyze(gradleProperties, buildFile);

		List<DependencySiteSearchHit> usages = search(buildFile, DependencySiteQuery.ofProperty("springVersion"),
				SiteRole.VERSION_USAGE);

		assertThat(usages).hasSize(1);
		assertThat(usages).first().extracting(DependencySiteSearchHit::label).isEqualTo("springVersion");
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = "springVersion=6.1.0\n")
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.springframework:spring-core:$springVersion")
			}
			""")
	void findsPropertyInterpolationUsageInKotlinScript(PsiFile gradleProperties, PsiFile buildFile) {

		GradleFixtures.analyze(gradleProperties, buildFile);

		List<DependencySiteSearchHit> usages = search(buildFile, DependencySiteQuery.ofProperty("springVersion"),
				SiteRole.VERSION_USAGE);

		assertThat(usages).hasSize(1);
		assertThat(usages).first().extracting(DependencySiteSearchHit::label).isEqualTo("springVersion");
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
	void findsCatalogAccessorUsage(PsiFile toml, PsiFile buildFile) {

		GradleFixtures.analyze(toml, buildFile);

		List<DependencySiteSearchHit> usages = search(buildFile,
				DependencySiteQuery.ofArtifact("org.springframework", "spring-core"), SiteRole.VERSION_USAGE);

		assertThat(usages).hasSize(1);
		assertThat(usages).first().satisfies(it -> assertThat(it.element()).containsText("libs.spring.core"));
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			spring = "6.1.0"

			[libraries]
			spring-core = { module = "org.springframework:spring-core", version.ref = "spring" }
			""")
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(libs.spring.core)
			}
			""")
	void findsCatalogAccessorUsageInKotlinScript(PsiFile toml, PsiFile buildFile) {

		GradleFixtures.analyze(toml, buildFile);

		List<DependencySiteSearchHit> usages = search(buildFile,
				DependencySiteQuery.ofArtifact("org.springframework", "spring-core"), SiteRole.VERSION_USAGE);

		assertThat(usages).hasSize(1);
		assertThat(usages).first().satisfies(it -> assertThat(it.element()).containsText("libs.spring.core"));
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation "org.springframework:spring-core:6.1.0"
			}
			""")
	void inlineDefinitionsHelperLocatesDeclaration(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		List<DependencySiteSearchHit> definitions = ArtifactReferenceResolver
				.inlineDefinitions(buildFile, DependencySiteQuery.ofArtifact("org.springframework", "spring-core"),
						resolverFor(buildFile)::resolveArtifactReference)
				.stream().filter(finding -> finding.role() == SiteRole.DECLARATION).toList();

		assertThat(definitions).isNotEmpty();
		assertThat(definitions).first().satisfies(it -> assertThat(it.element()).containsText("6.1.0"));
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
	void findsCamelCaseAliasSitesAcrossFiles(PsiFile toml, PsiFile buildFile) {

		GradleFixtures.analyze(toml, buildFile);

		DependencySiteQuery query = DependencySiteQuery.create(it -> it
				.artifact(biz.paluch.dap.artifact.ArtifactId.of("com.microsoft.azure", "applicationinsights-core"))
				.versionProperty("application-insights"));

		assertThat(resolverFor(toml).search(query)).as("toml definition").isNotEmpty();
		assertThat(resolverFor(buildFile).search(query)).as("build.gradle accessor usage").isNotEmpty();
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			orphan = "1.0"
			""")
	void findsOrphanTomlVersionDefinitionWithoutReferencingLibrary(PsiFile toml) {

		GradleFixtures.analyze(toml);

		List<DependencySiteSearchHit> definitions = search(toml, DependencySiteQuery.ofProperty("orphan"),
				SiteRole.DECLARATION);

		assertThat(definitions).hasSize(1);
		assertThat(definitions).first().satisfies(it -> {
			assertThat(it.element()).containsText("\"1.0\"");
			assertThat(it.label()).isEqualTo("1.0");
		});
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = "springVersion=6.1.0\n")
	void findsGradlePropertiesVersionDefinition(PsiFile gradleProperties) {

		GradleFixtures.analyze(gradleProperties);

		List<DependencySiteSearchHit> definitions = search(gradleProperties,
				DependencySiteQuery.ofProperty("springVersion"), SiteRole.DECLARATION);

		assertThat(definitions).hasSize(1);
		assertThat(definitions).first().satisfies(it -> {
			assertThat(it.element()).containsText("6.1.0");
			assertThat(it.label()).isEqualTo("6.1.0");
		});
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    springVersion = '6.1.0'
			}
			""")
	void findsExtVersionDefinitionConsumedInAnotherModule(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		List<DependencySiteSearchHit> definitions = search(buildFile,
				DependencySiteQuery.ofProperty("springVersion"), SiteRole.DECLARATION);

		assertThat(definitions).hasSize(1);
		assertThat(definitions).first().satisfies(it -> {
			assertThat(it.element()).containsText("6.1.0");
			assertThat(it.label()).isEqualTo("6.1.0");
		});
	}

	private List<DependencySiteSearchHit> search(PsiFile file, DependencySiteQuery query, SiteRole role) {
		return resolverFor(file).search(query).stream().filter(finding -> finding.role() == role).toList();
	}

	private GradleArtifactReferenceResolver resolverFor(PsiFile file) {
		GradleProjectContext buildContext = file.getUserData(GradleProjectContext.KEY);
		ProjectState projectState = StateService.getInstance(project).getProjectState(buildContext.getProjectId());
		return new GradleArtifactReferenceResolver(projectState, file);
	}

}
