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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for {@link UpdateGradleFile}.
 *
 * @author Mark Paluch
 */
@RunInEdt(writeIntent = true)
class UpdateGradleFileTests {

	private CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() throws Exception {
		TestFixtureBuilder<IdeaProjectTestFixture> builder = IdeaTestFixtureFactory.getFixtureFactory()
				.createLightFixtureBuilder(new LightProjectDescriptor(), getClass().getSimpleName());
		fixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(builder.getFixture());
		fixture.setUp();
	}

	@AfterEach
	void tearDown() throws Exception {
		fixture.tearDown();
		fixture = null;
	}

	@Test
	void groovyPluginVersionIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle", """
				plugins {
				    id 'groovy'
				    id 'org.springframework.boot' version '3.5.0'
				    id 'io.spring.dependency-management' version '1.1.7'
				}
				""");

		applyUpdate(buildFile, "org.springframework.boot", "org.springframework.boot", "3.5.0",
				DeclarationSource.plugin(),
				VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(buildFile.getText()).contains("id 'org.springframework.boot' version '4.0.3'")
				.doesNotContain("version '3.5.0'");
		assertThat(buildFile.getText()).contains("version '1.1.7'");
	}

	@Test
	void groovyPluginVersionInParenthesisStyleIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle", """
				plugins {
				    id('org.springframework.boot') version '3.5.0'
				}
				""");

		applyUpdate(buildFile, "org.springframework.boot", "org.springframework.boot", "3.5.0",
				DeclarationSource.plugin(),
				VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(buildFile.getText()).contains("version '4.0.3'").doesNotContain("version '3.5.0'");
	}

	@Test
	void settingsGradlePluginVersionIsUpdated() {

		PsiFile settingsFile = fixture.addFileToProject("settings.gradle", """
				pluginManagement {
				    plugins {
				        id 'org.springframework.boot' version '3.5.0'
				    }
				}
				rootProject.name = 'demo'
				""");

		applyUpdate(settingsFile, "org.springframework.boot", "org.springframework.boot", "3.5.0",
				DeclarationSource.plugin(), VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(settingsFile.getText()).contains("id 'org.springframework.boot' version '4.0.3'")
				.doesNotContain("version '3.5.0'");
	}

	@Test
	void groovyDependencyVersionInSingleQuotesIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle", """
				dependencies {
				    implementation 'org.apache.commons:commons-lang3:3.19.0'
				    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.0'
				}
				""");

		applyUpdate(buildFile, "org.apache.commons", "commons-lang3", "3.19.0", DeclarationSource.dependency(),
				VersionSource.declared("3.19.0"), "3.20.0");

		assertThat(buildFile.getText()).contains("'org.apache.commons:commons-lang3:3.20.0'");
		assertThat(buildFile.getText()).contains("'org.junit.jupiter:junit-jupiter:5.11.0'");
	}

	@Test
	void groovyDependencyVersionInDoubleQuotesIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle", """
				dependencies {
				    implementation "org.apache.commons:commons-lang3:3.19.0"
				}
				""");

		applyUpdate(buildFile, "org.apache.commons", "commons-lang3", "3.19.0", DeclarationSource.dependency(),
				VersionSource.declared("3.19.0"), "3.20.0");

		assertThat(buildFile.getText()).contains("\"org.apache.commons:commons-lang3:3.20.0\"");
	}

	@Test
	void groovyManagedDependencyVersionIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle", """
				dependencyManagement {
				    imports {
				        mavenBom 'org.springframework.boot:spring-boot-dependencies:3.5.0'
				        mavenBom 'io.micrometer:micrometer-bom:1.14.0'
				    }
				}
				""");

		applyUpdate(buildFile, "org.springframework.boot", "spring-boot-dependencies", "3.5.0",
				DeclarationSource.managed(),
				VersionSource.declared("3.5.0"), "3.6.0");

		assertThat(buildFile.getText()).contains("'org.springframework.boot:spring-boot-dependencies:3.6.0'");
		assertThat(buildFile.getText()).contains("'io.micrometer:micrometer-bom:1.14.0'");
	}

	@Test
	void propertyInGradlePropertiesIsUpdated() {

		PsiFile propsFile = fixture.addFileToProject("gradle.properties", """
				springVersion=3.5.0
				lombokVersion=1.18.36
				""");

		applyUpdate(propsFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(propsFile.getText()).contains("springVersion=3.6.0");
		assertThat(propsFile.getText()).contains("lombokVersion=1.18.36");
	}

	@Test
	void propertyInTomlVersionCatalogIsUpdated() {

		PsiFile tomlFile = fixture.addFileToProject("gradle/libs.versions.toml",
				"""
						[versions]
						spring-boot = "3.5.0"
						commons-lang = "3.17.0"

