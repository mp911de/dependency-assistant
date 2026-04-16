/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link TomlParser}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class TomlParserTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	void tomlVersionCatalogWithVersionRefs() {

		PsiFile file = fixture.configureByText("libs.versions.toml",
				"""
						[versions]
						spring-boot = "3.5.0"
						commons-lang = "3.17.0"
						junit = "5.11.0"

						[libraries]
						spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version.ref = "spring-boot" }
						commons-lang3 = { module = "org.apache.commons:commons-lang3", version.ref = "commons-lang" }
						junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
						""");

		DependencyCollector collector = new DependencyCollector();
		TomlParser parser = new TomlParser(collector);
		parser.parseVersionCatalog(file);

		Dependency springBoot = collector.getUsage("org.springframework.boot", "spring-boot-starter");
		assertThat(springBoot).as("spring-boot-starter from TOML").isNotNull();
		assertThat(springBoot.getCurrentVersion().toString()).isEqualTo("3.5.0");
		assertThat(springBoot.getVersionSources()).anyMatch(vs -> vs instanceof VersionSource.VersionCatalogProperty);

		assertThat(collector.getUsage("org.apache.commons", "commons-lang3")).as("commons-lang3 from TOML")
				.isNotNull();
		assertThat(collector.getUsage("org.junit.jupiter", "junit-jupiter")).as("junit-jupiter from TOML").isNotNull();
	}

	@Test
	void tomlVersionCatalogWithInlineVersions() {

		PsiFile file = fixture.configureByText("libs.versions.toml", """
				[libraries]
				log4j-core = { module = "org.apache.logging.log4j:log4j-core", version = "2.24.3" }
				""");

		DependencyCollector collector = new DependencyCollector();
		TomlParser parser = new TomlParser(collector);
		parser.parseVersionCatalog(file);

		Dependency log4j = collector.getUsage("org.apache.logging.log4j", "log4j-core");
		assertThat(log4j).as("log4j-core inline version").isNotNull();
		assertThat(log4j.getCurrentVersion().toString()).isEqualTo("2.24.3");
		// Inline version is a declared version, not a property.
		assertThat(log4j.hasPropertyVersion()).isFalse();
	}

	@Test
	void gavStringLiteralInLibraries() {

		PsiFile file = fixture.configureByText("libs.versions.toml", """
				[libraries]
				commons-lang3 = "org.apache.commons:commons-lang3:3.17.0"
				""");

		DependencyCollector collector = new DependencyCollector();
		new TomlParser(collector).parseVersionCatalog(file);

		Dependency dep = collector.getUsage("org.apache.commons", "commons-lang3");
		assertThat(dep).isNotNull();
		assertThat(dep.getCurrentVersion().toString()).isEqualTo("3.17.0");
	}

	@Test
	void gavStringLiteralWithFewerThanThreeSegmentsIsIgnored() {

		PsiFile file = fixture.configureByText("libs.versions.toml", """
				[libraries]
				bad-entry = "org.example:artifact"
				""");

		DependencyCollector collector = new DependencyCollector();
		new TomlParser(collector).parseVersionCatalog(file);

		assertThat(collector.getUsage("org.example", "artifact")).isNull();
	}

	@Test
	void groupAndNameInlineTableNormalizesToModule() {

		PsiFile file = fixture.configureByText("libs.versions.toml", """
				[libraries]
				guava = { group = "com.google.guava", name = "guava", version = "33.4.0-jre" }
				""");

		DependencyCollector collector = new DependencyCollector();
		new TomlParser(collector).parseVersionCatalog(file);

		Dependency dep = collector.getUsage("com.google.guava", "guava");
		assertThat(dep).isNotNull();
		assertThat(dep.getCurrentVersion().toString()).isEqualTo("33.4.0-jre");
	}

	@Test
	void groupAndNameInlineTableWithVersionRef() {

		PsiFile file = fixture.configureByText("libs.versions.toml", """
				[versions]
				guava = "33.4.0-jre"

				[libraries]
				guava = { group = "com.google.guava", name = "guava", version.ref = "guava" }
				""");

		DependencyCollector collector = new DependencyCollector();
		new TomlParser(collector).parseVersionCatalog(file);

		Dependency dep = collector.getUsage("com.google.guava", "guava");
		assertThat(dep).isNotNull();
		assertThat(dep.getCurrentVersion().toString()).isEqualTo("33.4.0-jre");
	}

	@Test
	void pluginShortNotation() {

		PsiFile file = fixture.configureByText("libs.versions.toml", """
				[plugins]
				spring-boot = "org.springframework.boot:4.0.0"
				""");

		DependencyCollector collector = new DependencyCollector();
		new TomlParser(collector).parseVersionCatalog(file);

		Dependency dep = collector.getUsage("org.springframework.boot", "org.springframework.boot");
		assertThat(dep).isNotNull();
		assertThat(dep.getCurrentVersion().toString()).isEqualTo("4.0.0");
	}

	@Test
	void pluginShortNotationWithSingleSegmentIsIgnored() {

		PsiFile file = fixture.configureByText("libs.versions.toml", """
				[plugins]
				spring-boot = "org.springframework.boot"
				""");

		DependencyCollector collector = new DependencyCollector();
		new TomlParser(collector).parseVersionCatalog(file);

		assertThat(collector.getUsage("org.springframework.boot", "org.springframework.boot")).isNull();
	}

	@Test
	void pluginLongNotation() {

		PsiFile file = fixture.configureByText("libs.versions.toml", """
				[plugins]
				long-notation = { id = "some.plugin.id", version = "1.4" }
				""");

		DependencyCollector collector = new DependencyCollector();
		new TomlParser(collector).parseVersionCatalog(file);

		Dependency dep = collector.getUsage("some.plugin.id", "some.plugin.id");
		assertThat(dep).isNotNull();
		assertThat(dep.getCurrentVersion().toString()).isEqualTo("1.4");
	}

	@Test
	void pluginRefNotation() {

		PsiFile file = fixture.configureByText("libs.versions.toml", """
				[versions]
				common = "2.0.0"

				[plugins]
				ref-notation = { id = "some.plugin.id", version.ref = "common" }
				""");

		DependencyCollector collector = new DependencyCollector();
		new TomlParser(collector).parseVersionCatalog(file);

		Dependency dep = collector.getUsage("some.plugin.id", "some.plugin.id");
		assertThat(dep).isNotNull();
		assertThat(dep.getCurrentVersion().toString()).isEqualTo("2.0.0");
	}

}
