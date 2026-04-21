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
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for {@link UpdateGradleFile}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class UpdateGradleFileTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id 'groovy'
			    id 'org.springframework.boot' version '3.5.0'
			    id 'io.spring.dependency-management' version '1.1.7'
			}
			""")
	void groovyPluginVersionIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.springframework.boot", "org.springframework.boot", "3.5.0",
				DeclarationSource.plugin(),
				VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(buildFile.getText()).contains("id 'org.springframework.boot' version '4.0.3'")
				.doesNotContain("version '3.5.0'");
		assertThat(buildFile.getText()).contains("version '1.1.7'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id('org.springframework.boot') version '3.5.0'
			}
			""")
	void groovyPluginVersionInParenthesisStyleIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.springframework.boot", "org.springframework.boot", "3.5.0",
				DeclarationSource.plugin(),
				VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(buildFile.getText()).contains("version '4.0.3'").doesNotContain("version '3.5.0'");
	}

	@Test
	@ProjectFile(name = "settings.gradle", content = """
			pluginManagement {
			    plugins {
			        id 'org.springframework.boot' version '3.5.0'
			    }
			}
			rootProject.name = 'demo'
			""")
	void settingsGradlePluginVersionIsUpdated(PsiFile settingsFile) {

		applyUpdate(settingsFile, "org.springframework.boot", "org.springframework.boot", "3.5.0",
				DeclarationSource.plugin(), VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(settingsFile.getText()).contains("id 'org.springframework.boot' version '4.0.3'")
				.doesNotContain("version '3.5.0'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.apache.commons:commons-lang3:3.19.0'
			    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.0'
			}
			""")
	void groovyDependencyVersionInSingleQuotesIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.apache.commons", "commons-lang3", "3.19.0", DeclarationSource.dependency(),
				VersionSource.declared("3.19.0"), "3.20.0");

		assertThat(buildFile.getText()).contains("'org.apache.commons:commons-lang3:3.20.0'");
		assertThat(buildFile.getText()).contains("'org.junit.jupiter:junit-jupiter:5.11.0'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation "org.apache.commons:commons-lang3:3.19.0"
			}
			""")
	void groovyDependencyVersionInDoubleQuotesIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.apache.commons", "commons-lang3", "3.19.0", DeclarationSource.dependency(),
				VersionSource.declared("3.19.0"), "3.20.0");

		assertThat(buildFile.getText()).contains("\"org.apache.commons:commons-lang3:3.20.0\"");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencyManagement {
			    imports {
			        mavenBom 'org.springframework.boot:spring-boot-dependencies:3.5.0'
			        mavenBom 'io.micrometer:micrometer-bom:1.14.0'
			    }
			}
			""")
	void groovyManagedDependencyVersionIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.springframework.boot", "spring-boot-dependencies", "3.5.0",
				DeclarationSource.managed(),
				VersionSource.declared("3.5.0"), "3.6.0");

		assertThat(buildFile.getText()).contains("'org.springframework.boot:spring-boot-dependencies:3.6.0'");
		assertThat(buildFile.getText()).contains("'io.micrometer:micrometer-bom:1.14.0'");
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			springVersion=3.5.0
			lombokVersion=1.18.36
			""")
	void propertyInGradlePropertiesIsUpdated(PsiFile propsFile) {

		applyUpdate(propsFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(propsFile.getText()).contains("springVersion=3.6.0");
		assertThat(propsFile.getText()).contains("lombokVersion=1.18.36");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			spring-boot = "3.5.0"
			commons-lang = "3.17.0"

			[libraries]
			spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version.ref = "spring-boot" }
			""")
	void propertyInTomlVersionCatalogIsUpdated(PsiFile tomlFile) {

		applyUpdate(tomlFile, "org.springframework.boot", "spring-boot-starter", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.property("spring-boot"), "3.6.0");

		assertThat(tomlFile.getText()).contains("spring-boot = \"3.6.0\"");
		assertThat(tomlFile.getText()).contains("commons-lang = \"3.17.0\"");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			commons-lang = "3.17.0"

			[libraries]
			spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version = "3.5.0" }
			""")
	void libraryVersionInTomlVersionCatalogIsUpdated(PsiFile tomlFile) {

		applyUpdate(tomlFile, "org.springframework.boot", "spring-boot-starter", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.declared("3.5.0"), "3.6.0");

		assertThat(tomlFile.getText()).contains("version = \"3.6.0\"");
		assertThat(tomlFile.getText()).contains("commons-lang = \"3.17.0\"");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """

			[libraries]
			spring-boot-starter = "org.springframework.boot:spring-boot-starter:3.5.0"
			""")
	void libraryLiteralVersionInTomlVersionCatalogIsUpdated(PsiFile tomlFile) {

		applyUpdate(tomlFile, "org.springframework.boot", "spring-boot-starter", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.declared("3.5.0"), "3.6.0");

		assertThat(tomlFile.getText()).contains("org.springframework.boot:spring-boot-starter:3.6.0");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """

			[plugins]
			spring-boot = "org.springframework.boot:4.0.0"
			""")
	void pluginLiteralVersionInTomlVersionCatalogIsUpdated(PsiFile tomlFile) {

		applyUpdate(tomlFile, "org.springframework.boot", "org.springframework.boot", "3.5.0",
				DeclarationSource.plugin(),
				VersionSource.declared("3.5.0"), "4.0.0");

		assertThat(tomlFile.getText()).contains("org.springframework.boot:4.0.0");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			commons-lang = "3.17.0"

			[plugins]
			spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.6" }
			""")
	void pluginVersionInTomlVersionCatalogIsUpdated(PsiFile tomlFile) {

		applyUpdate(tomlFile, "io.spring.dependency-management", "io.spring.dependency-management", "1.1.6",
				DeclarationSource.plugin(), VersionSource.declared("1.1.6"), "1.1.7");

		assertThat(tomlFile.getText()).contains("version = \"1.1.7\"");
		assertThat(tomlFile.getText()).contains("commons-lang = \"3.17.0\"");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    springVersion = '3.5.0'
			    lombokVersion = '1.18.36'
			}

			dependencies {
			    implementation "org.springframework:spring-core:${springVersion}"
			}
			""")
	void propertyInGroovyExtBlockIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(buildFile.getText()).contains("springVersion = '3.6.0'");
		assertThat(buildFile.getText()).contains("lombokVersion = '1.18.36'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext.springVersion = '3.5.0'
			ext.lombokVersion = '1.18.36'

			dependencies {
			    implementation "org.springframework:spring-core:${springVersion}"
			}
			""")
	void propertyInGroovyExtDotAssignmentIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(buildFile.getText()).contains("ext.springVersion = '3.6.0'");
		assertThat(buildFile.getText()).contains("ext.lombokVersion = '1.18.36'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    set('springVersion', "2.0.4")
			    set('lombokVersion', "1.18.36")
			}

			dependencies {
			    implementation "org.springframework:spring-core:${springVersion}"
			}
			""")
	void propertyInGroovyExtSetCallIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.springframework", "spring-core", "2.0.4", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.5.0");

		assertThat(buildFile.getText()).contains("set('springVersion', \"3.5.0\")");
		assertThat(buildFile.getText()).contains("set('lombokVersion', \"1.18.36\")");
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = "springVersion=3.5.0\n")
	void gradlePropertiesTakesPriorityOverExtBlock(PsiFile propsFile) {

		applyUpdate(propsFile, "org.springframework", "spring-core", "3.5.0", DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(propsFile.getText()).contains("springVersion=3.6.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'com.google.guava', name: 'guava', version: '32.1.2-jre'
			    implementation 'org.apache.commons:commons-lang3:3.19.0'
			}
			""")
	void groovyMapSyntaxVersionIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "com.google.guava", "guava", "32.1.2-jre", DeclarationSource.dependency(),
				VersionSource.declared("32.1.2-jre"), "33.0.0-jre");

		assertThat(buildFile.getText()).contains("version: '33.0.0-jre'");
		assertThat(buildFile.getText()).doesNotContain("version: '32.1.2-jre'");
		assertThat(buildFile.getText()).contains("group: 'com.google.guava'");
		assertThat(buildFile.getText()).contains("name: 'guava'");
		assertThat(buildFile.getText()).contains("'org.apache.commons:commons-lang3:3.19.0'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'com.google.guava', name: 'guava', version: '32.1.2-jre', classifier: 'android'
			}
			""")
	void groovyMapSyntaxExtraKeyIsPreserved(PsiFile buildFile) {

		applyUpdate(buildFile, "com.google.guava", "guava", "32.1.2-jre", DeclarationSource.dependency(),
				VersionSource.declared("32.1.2-jre"), "33.0.0-jre");

		assertThat(buildFile.getText()).contains("version: '33.0.0-jre'");
		assertThat(buildFile.getText()).contains("classifier: 'android'");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(group = "com.google.guava", name = "guava", version = "32.1.2-jre")
			    implementation("org.apache.commons:commons-lang3:3.19.0")
			}
			""")
	void kotlinNamedArgVersionIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "com.google.guava", "guava", "32.1.2-jre", DeclarationSource.dependency(),
				VersionSource.declared("32.1.2-jre"), "33.0.0-jre");

		assertThat(buildFile.getText()).contains("version = \"33.0.0-jre\"");
		assertThat(buildFile.getText()).doesNotContain("version = \"32.1.2-jre\"");
		assertThat(buildFile.getText()).contains("group = \"com.google.guava\"");
		assertThat(buildFile.getText()).contains("name = \"guava\"");
		assertThat(buildFile.getText()).contains("\"org.apache.commons:commons-lang3:3.19.0\"");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(group = "com.google.guava", name = "guava", version = "32.1.2-jre", classifier = "android")
			}
			""")
	void kotlinNamedArgExtraKeyIsPreserved(PsiFile buildFile) {

		applyUpdate(buildFile, "com.google.guava", "guava", "32.1.2-jre", DeclarationSource.dependency(),
				VersionSource.declared("32.1.2-jre"), "33.0.0-jre");

		assertThat(buildFile.getText()).contains("version = \"33.0.0-jre\"");
		assertThat(buildFile.getText()).contains("classifier = \"android\"");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.slf4j:slf4j-api') {
			        version {
			            strictly '[1.7, 1.8['
			            prefer '1.7.25'
			        }
			    }
			}
			""")
	void groovyVersionBlockPreferIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.slf4j", "slf4j-api", "1.7.25", DeclarationSource.dependency(),
				VersionSource.declared("1.7.25"), "1.8.0");

		assertThat(buildFile.getText()).contains("prefer '1.8.0'");
		assertThat(buildFile.getText()).contains("strictly '[1.7, 1.8['");
		assertThat(buildFile.getText()).doesNotContain("prefer '1.7.25'");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.slf4j:slf4j-api") {
			        version {
			            strictly("[1.7, 1.8[")
			            prefer("1.7.25")
			        }
			    }
			}
			""")
	void kotlinVersionBlockPreferIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.slf4j", "slf4j-api", "1.7.25", DeclarationSource.dependency(),
				VersionSource.declared("1.7.25"), "1.8.0");

		assertThat(buildFile.getText()).contains("prefer(\"1.8.0\")");
		assertThat(buildFile.getText()).contains("strictly(\"[1.7, 1.8[\")");
		assertThat(buildFile.getText()).doesNotContain("prefer(\"1.7.25\")");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    guavaVersion = '33.0-jre'
			}

			dependencies {
			    implementation(group: 'com.google.guava', name: 'guava', version: "${guavaVersion}")
			}
			""")
	void groovyMapGStringVersionIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "com.google.guava", "guava", "33.0-jre", DeclarationSource.dependency(),
				VersionSource.property("guavaVersion"), "33.1.0-jre");

		assertThat(buildFile.getText()).contains("guavaVersion = '33.1.0-jre'");
		assertThat(buildFile.getText()).doesNotContain("guavaVersion = '33.0-jre'");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val guavaVersion = "33.0-jre"

			dependencies {
			    implementation(group = "com.google.guava", name = "guava", version = guavaVersion)
			}
			""")
	void kotlinMapBareRefVersionIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "com.google.guava", "guava", "33.0-jre", DeclarationSource.dependency(),
				VersionSource.property("guavaVersion"), "33.1.0-jre");

		assertThat(buildFile.getText()).contains("val guavaVersion = \"33.1.0-jre\"");
		assertThat(buildFile.getText()).doesNotContain("val guavaVersion = \"33.0-jre\"");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			def junitVersion = '6.0.0'
			def otherVersion = '1.0.0'

			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junitVersion
			}
			""")
	void groovyLocalVariableVersionIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0", DeclarationSource.dependency(),
				VersionSource.property("junitVersion"), "6.0.3");

		assertThat(buildFile.getText()).contains("def junitVersion = '6.0.3'");
		assertThat(buildFile.getText()).doesNotContain("def junitVersion = '6.0.0'");
		assertThat(buildFile.getText()).contains("def otherVersion = '1.0.0'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			def junitVersion = '6.0.0'

			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly junitVersion
			        }
			    }
			}
			""")
	void groovyVersionBlockStrictlyVariableIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0", DeclarationSource.dependency(),
				VersionSource.property("junitVersion"), "6.0.3");

		assertThat(buildFile.getText()).contains("def junitVersion = '6.0.3'");
		assertThat(buildFile.getText()).doesNotContain("def junitVersion = '6.0.0'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			def junitVersion = '6.0.0'

			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly '[5.0, 7.0['
			            prefer junitVersion
			        }
			    }
			}
			""")
	void groovyVersionBlockPreferVariableIsUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0", DeclarationSource.dependency(),
				VersionSource.property("junitVersion"), "6.0.3");

		assertThat(buildFile.getText()).contains("def junitVersion = '6.0.3'");
		assertThat(buildFile.getText()).doesNotContain("def junitVersion = '6.0.0'");
		assertThat(buildFile.getText()).contains("strictly '[5.0, 7.0['");
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
