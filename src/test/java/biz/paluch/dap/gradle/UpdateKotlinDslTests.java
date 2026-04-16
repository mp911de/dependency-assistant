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
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for {@link UpdateGradleFile} using Kotlin DSL.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class UpdateKotlinDslTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	void kotlinPluginVersionIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle.kts", """
				plugins {
				    kotlin("jvm")
				    id("org.springframework.boot") version "3.5.0"
				    id("io.spring.dependency-management") version "1.1.7"
				}
				""");

		applyUpdate(buildFile, "org.springframework.boot", "org.springframework.boot", "3.5.0",
				DeclarationSource.plugin(),
				VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(buildFile.getText()).contains("id(\"org.springframework.boot\") version \"4.0.3\"")
				.doesNotContain("version \"3.5.0\"");
		assertThat(buildFile.getText()).contains("version \"1.1.7\"");
	}

	@Test
	void settingsKtsPluginVersionIsUpdated() {

		PsiFile settingsFile = fixture.addFileToProject("settings.gradle.kts", """
				pluginManagement {
				    plugins {
				        id("org.springframework.boot") version "3.5.0"
				    }
				}
				rootProject.name = "demo"
				""");

		applyUpdate(settingsFile, "org.springframework.boot", "org.springframework.boot", "3.5.0",
				DeclarationSource.plugin(), VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(settingsFile.getText()).contains("id(\"org.springframework.boot\") version \"4.0.3\"")
				.doesNotContain("version \"3.5.0\"");
	}

	@Test
	void kotlinDependencyVersionIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle.kts", """
				dependencies {
				    implementation("org.apache.commons:commons-lang3:3.19.0")
				    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
				}
				""");

		applyUpdate(buildFile, "org.apache.commons", "commons-lang3", "3.19.0", DeclarationSource.dependency(),
				VersionSource.declared("3.19.0"), "3.20.0");

		assertThat(buildFile.getText()).contains("\"org.apache.commons:commons-lang3:3.20.0\"");
		assertThat(buildFile.getText()).contains("\"org.junit.jupiter:junit-jupiter:5.11.0\"");
	}

	@Test
	void kotlinManagedDependencyVersionIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle.kts", """
				dependencies {
				    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.0"))
				    implementation(platform("io.micrometer:micrometer-bom:1.14.0"))
				}
				""");

		applyUpdate(buildFile, "org.springframework.boot", "spring-boot-dependencies", "3.5.0",
				DeclarationSource.managed(),
				VersionSource.declared("3.5.0"), "3.6.0");

		assertThat(buildFile.getText()).contains("\"org.springframework.boot:spring-boot-dependencies:3.6.0\"");
		assertThat(buildFile.getText()).contains("\"io.micrometer:micrometer-bom:1.14.0\"");
	}

	@Test
	void kotlinExtraPropertyIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle.kts", """
				extra["springVersion"] = "3.5.0"
				extra["lombokVersion"] = "1.18.36"

				dependencies {
				    implementation("org.springframework:spring-core:${property("springVersion")}")
				}
				""");

		applyUpdate(buildFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(buildFile.getText()).contains("extra[\"springVersion\"] = \"3.6.0\"");
		assertThat(buildFile.getText()).contains("extra[\"lombokVersion\"] = \"1.18.36\"");
	}

	@Test
	void kotlinExtraPropertyViaAlsoWithItIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle.kts", """
				"3.5.0".also { extra["springVersion"] = it }
				extra["lombokVersion"] = "1.18.36"

				dependencies {
				    implementation("org.springframework:spring-core:${property("springVersion")}")
				}
				""");

		applyUpdate(buildFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(buildFile.getText()).contains("\"3.6.0\".also");
		assertThat(buildFile.getText()).contains("extra[\"lombokVersion\"] = \"1.18.36\"");
	}

	@Test
	void kotlinExtraPropertyViaBuildStringIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle.kts", """
				extra["springVersion"] = buildString {
				        append("3.5.0")
				    }
				extra["lombokVersion"] = "1.18.36"

				dependencies {
				    implementation("org.springframework:spring-core:${property("springVersion")}")
				}
				""");

		applyUpdate(buildFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(buildFile.getText()).contains("append(\"3.6.0\")");
		assertThat(buildFile.getText()).contains("extra[\"lombokVersion\"] = \"1.18.36\"");
	}

	@Test
	void kotlinExtraPropertyViaTripleQuotedStringIsUpdated() {

		PsiFile buildFile = fixture.addFileToProject("build.gradle.kts", """
				extra["springVersion"] = \"""3.5.0\"""
				extra["lombokVersion"] = "1.18.36"

				dependencies {
				    implementation("org.springframework:spring-core:${property("springVersion")}")
				}
				""");

		applyUpdate(buildFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(buildFile.getText()).contains("\"\"\"3.6.0\"\"\"");
		assertThat(buildFile.getText()).contains("extra[\"lombokVersion\"] = \"1.18.36\"");
	}

	@Test
	void propertyInGradlePropertiesIsUpdatedFromKotlinBuildFile() {

		// findProjectRoot falls back to the build file's parent when no settings file
		// is present.
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
	void propertyInTomlVersionCatalogIsUpdatedFromKotlinBuildFile() {

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
	void gradlePropertiesTakesPriorityOverExtraBlock() {

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
