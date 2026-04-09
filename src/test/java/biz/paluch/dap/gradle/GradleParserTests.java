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

import static org.assertj.core.api.Assertions.*;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.junit5.RunInEdt;

/**
 * PSI-level integration tests for {@link GradleParser}.
 *
 * @author Mark Paluch
 */
@RunInEdt(writeIntent = true)
class GradleParserTests {

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
	void pluginsWithVersionsAreDiscovered() {

		PsiFile file = fixture.configureByText("build.gradle", """
				plugins {
				    id 'groovy'
				    id 'org.springframework.boot' version '4.0.3'
				    id 'io.spring.dependency-management' version '1.1.7'
				}
				""");

		DependencyCollector collector = new DependencyCollector();
		GradleParser parser = new GradleParser(collector);
		parser.parseGroovyScript(file);

		// Versioned plugins are registered as update candidates.
		Dependency boot = collector.getDependency("org.springframework.boot", "org.springframework.boot");
		assertThat(boot).as("org.springframework.boot plugin").isNotNull();
		assertThat(boot.getCurrentVersion().toString()).isEqualTo("4.0.3");
		assertThat(boot.getDeclarationSources()).anyMatch(ds -> ds instanceof DeclarationSource.Plugin);

		Dependency depMgmt = collector.getDependency("io.spring.dependency-management", "io.spring.dependency-management");
		assertThat(depMgmt).as("io.spring.dependency-management plugin").isNotNull();
		assertThat(depMgmt.getCurrentVersion().toString()).isEqualTo("1.1.7");

		// Plugin without a version is not an update candidate.
		assertThat(collector.getDependency("groovy", "groovy")).isNull();
	}

	@Test
	void directDependenciesWithInlineVersionsAreDiscovered() {

		PsiFile file = fixture.configureByText("build.gradle", """
				dependencies {
				    implementation 'org.apache.commons:commons-lang3:3.19.0'
				    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.0'
				    compileOnly 'org.projectlombok:lombok:1.18.36'
				}
				""");

		DependencyCollector collector = new DependencyCollector();
		GradleParser parser = new GradleParser(collector);
		parser.parseGroovyScript(file);

		Dependency commonsLang = collector.getDependency("org.apache.commons", "commons-lang3");
		assertThat(commonsLang).as("commons-lang3").isNotNull();
		assertThat(commonsLang.getCurrentVersion().toString()).isEqualTo("3.19.0");
		assertThat(commonsLang.getDeclarationSources()).anyMatch(ds -> ds instanceof DeclarationSource.Dependency);

		assertThat(collector.getDependency("org.junit.jupiter", "junit-jupiter")).as("junit-jupiter").isNotNull();
		assertThat(collector.getDependency("org.projectlombok", "lombok")).as("lombok").isNotNull();
	}

	@Test
	void directDependencyInMapNotationIsDiscovered() {

		PsiFile file = fixture.configureByText("build.gradle", """
				dependencies {
				    implementation group: 'org.apache.groovy', name: 'groovy', version: '4.0.25'
				}
				""");

		DependencyCollector collector = new DependencyCollector();
		GradleParser parser = new GradleParser(collector);
		parser.parseGroovyScript(file);

		Dependency groovy = collector.getDependency("org.apache.groovy", "groovy");
		assertThat(groovy).as("groovy map-notation").isNotNull();
		assertThat(groovy.getCurrentVersion().toString()).isEqualTo("4.0.25");
	}

	@Test
	void dependenciesWithoutVersionAreNotCollected() {

		PsiFile file = fixture.configureByText("build.gradle", """
				dependencies {
				    implementation 'org.apache.groovy:groovy'
				    implementation 'org.springframework.modulith:spring-modulith-starter-core'
				}
				""");

		DependencyCollector collector = new DependencyCollector();
		GradleParser parser = new GradleParser(collector);
		parser.parseGroovyScript(file);

		assertThat(collector.getDependencies()).isEmpty();
	}

