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

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Tests for {@link TomlParser}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class TomlParserTests {

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[versions]
			spring-boot = "3.5.0"
			commons-lang = "3.17.0"
			junit = "5.11.0"

			[libraries]
			spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version.ref = "spring-boot" }
			commons-lang3 = { module = "org.apache.commons:commons-lang3", version.ref = "commons-lang" }
			junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
			""")
	void tomlVersionCatalogWithVersionRefs(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.springframework.boot", "spring-boot-starter")
				.hasVersion("3.5.0")
				.hasVersionSource(VersionSource.VersionCatalogProperty.class);
		assertThat(collector).hasDependencyUsage("org.apache.commons", "commons-lang3").hasVersion("3.17.0");
		assertThat(collector).hasDependencyUsage("org.junit.jupiter", "junit-jupiter").hasVersion("5.11.0");
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[libraries]
			log4j-core = { module = "org.apache.logging.log4j:log4j-core", version = "2.24.3" }
			""")
	void tomlVersionCatalogWithInlineVersions(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.apache.logging.log4j", "log4j-core")
				.hasVersion("2.24.3")
				.hasNoPropertyVersion();
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[libraries]
			commons-lang3 = "org.apache.commons:commons-lang3:3.17.0"
			""")
	void gavStringLiteralInLibraries(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.apache.commons", "commons-lang3")
				.hasVersion("3.17.0");
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[libraries]
			bad-entry = "org.example:artifact"
			""")
	void gavStringLiteralWithFewerThanThreeSegmentsIsIgnored(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasNoDependencyUsage("org.example", "artifact");
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[libraries]
			guava = { group = "com.google.guava", name = "guava", version = "33.4.0-jre" }
			""")
	void groupAndNameInlineTableNormalizesToModule(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("com.google.guava", "guava")
				.hasVersion("33.4.0-jre");
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[versions]
			guava = "33.4.0-jre"

			[libraries]
			guava = { group = "com.google.guava", name = "guava", version.ref = "guava" }
			""")
	void groupAndNameInlineTableWithVersionRef(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("com.google.guava", "guava")
				.hasVersion("33.4.0-jre");
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[versions]
			spring-dependency-management = "1.1.5"
			lettuce = "7.0.0.RELEASE"

			[libraries]
			lettuce-core = { module = "io.lettuce:lettuce-core", version.ref = "lettuce" }

			[plugins]
			spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
			""")
	void shouldContainOnlyDeclaredDependencies(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasNoDependencyUsage("io.lettuce", "io.lettuce")
				.hasDependencyUsage("io.lettuce", "lettuce-core")
				.hasVersion("7.0.0.RELEASE");
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[plugins]
			spring-boot = "org.springframework.boot:4.0.0"
			""")
	void pluginShortNotation(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.springframework.boot")
				.hasVersion("4.0.0");
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[plugins]
			spring-boot = "org.springframework.boot"
			""")
	void pluginShortNotationWithSingleSegmentIsIgnored(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasNoDependencyUsage("org.springframework.boot");
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[plugins]
			long-notation = { id = "some.plugin.id", version = "1.4" }
			""")
	void pluginLongNotation(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("some.plugin.id")
				.hasVersion("1.4");
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[versions]
			common = "2.0.0"

			[plugins]
			ref-notation = { id = "some.plugin.id", version.ref = "common" }
			""")
	void pluginRefNotation(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("some.plugin.id")
				.hasVersion("2.0.0");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[libraries]
			guava = "com.google.guava:guava:32.1.3-jre"
			""")
	void tomlAnchorDoesNotDoubleParse(PsiFile tomlFile) {

		DependencyCollector collector = GradleFixtures.analyze(tomlFile);

		assertThat(collector).hasUsageCount(1);
		assertThat(collector).hasDependencyUsage("com.google.guava", "guava").hasVersion("32.1.3-jre");
	}

}
