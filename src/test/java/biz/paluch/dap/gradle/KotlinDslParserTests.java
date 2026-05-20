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

import java.util.Map;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link KotlinDslParser} and
 * {@link DependencyCollector} (property maps, {@code extra},
 * {@code property(...)}).
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class KotlinDslParserTests {

	@BeforeEach
	void setUp(Project project) {
		GradleFixtures.setup(project);
	}

	// -------------------------------------------------------------------------
	// GAV style
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			plugins {
			    kotlin("jvm")
			    id("org.springframework.boot") version "4.0.3"
			    id("io.spring.dependency-management") version "1.1.7"
			}
			""")
	void pluginsWithVersionsAreDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.springframework.boot")
				.hasVersion("4.0.3");
		assertThat(collector).hasDependencyUsage("io.spring.dependency-management")
				.hasVersion("1.1.7");
		assertThat(collector).hasNoDependencyUsage("jvm");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:6.0.0")
			}
			""")
	void gavWithVersionIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(platform("org.junit:junit-bom:6.0.0"))
			}
			""")
	void gavPlatformIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasDeclaration(DeclarationSource.managed());
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(group = "org.junit", name = "junit-bom", version = "6.0.0")
			}
			""")
	void directDependencyInNamedArgNotationIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.apache.groovy:groovy")
			    implementation("org.springframework.modulith:spring-modulith-starter-core")
			}
			""")
	void dependenciesWithoutVersionAreNotCollected(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Constraints
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:6.0.0!!")
			}
			""")
	void gavEnforcedVersion(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:[5.2.0, 6.0.0]")
			}
			""")
	void gavRange1(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:[5.2.0,6.0.0]")
			}
			""")
	void gavRange2(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:[5.0, 7.0[!!6.0.0")
			}
			""")
	void gavRangeStrictly(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:[6.0.0,)")
			}
			""")
	void gavOpenRange(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:(5.2,6.0.0]")
			}
			""")
	void gavClosedRange(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:0.10.+")
			}
			""")
	void gavPrefixVersion(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation("org.junit:junit-bom:latest.release")
			}
			""")
	void gavLatestReleaseVersion(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Version block
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("[5.0, 6.1[")
			            prefer("6.0.0")
			        }
			    }
			}
			""")
	void versionBlockWithPreferAndStrictlyIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    constraints {
			        implementation("org.junit:junit-bom:6.0.0")
			    }
			}
			""")
	void dependencyConstraintLiteralIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.slf4j:slf4j-api") {
			        version {
			            prefer("1.7.25")
			        }
			    }
			}
			""")
	void versionBlockWithPreferOnlyIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.slf4j", "slf4j-api")
				.hasVersion("1.7.25");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.slf4j:slf4j-api") {
			        version {
			            strictly("[1.7, 1.8[")
			        }
			    }
			}
			""")
	void versionBlockWithRangeOnlyIsSkipped(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("6.0.0")
			        }
			    }
			}
			""")
	void versionBlockWithExactStrictlyNoPreferIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	// -------------------------------------------------------------------------
	// Properties
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			"2.0.4".also { extra["springModulithVersion"] = it }

			dependencies {
			    implementation(platform("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}"))
			}
			""")
	void managedBomWithPropertyExpressionIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.springframework.modulith", "spring-modulith-bom")
				.hasVersion("2.0.4")
				.hasDeclaration(DeclarationSource.managed())
				.hasPropertyVersion("springModulithVersion");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val junit by extra("6.0.0")
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${junit}")
			    }
			}
			""")
	void extraPropertyWithDefault(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasPropertyVersion("junit")
				.hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${property("junit")}")
			    }
			}
			""")
	void mavenBomGradlePropertyViaPropertyIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasDeclaration(DeclarationSource.managed())
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${project.property("junit")}")
			    }
			}
			""")
	void mavenBomGradlePropertyViaProjectPropertyIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val junit: String by project
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${junit}")
			    }
			}
			""")
	void gradlePropertyByProjectDelegateIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["junit"] = "6.0.0"
			val junit: String by extra
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${junit}")
			    }
			}
			""")
	void extraPropertyByDelegateIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["junit"] = "6.0.0"
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${extra["junit"]}")
			    }
			}
			""")
	void mavenBomExtPropertySetViaExtraIndexIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["junit"] = "6.0.0"
			dependencies {
			    implementation(group = "org.junit", name = "junit-bom", version = "${extra["junit"]}")
			}
			""")
	void mapNotationWithVersionPropertyFromExtraIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["commonsVersion"] = "3.19.0"

			dependencies {
			    implementation("org.apache.commons:commons-lang3:${property("commonsVersion")}")
			}
			""")
	void dependencyVersionResolvedViaExtraProperty(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.apache.commons", "commons-lang3")
				.hasVersion("3.19.0")
				.hasPropertyVersion("commonsVersion");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation(group = "org.junit", name = "junit-bom", version = junit)
			}
			""")
	void mapDependencyWithBareVariableVersionIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	// -------------------------------------------------------------------------
	// Constraints + Properties
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["junit"] = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("${extra["junit"]}")
			        }
			    }
			}
			""")
	void gavStrictlyWithVersionPropertyIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly(junit)
			        }
			    }
			}
			""")
	void gavStrictlyWithVersionVariableIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["junit"] = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("[5.0, 6.1[")
			            prefer("${extra["junit"]}")
			        }
			    }
			}
			""")
	void gavStrictlyPreferWithVersionPropertyIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("[5.0, 6.1[")
			            prefer(junit)
			        }
			    }
			}
			""")
	void gavStrictlyPreferWithVersionVariableIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom:$junit")
			}
			""")
	void gavStringInterpolationNoBracesIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom:${junit}")
			}
			""")
	void gavStringInterpolationWithBracesIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	// -------------------------------------------------------------------------
	// Plugins (plugin ID resolution)
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["myPlugin"] = "org.foo"

			plugins {
			    id("${myPlugin}") version "1.0"
			}
			""")
	void pluginIdFromExtraPropertyIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.foo").hasVersion("1.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			plugins {
			    id("${property("myPlugin")}") version "1.0"
			}
			""")
	void pluginIdFromGradlePropertiesIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("myPlugin", "org.foo"));

		assertThat(collector)
				.hasDependencyUsage("org.foo")
				.hasVersion("1.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			plugins {
			    id("${property("myPlugin")}") version "1.0"
			}
			""")
	void pluginIdViaPropertyCallIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("myPlugin", "org.foo"));

		assertThat(collector)
				.hasDependencyUsage("org.foo")
				.hasVersion("1.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			plugins {
			    id("com.example.${property("suffix")}") version "1.0"
			}
			""")
	void pluginIdMixedStringWithPropertyCallIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("suffix", "bar"));

		assertThat(collector)
				.hasDependencyUsage("com.example.bar")
				.hasVersion("1.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["suffix"] = "bar"

			plugins {
			    id("com.example.${suffix}") version "1.0"
			}
			""")
	void pluginIdMixedStringWithVarRefIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("com.example.bar")
				.hasVersion("1.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["myPlugin"] = "org.foo"

			plugins {
			    id("$myPlugin") version "1.0"
			}
			""")
	void pluginIdBareVarRefIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.foo", "org.foo")
				.hasVersion("1.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			plugins {
			    id("${property("missing")}") version "1.0"
			}
			""")
	void pluginIdUnresolvablePropertyCallSkipped(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			plugins {
			    id("${missing}") version "1.0"
			}
			""")
	void pluginIdUnresolvableSkipped(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			plugins {
			    id("${property("myPlugin")}") version "1.0"
			}
			""")
	void pluginIdEmptyValueSkipped(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile, Map.of("myPlugin", ""))).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			plugins {
			    id("${property("pluginId")}") version "${property("pluginVer")}"
			}
			""")
	void pluginIdAndVersionBothFromPropertiesKotlin(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile,
				Map.of("pluginId", "org.foo", "pluginVer", "3.0"));

		assertThat(collector)
				.hasDependencyUsage("org.foo", "org.foo")
				.hasVersion("3.0")
				.hasPropertyVersion("pluginVer");
	}

	// -------------------------------------------------------------------------
	// Settings
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "settings.gradle.kts", content = """
			pluginManagement {
			    plugins {
			        id("${property("myPlugin")}") version "1.0"
			    }
			}
			""")
	void settingsKotlinPluginManagementIdResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile,
				Map.of("myPlugin", "org.foo"));

		assertThat(collector)
				.hasDependencyUsage("org.foo", "org.foo")
				.hasVersion("1.0");
	}

	// -------------------------------------------------------------------------
	// Map notation (edge cases)
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(name = "guava", group = "com.google.guava", version = "33.0.0-jre")
			}
			""")
	void parsesMapSyntaxDependencyCorrectly(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("com.google.guava", "guava").hasVersion("33.0.0-jre");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(name = "guava", version = "33.0.0-jre")
			}
			""")
	void skipsMissingGroup(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(group = "com.google.guava", name = "guava")
			}
			""")
	void skipsMissingVersion(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(group = "com.google.guava", version = "33.0.0-jre")
			}
			""")
	void skipsMissingName(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Full build file
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			plugins {
			    kotlin("jvm")
			    id("org.springframework.boot") version "4.0.3"
			    id("io.spring.dependency-management") version "1.1.7"
			}

			group = "com.example"
			version = "0.0.1-SNAPSHOT"

			extra["springModulithVersion"] = "2.0.4"

			dependencies {
			    implementation("org.apache.groovy:groovy")
			    implementation("org.apache.commons:commons-lang3:3.19.0")
			    implementation("org.springframework.modulith:spring-modulith-starter-core")
			    testImplementation("org.springframework.boot:spring-boot-starter-test")
			    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
			    implementation(platform("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}"))
			}
			""")
	void fullKotlinBuildFileDiscovery(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.springframework.boot", "org.springframework.boot");
		assertThat(collector).hasDependencyUsage("io.spring.dependency-management", "io.spring.dependency-management");
		assertThat(collector).hasDependencyUsage("org.apache.commons", "commons-lang3");
		assertThat(collector)
				.hasDependencyUsage("org.springframework.modulith", "spring-modulith-bom")
				.hasVersion("2.0.4")
				.hasPropertyVersion("springModulithVersion");
	}

}