	@Test
	void extSetPropertyIsCollected() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext {
				    set('springModulithVersion', "2.0.4")
				}
				""");

		Map<String, String> props = GradleParser.parseExtProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.4");
	}

	@Test
	void extAssignmentPropertyIsCollected() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext {
				    springVersion = '6.1.0'
				    lombokVersion = '1.18.36'
				}
				""");

		Map<String, String> props = GradleParser.parseExtProperties(file);

		assertThat(props).containsEntry("springVersion", "6.1.0").containsEntry("lombokVersion", "1.18.36");
	}

	@Test
	void extDotAssignmentPropertyIsCollected() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext.springBootVersion = '3.5.0'
				""");

		Map<String, String> props = GradleParser.parseExtProperties(file);

		assertThat(props).containsEntry("springBootVersion", "3.5.0");
	}

	@Test
	void managedBomWithPropertyExpressionIsResolved() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext {
				    set('springModulithVersion', "2.0.4")
				}

				dependencyManagement {
				    imports {
				        mavenBom "org.springframework.modulith:spring-modulith-bom:${springModulithVersion}"
				    }
				}
				""");

		Map<String, String> extProps = GradleParser.parseExtProperties(file);
		DependencyCollector collector = new DependencyCollector();
		GradleParser parser = new GradleParser(collector, extProps);
		parser.parseGroovyScript(file);

		Dependency bom = collector.getDependency("org.springframework.modulith", "spring-modulith-bom");
		assertThat(bom).as("spring-modulith-bom").isNotNull();
		assertThat(bom.getCurrentVersion().toString()).isEqualTo("2.0.4");
		assertThat(bom.getDeclarationSources()).anyMatch(ds -> ds instanceof DeclarationSource.Managed);

		// Version should be tracked as a property reference, not an inline literal.
		assertThat(bom.hasPropertyVersion()).isTrue();
		assertThat(bom.findPropertyVersion().getProperty()).isEqualTo("springModulithVersion");
	}

	@Test
	void dependencyVersionResolvedViaExtProperty() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext {
				    commonsVersion = '3.19.0'
				}

				dependencies {
				    implementation "org.apache.commons:commons-lang3:${commonsVersion}"
				}
				""");

		Map<String, String> extProps = GradleParser.parseExtProperties(file);
		DependencyCollector collector = new DependencyCollector();
		GradleParser parser = new GradleParser(collector, extProps);
		parser.parseGroovyScript(file);

		Dependency dep = collector.getDependency("org.apache.commons", "commons-lang3");
		assertThat(dep).as("commons-lang3 via ext property").isNotNull();
		assertThat(dep.getCurrentVersion().toString()).isEqualTo("3.19.0");
		assertThat(dep.findPropertyVersion()).isNotNull();
		assertThat(dep.findPropertyVersion().getProperty()).isEqualTo("commonsVersion");
	}

	@Test
	void tomlVersionCatalogWithVersionRefs() {

		PsiFile file = fixture.configureByText("libs.versions.toml", """
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

		Dependency springBoot = collector.getDependency("org.springframework.boot", "spring-boot-starter");
		assertThat(springBoot).as("spring-boot-starter from TOML").isNotNull();
		assertThat(springBoot.getCurrentVersion().toString()).isEqualTo("3.5.0");
		assertThat(springBoot.getVersionSources()).anyMatch(vs -> vs instanceof VersionSource.VersionCatalogProperty);

		assertThat(collector.getDependency("org.apache.commons", "commons-lang3")).as("commons-lang3 from TOML")
				.isNotNull();
		assertThat(collector.getDependency("org.junit.jupiter", "junit-jupiter")).as("junit-jupiter from TOML").isNotNull();
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

		Dependency log4j = collector.getDependency("org.apache.logging.log4j", "log4j-core");
		assertThat(log4j).as("log4j-core inline version").isNotNull();
		assertThat(log4j.getCurrentVersion().toString()).isEqualTo("2.24.3");
		// Inline version is a declared version, not a property.
		assertThat(log4j.hasPropertyVersion()).isFalse();
	}

	@Test
	void fullGroovyBuildFileDiscovery() {

		PsiFile file = fixture.configureByText("build.gradle", """
				plugins {
				    id 'groovy'
				    id 'org.springframework.boot' version '4.0.3'
				    id 'io.spring.dependency-management' version '1.1.7'
				}

				group = 'com.example'
				version = '0.0.1-SNAPSHOT'

				ext {
				    set('springModulithVersion', "2.0.4")
				}

				repositories {
				    mavenCentral()
				}

				dependencies {
				    implementation 'org.apache.groovy:groovy'
				    implementation 'org.apache.commons:commons-lang3:3.19.0'
				    implementation 'org.springframework.modulith:spring-modulith-starter-core'
				    testImplementation 'org.springframework.boot:spring-boot-starter-test'
				    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
				}

				dependencyManagement {
				    imports {
				        mavenBom "org.springframework.modulith:spring-modulith-bom:${springModulithVersion}"
				    }
				}
				""");

		Map<String, String> extProps = GradleParser.parseExtProperties(file);
		DependencyCollector collector = new DependencyCollector();
		GradleParser parser = new GradleParser(collector, extProps);
		parser.parseGroovyScript(file);

		// Plugins with versions
		assertThat(collector.getDependency("org.springframework.boot", "org.springframework.boot")).as("boot plugin")
				.isNotNull();
		assertThat(collector.getDependency("io.spring.dependency-management", "io.spring.dependency-management"))
				.as("dep-mgmt plugin").isNotNull();

		// Direct dependency with inline version
		assertThat(collector.getDependency("org.apache.commons", "commons-lang3")).as("commons-lang3").isNotNull();

		// Managed BOM resolved via ext set() property
		Dependency bom = collector.getDependency("org.springframework.modulith", "spring-modulith-bom");
		assertThat(bom).as("spring-modulith-bom").isNotNull();
		assertThat(bom.getCurrentVersion().toString()).isEqualTo("2.0.4");
		assertThat(bom.hasPropertyVersion()).isTrue();
	}

	@Test
	void kotlinExtraPropertyAlternateFormatsAreCollected() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				"2.0.3".also { extra["springModulithVersion"] = it }

				extra["buildStringKey"] = buildString {
				        append("2.0.3")
				    }

				extra["tripleKey"] = \"""2.0.3\"""
				""");

		Map<String, String> props = KotlinDslParser.parseExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.3").containsEntry("buildStringKey", "2.0.3")
				.containsEntry("tripleKey", "2.0.3");
	}

}