						[libraries]
						spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version.ref = "spring-boot" }
						""");

		applyUpdate(tomlFile, "org.springframework.boot", "spring-boot-starter", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.property("spring-boot"), "3.6.0");

		assertThat(tomlFile.getText()).contains("spring-boot = \"3.6.0\"");
		assertThat(tomlFile.getText()).contains("commons-lang = \"3.17.0\"");
	}

	@Test
	void libraryVersionInTomlVersionCatalogIsUpdated() {

		PsiFile tomlFile = fixture.addFileToProject("gradle/libs.versions.toml", """
				[versions]
				commons-lang = "3.17.0"

				[libraries]
				spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version = "3.5.0" }
				""");

		applyUpdate(tomlFile, "org.springframework.boot", "spring-boot-starter", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.declared("3.5.0"), "3.6.0");

		assertThat(tomlFile.getText()).contains("version = \"3.6.0\"");
		assertThat(tomlFile.getText()).contains("commons-lang = \"3.17.0\"");
	}

	@Test
	void pluginVersionInTomlVersionCatalogIsUpdated() {

		PsiFile tomlFile = fixture.addFileToProject("gradle/libs.versions.toml", """
				[versions]
				commons-lang = "3.17.0"

				[plugins]
				spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.6" }
				""");

		applyUpdate(tomlFile, "io.spring.dependency-management", "io.spring.dependency-management", "1.1.6",
				DeclarationSource.dependency(), VersionSource.declared("1.1.6"), "1.1.7");

		assertThat(tomlFile.getText()).contains("version = \"1.1.7\"");
		assertThat(tomlFile.getText()).contains("commons-lang = \"3.17.0\"");
	}

	@Test
	void propertyInGroovyExtBlockIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle", """
				ext {
				    springVersion = '3.5.0'
				    lombokVersion = '1.18.36'
				}

				dependencies {
				    implementation "org.springframework:spring-core:${springVersion}"
				}
				""");

		applyUpdate(buildFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(buildFile.getText()).contains("springVersion = '3.6.0'");
		assertThat(buildFile.getText()).contains("lombokVersion = '1.18.36'");
	}

	@Test
	void propertyInGroovyExtDotAssignmentIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle", """
				ext.springVersion = '3.5.0'
				ext.lombokVersion = '1.18.36'

				dependencies {
				    implementation "org.springframework:spring-core:${springVersion}"
				}
				""");

		applyUpdate(buildFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(buildFile.getText()).contains("ext.springVersion = '3.6.0'");
		assertThat(buildFile.getText()).contains("ext.lombokVersion = '1.18.36'");
	}

	@Test
	void propertyInGroovyExtSetCallIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle", """
				ext {
				    set('springVersion', "2.0.4")
				    set('lombokVersion', "1.18.36")
				}

				dependencies {
				    implementation "org.springframework:spring-core:${springVersion}"
				}
				""");

		applyUpdate(buildFile, "org.springframework", "spring-core", "2.0.4", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.5.0");

		assertThat(buildFile.getText()).contains("set('springVersion', \"3.5.0\")");
		assertThat(buildFile.getText()).contains("set('lombokVersion', \"1.18.36\")");
	}

	@Test
	void gradlePropertiesTakesPriorityOverExtBlock() {

		PsiFile propsFile = fixture.addFileToProject("gradle.properties", "springVersion=3.5.0\n");

		applyUpdate(propsFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(propsFile.getText()).contains("springVersion=3.6.0");
	}

	private void applyUpdate(PsiFile targetFile, String groupId, String artifactId, String fromVersion,
			DeclarationSource declarationSource, VersionSource versionSource, String toVersion) {

		ArtifactId id = ArtifactId.of(groupId, artifactId);
		ArtifactVersion current = ArtifactVersion.of(fromVersion);
		ArtifactVersion updateTo = ArtifactVersion.of(toVersion);

		Dependency dep = new Dependency(id, current);
		dep.addDeclarationSource(declarationSource);
		dep.addVersionSource(versionSource);

		DependencyUpdate update = new DependencyUpdate(id, updateTo, dep.getDeclarationSources(),
				dep.getVersionSources());

		new UpdateGradleFile(fixture.getProject()).applyUpdates(targetFile.getVirtualFile(), List.of(update));
		PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
	}

}
