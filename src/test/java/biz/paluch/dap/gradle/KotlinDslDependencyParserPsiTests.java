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
 * PSI-level integration tests for {@link KotlinDslParser}.
 * <p>
 * Mirrors {@link GradleParserPsiTests} using Kotlin DSL syntax ({@code build.gradle.kts}, {@code settings.gradle.kts}).
 * Covers plugin discovery, direct dependency and managed BOM parsing, {@code extra} property collection and resolution,
 * and TOML version catalog parsing.
 *
 * @author Mark Paluch
 */
@RunInEdt(writeIntent = true)
class KotlinDslDependencyParserPsiTests {

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

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				plugins {
				    kotlin("jvm")
				    id("org.springframework.boot") version "4.0.3"
				    id("io.spring.dependency-management") version "1.1.7"
				}
				""");

		DependencyCollector collector = new DependencyCollector();
		KotlinDslParser parser = new KotlinDslParser(collector);
		parser.parseKotlinScript(file);

		// Versioned plugins are registered as update candidates.
		Dependency boot = collector.getDependency("org.springframework.boot", "org.springframework.boot");
		assertThat(boot).as("org.springframework.boot plugin").isNotNull();
		assertThat(boot.getCurrentVersion().toString()).isEqualTo("4.0.3");
		assertThat(boot.getDeclarationSources()).anyMatch(ds -> ds instanceof DeclarationSource.Plugin);

		Dependency depMgmt = collector.getDependency("io.spring.dependency-management", "io.spring.dependency-management");
		assertThat(depMgmt).as("io.spring.dependency-management plugin").isNotNull();
		assertThat(depMgmt.getCurrentVersion().toString()).isEqualTo("1.1.7");

		// Plugin without a version is not an update candidate.
		assertThat(collector.getDependency("jvm", "jvm")).isNull();
	}

	@Test
	void directDependenciesWithInlineVersionsAreDiscovered() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				dependencies {
				    implementation("org.apache.commons:commons-lang3:3.19.0")
				    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
				    compileOnly("org.projectlombok:lombok:1.18.36")
				}
				""");

		DependencyCollector collector = new DependencyCollector();
		KotlinDslParser parser = new KotlinDslParser(collector);
		parser.parseKotlinScript(file);

		Dependency commonsLang = collector.getDependency("org.apache.commons", "commons-lang3");
		assertThat(commonsLang).as("commons-lang3").isNotNull();
		assertThat(commonsLang.getCurrentVersion().toString()).isEqualTo("3.19.0");
		assertThat(commonsLang.getDeclarationSources()).anyMatch(ds -> ds instanceof DeclarationSource.Dependency);

		assertThat(collector.getDependency("org.junit.jupiter", "junit-jupiter")).as("junit-jupiter").isNotNull();
		assertThat(collector.getDependency("org.projectlombok", "lombok")).as("lombok").isNotNull();
	}

	@Test
	void directDependencyInNamedArgNotationIsDiscovered() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				dependencies {
				    implementation(group = "org.apache.groovy", name = "groovy", version = "4.0.25")
				}
				""");

		DependencyCollector collector = new DependencyCollector();
		KotlinDslParser parser = new KotlinDslParser(collector);
		parser.parseKotlinScript(file);

		Dependency groovy = collector.getDependency("org.apache.groovy", "groovy");
		assertThat(groovy).as("groovy named-arg notation").isNotNull();
		assertThat(groovy.getCurrentVersion().toString()).isEqualTo("4.0.25");
	}

	@Test
	void dependenciesWithoutVersionAreNotCollected() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				dependencies {
				    implementation("org.apache.groovy:groovy")
				    implementation("org.springframework.modulith:spring-modulith-starter-core")
				}
				""");

		DependencyCollector collector = new DependencyCollector();
		KotlinDslParser parser = new KotlinDslParser(collector);
		parser.parseKotlinScript(file);

		assertThat(collector.getDependencies()).isEmpty();
	}

	@Test
	void extraPropertiesAreCollected() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				extra["springModulithVersion"] = "2.0.4"
				extra["lombokVersion"] = "1.18.36"
				""");

		Map<String, String> props = KotlinDslParser.parseExtraProperties(file);

		assertThat(props).containsEntry("springModulithVersion", "2.0.4").containsEntry("lombokVersion", "1.18.36");
	}

	@Test
	void managedBomWithPropertyExpressionIsResolved() {

		PsiFile file = fixture.configureByText("build.gradle.kts",
				"""
						extra["springModulithVersion"] = "2.0.4"

						dependencies {
						    implementation(platform("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}"))
						}
						""");

		Map<String, String> extraProps = KotlinDslParser.parseExtraProperties(file);
		DependencyCollector collector = new DependencyCollector();
		KotlinDslParser parser = new KotlinDslParser(collector, extraProps);
		parser.parseKotlinScript(file);

		Dependency bom = collector.getDependency("org.springframework.modulith", "spring-modulith-bom");
		assertThat(bom).as("spring-modulith-bom").isNotNull();
		assertThat(bom.getCurrentVersion().toString()).isEqualTo("2.0.4");
		assertThat(bom.getDeclarationSources()).hasAtLeastOneElementOfType(DeclarationSource.Dependency.class);

		// Version is tracked as a property reference, not an inline literal.
		assertThat(bom.hasPropertyVersion()).isTrue();
		assertThat(bom.findPropertyVersion().getProperty()).isEqualTo("springModulithVersion");
	}

	@Test
	void dependencyVersionResolvedViaExtraProperty() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				extra["commonsVersion"] = "3.19.0"

				dependencies {
				    implementation("org.apache.commons:commons-lang3:${property("commonsVersion")}")
				}
				""");

		Map<String, String> extraProps = KotlinDslParser.parseExtraProperties(file);
		DependencyCollector collector = new DependencyCollector();
		KotlinDslParser parser = new KotlinDslParser(collector, extraProps);
		parser.parseKotlinScript(file);

		Dependency dep = collector.getDependency("org.apache.commons", "commons-lang3");
		assertThat(dep).as("commons-lang3 via extra property").isNotNull();
		assertThat(dep.getCurrentVersion().toString()).isEqualTo("3.19.0");
		assertThat(dep.findPropertyVersion()).isNotNull();
		assertThat(dep.findPropertyVersion().getProperty()).isEqualTo("commonsVersion");
	}

	@Test
	void fullKotlinBuildFileDiscovery() {

		PsiFile file = fixture.configureByText("build.gradle.kts",
				"""
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
						""");

		Map<String, String> extraProps = KotlinDslParser.parseExtraProperties(file);
		DependencyCollector collector = new DependencyCollector();
		KotlinDslParser parser = new KotlinDslParser(collector, extraProps);
		parser.parseKotlinScript(file);

		// Plugins with versions
		assertThat(collector.getDependency("org.springframework.boot", "org.springframework.boot")).as("boot plugin")
				.isNotNull();
		assertThat(collector.getDependency("io.spring.dependency-management", "io.spring.dependency-management"))
				.as("dep-mgmt plugin").isNotNull();

		// Direct dependency with inline version
		assertThat(collector.getDependency("org.apache.commons", "commons-lang3")).as("commons-lang3").isNotNull();

		// Managed BOM resolved via extra property
		Dependency bom = collector.getDependency("org.springframework.modulith", "spring-modulith-bom");
		assertThat(bom).as("spring-modulith-bom").isNotNull();
		assertThat(bom.getCurrentVersion().toString()).isEqualTo("2.0.4");
		assertThat(bom.hasPropertyVersion()).isTrue();
	}

}
