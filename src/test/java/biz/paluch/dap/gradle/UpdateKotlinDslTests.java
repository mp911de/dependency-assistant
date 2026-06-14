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

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assertions.UpdatedBuildFile;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;
import static biz.paluch.dap.gradle.UpdateTestSupport.*;

/**
 * PSI-level integration tests for {@link UpdateGradleFile} using Kotlin DSL.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateKotlinDslTests {

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			plugins {
			    kotlin("jvm")
			    id("org.springframework.boot") version "3.5.0"
			    id("io.spring.dependency-management") version "1.1.7"
			}
			""")
	void kotlinPluginVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyPluginUpdate(buildFile, "org.springframework.boot", "3.5.0", "4.0.3");

		assertThat(updated).hasDependency("org.springframework.boot", "4.0.3");
		assertThat(updated).hasDependency("io.spring.dependency-management", "1.1.7");
	}

	@Test
	@ProjectFile(name = "settings.gradle.kts", content = """
			pluginManagement {
			    plugins {
			        id("org.springframework.boot") version "3.5.0"
			    }
			}
			rootProject.name = "demo"
			""")
	void settingsKtsPluginVersionIsUpdated(PsiFile settingsFile) {

		UpdatedBuildFile updated = applyPluginUpdate(settingsFile, "org.springframework.boot", "3.5.0", "4.0.3");

		assertThat(updated).hasDependency("org.springframework.boot", "4.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.apache.commons:commons-lang3:3.19.0")
			    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
			}
			""")
	void kotlinDependencyVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.apache.commons", "commons-lang3", "3.20.0");

		assertThat(updated).hasDependency("commons-lang3", "3.20.0");
		assertThat(updated).hasDependency("junit-jupiter", "5.11.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:(5.2,6.0.0]")
			}
			""")
	void kotlinConstraintRangeUpperBoundIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				"6.0.3");

		assertThat(updated).hasDependency("junit-bom", "6.0.3");
		assertThat(buildFile).containsText("implementation(\"org.junit:junit-bom:(5.2,6.0.3]\")");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:[6.0.0,)")
			}
			""")
	void kotlinConstraintRangeLowerBoundIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				"6.0.3");

		assertThat(updated).hasDependency("junit-bom", "6.0.3");
		assertThat(buildFile).containsText("implementation(\"org.junit:junit-bom:[6.0.3,)\")");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:[5.0, 7.0[!!6.0.0")
			}
			""")
	void kotlinConstraintBangBangPreferVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				"6.0.3");

		assertThat(updated).hasDependency("junit-bom", "6.0.3");
		assertThat(buildFile).containsText("implementation(\"org.junit:junit-bom:[5.0, 7.0[!!6.0.3\")");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:6.0.0!!")
			}
			""")
	void kotlinConstraintBangBangStrictVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0",
				"6.0.3");

		assertThat(updated).hasDependency("junit-bom", "6.0.3");
		assertThat(buildFile).containsText("implementation(\"org.junit:junit-bom:6.0.3!!\")");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:0.10.+")
			    testImplementation("org.junit:junit-bom:latest.release")
			}
			""")
	void kotlinDynamicConstraintVersionsAreNotUpdated(PsiFile buildFile) {

		applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0", DeclarationSource.dependency(),
				VersionSource.declared("0.10.+"), "6.0.3");
		applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.0", DeclarationSource.dependency(),
				VersionSource.declared("latest.release"), "6.0.3");

		assertThat(buildFile)
				.containsText("implementation(\"org.junit:junit-bom:0.10.+\")")
				.containsText("testImplementation(\"org.junit:junit-bom:latest.release\")")
				.doesNotContainText("implementation(\"org.junit:junit-bom:6.0.3\")")
				.doesNotContainText("testImplementation(\"org.junit:junit-bom:6.0.3\")");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.0"))
			    implementation(platform("io.micrometer:micrometer-bom:1.14.0"))
			}
			""")
	void kotlinManagedDependencyVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.springframework.boot", "spring-boot-dependencies",
				"3.6.0");

		assertThat(updated).hasDependency("spring-boot-dependencies", "3.6.0");
		assertThat(updated).hasDependency("micrometer-bom", "1.14.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["springVersion"] = "3.5.0"
			extra["lombokVersion"] = "1.18.36"

			dependencies {
			    implementation("org.springframework:spring-core:${property("springVersion")}")
			}
			""")
	void kotlinExtraPropertyIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyPropertyUpdate(buildFile, "org.springframework", "spring-core",
				"springVersion", "3.5.0", "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0").hasProperty("lombokVersion",
				"1.18.36");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			"3.5.0".also { extra["springVersion"] = it }
			extra["lombokVersion"] = "1.18.36"

			dependencies {
			    implementation("org.springframework:spring-core:${property("springVersion")}")
			}
			""")
	void kotlinExtraPropertyViaAlsoWithItIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyPropertyUpdate(buildFile, "org.springframework", "spring-core",
				"springVersion", "3.5.0", "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0").hasProperty("lombokVersion",
				"1.18.36");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["springVersion"] = buildString {
			        append("3.5.0")
			    }
			extra["lombokVersion"] = "1.18.36"

			dependencies {
			    implementation("org.springframework:spring-core:${property("springVersion")}")
			}
			""")
	void kotlinExtraPropertyViaBuildStringIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyPropertyUpdate(buildFile, "org.springframework", "spring-core",
				"springVersion", "3.5.0", "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0").hasProperty("lombokVersion",
				"1.18.36");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["springVersion"] = \"""3.5.0\"""
			extra["lombokVersion"] = "1.18.36"

			dependencies {
			    implementation("org.springframework:spring-core:${property("springVersion")}")
			}
			""")
	void kotlinExtraPropertyViaTripleQuotedStringIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyPropertyUpdate(buildFile, "org.springframework", "spring-core",
				"springVersion", "3.5.0", "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0").hasProperty("lombokVersion",
				"1.18.36");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val springVersion = "3.5.0"
			val otherVersion = "1.1.7"

			dependencies {
			    implementation("org.springframework:spring-core:${springVersion}")
			}
			""")
	void kotlinValLiteralPropertyIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyPropertyUpdate(buildFile, "org.springframework", "spring-core",
				"springVersion", "3.5.0", "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0").hasProperty("otherVersion",
				"1.1.7");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val springVersion by extra("3.5.0")

			dependencies {
			    implementation("org.springframework:spring-core:${springVersion}")
			}
			""")
	void kotlinValByExtraDefaultIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyPropertyUpdate(buildFile, "org.springframework", "spring-core",
				"springVersion", "3.5.0", "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			extra["springVersion"] = "3.5.0"
			extra["lombokVersion"] = "1.18.36"
			val springVersion: String by extra

			dependencies {
			    implementation("org.springframework:spring-core:${property("springVersion")}")
			}
			""")
	void kotlinValByExtraDelegateRoutesThroughExtra(PsiFile buildFile) {

		UpdatedBuildFile updated = applyPropertyUpdate(buildFile, "org.springframework", "spring-core",
				"springVersion", "3.5.0", "3.6.0");

		assertThat(updated).hasProperty("springVersion", "3.6.0").hasProperty("lombokVersion",
				"1.18.36");
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
	void kotlinVersionBlockStrictlyLiteralIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.3");

		assertThat(updated).hasDependency("junit-bom", "6.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    constraints {
			        implementation("org.junit:junit-bom:6.0.0")
			    }
			}
			""")
	void kotlinDependencyConstraintVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.junit", "junit-bom", "6.0.3");

		assertThat(updated).hasDependency("junit-bom", "6.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val guavaVersion = "33.0-jre"

			dependencies {
			    implementation(group = "com.google.guava", name = "guava", version = guavaVersion)
			}
			""")
	void kotlinMapBareRefVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyPropertyUpdate(buildFile, "com.google.guava", "guava",
				"guavaVersion", "33.0-jre", "33.1.0-jre");

		assertThat(updated).hasProperty("guavaVersion", "33.1.0-jre");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(group = "com.google.guava", name = "guava", version = "32.1.2-jre")
			    implementation("org.apache.commons:commons-lang3:3.19.0")
			}
			""")
	void kotlinNamedArgVersionIsUpdated(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "com.google.guava", "guava", "33.0.0-jre");

		assertThat(updated).hasDependency("guava", "33.0.0-jre");
		assertThat(buildFile)
				.containsText("group = \"com.google.guava\"")
				.containsText("name = \"guava\"")
				.containsText("\"org.apache.commons:commons-lang3:3.19.0\"");
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

		UpdatedBuildFile updated = applyUpdate(buildFile, "org.slf4j", "slf4j-api", "1.8.0");

		assertThat(updated).hasDependency("slf4j-api", "1.8.0");
		assertThat(buildFile).containsText("strictly(\"[1.7, 1.8[\")");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(group = "com.google.guava", name = "guava", version = "32.1.2-jre", classifier = "android")
			}
			""")
	void kotlinNamedArgExtraKeyIsPreserved(PsiFile buildFile) {

		UpdatedBuildFile updated = applyUpdate(buildFile, "com.google.guava", "guava", "33.0.0-jre");

		assertThat(updated).hasDependency("guava", "33.0.0-jre");
		assertThat(buildFile).containsText("classifier = \"android\"");
	}

}
