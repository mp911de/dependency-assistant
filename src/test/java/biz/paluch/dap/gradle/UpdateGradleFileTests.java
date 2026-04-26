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
import biz.paluch.dap.support.BuildActionDelegate;
import biz.paluch.dap.support.UpdatedBuildFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

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

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.springframework.boot", "org.springframework.boot",
				"3.5.0",
				DeclarationSource.plugin(),
				VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(updated).hasDependency("org.springframework.boot", "4.0.3");
		assertThat(updated).hasDependency("io.spring.dependency-management", "1.1.7");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id('org.springframework.boot') version '3.5.0'
			}
			""")
	void groovyPluginVersionInParenthesisStyleIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.springframework.boot", "org.springframework.boot",
				"3.5.0",
				DeclarationSource.plugin(),
				VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(updated).hasDependency("org.springframework.boot", "4.0.3");
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

		UpdatedBuildFile updated = applyUpdate(settingsFile, "org.springframework.boot", "org.springframework.boot",
				"3.5.0",
				DeclarationSource.plugin(), VersionSource.declared("3.5.0"), "4.0.3");

		assertThat(updated).hasDependency("org.springframework.boot", "4.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.apache.commons:commons-lang3:3.19.0'
			    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.0'
			}
			""")
	void groovyDependencyVersionInSingleQuotesIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.apache.commons", "commons-lang3", "3.19.0",
				DeclarationSource.dependency(),
				VersionSource.declared("3.19.0"), "3.20.0");

		assertThat(updated).hasDependency("commons-lang3", "3.20.0");
		assertThat(updated).hasDependency("junit-jupiter", "5.11.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation "org.apache.commons:commons-lang3:3.19.0"
			}
			""")
	void groovyDependencyVersionInDoubleQuotesIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.apache.commons", "commons-lang3", "3.19.0",
				DeclarationSource.dependency(),
				VersionSource.declared("3.19.0"), "3.20.0");

		assertThat(updated).hasDependency("commons-lang3", "3.20.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:(5.2,6.0.0]'
			}
			""")
	void groovyConstraintRangeUpperBoundIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				DeclarationSource.dependency(),
				VersionSource.declared("6.0.0"), "6.0.3");

		assertThat(updated).hasDependency("junit-bom", "6.0.3");
		assertThat(buildFile.getText()).contains("implementation 'org.junit:junit-bom:(5.2,6.0.3]'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:[6.0.0,)'
			}
			""")
	void groovyConstraintRangeLowerBoundIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				DeclarationSource.dependency(),
				VersionSource.declared("6.0.0"), "6.0.3");

		assertThat(updated).hasDependency("junit-bom", "6.0.3");
		assertThat(buildFile.getText()).contains("implementation 'org.junit:junit-bom:[6.0.3,)'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:[5.0, 7.0[!!6.0.0'
			}
			""")
	void groovyConstraintBangBangPreferVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				DeclarationSource.dependency(),
				VersionSource.declared("6.0.0"), "6.0.3");

		assertThat(updated).hasDependency("junit-bom", "6.0.3");
		assertThat(buildFile.getText()).contains("implementation 'org.junit:junit-bom:[5.0, 7.0[!!6.0.3'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.0!!'
			}
			""")
	void groovyConstraintBangBangStrictVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				DeclarationSource.dependency(),
				VersionSource.declared("6.0.0"), "6.0.3");

		assertThat(updated).hasDependency("junit-bom", "6.0.3");
		assertThat(buildFile.getText()).contains("implementation 'org.junit:junit-bom:6.0.3!!'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:0.10.+'
			    testImplementation 'org.junit:junit-bom:latest.release'
			}
			""")
	void groovyDynamicConstraintVersionsAreNotUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0", DeclarationSource.dependency(),
				VersionSource.declared("0.10.+"), "6.0.3");
		applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0", DeclarationSource.dependency(),
				VersionSource.declared("latest.release"), "6.0.3");

		assertThat(buildFile.getText()).contains("implementation 'org.junit:junit-bom:0.10.+'");
		assertThat(buildFile.getText()).contains("testImplementation 'org.junit:junit-bom:latest.release'");
		assertThat(buildFile.getText()).doesNotContain("implementation 'org.junit:junit-bom:6.0.3'");
		assertThat(buildFile.getText()).doesNotContain("testImplementation 'org.junit:junit-bom:6.0.3'");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.springframework.boot", "spring-boot-dependencies",
				"3.5.0",
				DeclarationSource.managed(),
				VersionSource.declared("3.5.0"), "3.6.0");

		assertThat(updated).hasDependency("spring-boot-dependencies", "3.6.0");
		assertThat(updated).hasDependency("micrometer-bom", "1.14.0");
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			springVersion=3.5.0
			lombokVersion=1.18.36
			""")
	void propertyInGradlePropertiesIsUpdated(PsiFile propsFile) {

		UpdatedBuildFile updated = applyUpdate(propsFile, "org.springframework", "spring-core", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0").hasProperty("lombokVersion",
				"1.18.36");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.springframework", "spring-core", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0").hasProperty("lombokVersion",
				"1.18.36");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.springframework", "spring-core", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0").hasProperty("lombokVersion",
				"1.18.36");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.springframework", "spring-core", "2.0.4",
				DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.5.0");

		assertThat(updated).hasProperty("springVersion", "3.5.0").hasProperty("lombokVersion",
				"1.18.36");
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = "springVersion=3.5.0\n")
	void gradlePropertiesTakesPriorityOverExtBlock(PsiFile propsFile) {

		UpdatedBuildFile updated = applyUpdate(propsFile, "org.springframework", "spring-core", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.property("springVersion"), "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'com.google.guava', name: 'guava', version: '32.1.2-jre'
			    implementation 'org.apache.commons:commons-lang3:3.19.0'
			}
			""")
	void groovyMapSyntaxVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "com.google.guava", "guava", "32.1.2-jre",
				DeclarationSource.dependency(),
				VersionSource.declared("32.1.2-jre"), "33.0.0-jre");

		assertThat(updated).hasDependency("guava", "33.0.0-jre");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "com.google.guava", "guava", "32.1.2-jre",
				DeclarationSource.dependency(),
				VersionSource.declared("32.1.2-jre"), "33.0.0-jre");

		assertThat(updated).hasDependency("guava", "33.0.0-jre");
		assertThat(buildFile.getText()).contains("classifier: 'android'");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.slf4j", "slf4j-api", "1.7.25",
				DeclarationSource.dependency(),
				VersionSource.declared("1.7.25"), "1.8.0");

		assertThat(updated).hasDependency("slf4j-api", "1.8.0");
		assertThat(buildFile.getText()).contains("strictly '[1.7, 1.8['");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "com.google.guava", "guava", "33.0-jre",
				DeclarationSource.dependency(),
				VersionSource.property("guavaVersion"), "33.1.0-jre");

		assertThat(updated).hasProperty("guavaVersion", "33.1.0-jre");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				DeclarationSource.dependency(),
				VersionSource.property("junitVersion"), "6.0.3");

		assertThat(updated).hasProperty("junitVersion", "6.0.3").hasProperty("otherVersion",
				"1.0.0");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				DeclarationSource.dependency(),
				VersionSource.property("junitVersion"), "6.0.3");

		assertThat(updated).hasProperty("junitVersion", "6.0.3");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				DeclarationSource.dependency(),
				VersionSource.property("junitVersion"), "6.0.3");

		assertThat(updated).hasProperty("junitVersion", "6.0.3");
		assertThat(buildFile.getText()).contains("strictly '[5.0, 7.0['");
	}

	private UpdatedBuildFile applyUpdate(PsiFile targetFile, String groupId, String artifactId, String fromVersion,
			DeclarationSource declarationSource, VersionSource versionSource, String toVersion) {

		ArtifactId id = ArtifactId.of(groupId, artifactId);
		ArtifactVersion current = ArtifactVersion.of(fromVersion);
		ArtifactVersion updateTo = ArtifactVersion.of(toVersion);

		Dependency dep = new Dependency(id, current);
		dep.addDeclarationSource(declarationSource);
		dep.addVersionSource(versionSource);

		DependencyUpdate update = new DependencyUpdate(id, updateTo, dep.getDeclarationSources(),
				dep.getVersionSources());

		new BuildActionDelegate(fixture.getProject(),
				(file, updates) -> new UpdateGradleFile(fixture.getProject()).applyUpdates(targetFile, updates),
				targetFile.getVirtualFile()).updateBuildFile(List.of(update));
		return UpdatedGradleBuildFile.of(targetFile);
	}

}
