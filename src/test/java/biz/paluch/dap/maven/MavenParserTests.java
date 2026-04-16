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
package biz.paluch.dap.maven;

import java.util.HashMap;
import java.util.Map;

import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.state.Cache;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for {@link MavenParser}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenParserTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	void directDependenciesWithInlineVersionsAreDiscovered() {

		PsiFile file = fixture.configureByText("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>3.19.0</version>
					</dependency>
					<dependency>
						<groupId>org.junit.jupiter</groupId>
						<artifactId>junit-jupiter</artifactId>
						<version>5.11.0</version>
					</dependency>
				</dependencies>
				"""));

		DependencyCollector collector = parse(file);

		Dependency commonsLang = collector.getUsage("org.apache.commons", "commons-lang3");
		assertThat(commonsLang).as("commons-lang3").isNotNull();
		assertThat(commonsLang.getCurrentVersion().toString()).isEqualTo("3.19.0");
		assertThat(commonsLang.getDeclarationSources())
				.anyMatch(ds -> ds instanceof DeclarationSource.Dependency && !(ds instanceof DeclarationSource.Managed));

		assertThat(collector.getUsage("org.junit.jupiter", "junit-jupiter")).as("junit-jupiter").isNotNull();
	}

	@Test
	void managedDependencyWithInlineVersionIsDiscovered() {

		PsiFile file = fixture.configureByText("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>org.springframework.boot</groupId>
							<artifactId>spring-boot-dependencies</artifactId>
							<version>3.5.0</version>
							<type>pom</type>
							<scope>import</scope>
						</dependency>
					</dependencies>
				</dependencyManagement>
				"""));

		DependencyCollector collector = parse(file);

		Dependency bom = collector.getUsage("org.springframework.boot", "spring-boot-dependencies");
		assertThat(bom).as("spring-boot BOM").isNotNull();
		assertThat(bom.getCurrentVersion().toString()).isEqualTo("3.5.0");
		assertThat(bom.getDeclarationSources()).anyMatch(ds -> ds instanceof DeclarationSource.Managed);
	}

	@Test
	void dependencyVersionResolvedViaProperty() {

		PsiFile file = fixture.configureByText("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<properties>
					<commons.version>3.19.0</commons.version>
				</properties>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>${commons.version}</version>
					</dependency>
				</dependencies>
				"""));

		DependencyCollector collector = parse(file);

		Dependency dep = collector.getUsage("org.apache.commons", "commons-lang3");
		assertThat(dep).as("commons-lang3 via property").isNotNull();
		assertThat(dep.getCurrentVersion().toString()).isEqualTo("3.19.0");
		assertThat(dep.hasPropertyVersion()).isTrue();
		assertThat(dep.findPropertyVersion().getProperty()).isEqualTo("commons.version");
	}

	@Test
	void dependenciesWithoutVersionAreNotUpdateCandidates() {

		PsiFile file = fixture.configureByText("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
					</dependency>
				</dependencies>
				"""));

		DependencyCollector collector = parse(file);

		assertThat(collector.getDeclarations()).isEmpty();
	}

	@Test
	void pluginWithInlineVersionIsDiscovered() {

		PsiFile file = fixture.configureByText("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<build>
					<plugins>
						<plugin>
							<artifactId>maven-compiler-plugin</artifactId>
							<version>3.14.0</version>
						</plugin>
					</plugins>
				</build>
				"""));

		DependencyCollector collector = parse(file);

		Dependency plugin = collector.getUsage("org.apache.maven.plugins", "maven-compiler-plugin");
		assertThat(plugin).as("maven-compiler-plugin").isNotNull();
		assertThat(plugin.getCurrentVersion().toString()).isEqualTo("3.14.0");
		assertThat(plugin.getDeclarationSources())
				.anyMatch(ds -> ds instanceof DeclarationSource.Plugin && !(ds instanceof DeclarationSource.Managed));
	}

	@Test
	void parsePropertiesCollectsProjectAndProfileProperties() {

		XmlFile file = (XmlFile) fixture.configureByText("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<properties>
					<root.prop>root-value</root.prop>
				</properties>
				<profiles>
					<profile>
						<id>dev</id>
						<properties>
							<profile.prop>profile-value</profile.prop>
						</properties>
					</profile>
				</profiles>
				"""));

		Map<String, String> props = MavenParser.getProperties(file);

		assertThat(props).containsEntry("root.prop", "root-value").containsEntry("profile.prop", "profile-value");
	}

	@Test
	void singlePomFullDiscovery() {

		PsiFile file = fixture.configureByText("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<properties>
					<bom.version>2.0.4</bom.version>
				</properties>
				<dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>org.springframework.modulith</groupId>
							<artifactId>spring-modulith-bom</artifactId>
							<version>${bom.version}</version>
							<type>pom</type>
							<scope>import</scope>
						</dependency>
					</dependencies>
				</dependencyManagement>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>3.19.0</version>
					</dependency>
				</dependencies>
				<build>
					<plugins>
						<plugin>
							<groupId>org.apache.maven.plugins</groupId>
							<artifactId>maven-surefire-plugin</artifactId>
							<version>3.5.2</version>
						</plugin>
					</plugins>
				</build>
				"""));

		DependencyCollector collector = parse(file);

		assertThat(collector.getUsage("org.apache.commons", "commons-lang3")).as("direct dependency").isNotNull();

		Dependency bom = collector.getUsage("org.springframework.modulith", "spring-modulith-bom");
		assertThat(bom).as("managed BOM").isNotNull();
		assertThat(bom.getCurrentVersion().toString()).isEqualTo("2.0.4");
		assertThat(bom.hasPropertyVersion()).isTrue();

		assertThat(collector.getUsage("org.apache.maven.plugins", "maven-surefire-plugin")).as("plugin").isNotNull();
	}

	@Test
	void multiModuleParentThenChildResolvesVersionFromParentProperty() {

		XmlFile parent = (XmlFile) fixture.addFileToProject("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>parent</artifactId>
				<version>1.0.0</version>
				<properties>
					<junit.version>5.11.0</junit.version>
				</properties>
				"""));

		XmlFile child = (XmlFile) fixture.addFileToProject("module/pom.xml", Pom.of("""
				<parent>
					<groupId>com.example</groupId>
					<artifactId>parent</artifactId>
					<version>1.0.0</version>
				</parent>
				<artifactId>module</artifactId>
				<dependencies>
					<dependency>
						<groupId>org.junit.jupiter</groupId>
						<artifactId>junit-jupiter</artifactId>
						<version>${junit.version}</version>
					</dependency>
				</dependencies>
				"""));

		Cache cache = new Cache();
		DependencyCollector propertyCollector = new DependencyCollector();
		MavenParser parser = new MavenParser(propertyCollector, new HashMap<>());
		parser.parsePomFile(cache, child);
		cache.getProject(ProjectId.of("com.example", "module")).setProperties(propertyCollector);

		DependencyCollector collector = new DependencyCollector();
		parser = new MavenParser(collector, new HashMap<>());
		parser.parsePomFile(cache, parent);

		Dependency junit = collector.getUsage("org.junit.jupiter", "junit-jupiter");
		assertThat(junit).as("junit-jupiter resolved from parent property").isNotNull();
		assertThat(junit.getCurrentVersion().toString()).isEqualTo("5.11.0");
		assertThat(junit.hasPropertyVersion()).isTrue();
		assertThat(junit.findPropertyVersion().getProperty()).isEqualTo("junit.version");
	}

	@Test
	void multiModuleChildAddsDependenciesAlongsideParent() {

		XmlFile parent = (XmlFile) fixture.addFileToProject("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>parent</artifactId>
				<version>1.0.0</version>
				<dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>org.assertj</groupId>
							<artifactId>assertj-core</artifactId>
							<version>3.27.0</version>
						</dependency>
					</dependencies>
				</dependencyManagement>
				"""));

		XmlFile child = (XmlFile) fixture.addFileToProject("module/pom.xml", Pom.of("""
				<parent>
					<groupId>com.example</groupId>
					<artifactId>parent</artifactId>
					<version>1.0.0</version>
				</parent>
				<artifactId>module</artifactId>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>3.19.0</version>
					</dependency>
				</dependencies>
				"""));

		DependencyCollector collector = new DependencyCollector();
		MavenParser parser = new MavenParser(collector, new HashMap<>());
		parser.parsePomFile(new Cache(), parent);
		parser.parsePomFile(new Cache(), child);

		assertThat(collector.getUsage("org.assertj", "assertj-core")).as("parent dependencyManagement").isNotNull();
		assertThat(collector.getUsage("org.apache.commons", "commons-lang3")).as("child dependency").isNotNull();
	}

	private DependencyCollector parse(PsiFile pomFile) {
		DependencyCollector collector = new DependencyCollector();
		MavenParser parser = new MavenParser(collector, new HashMap<>());
		parser.parsePomFile(new Cache(), (XmlFile) pomFile);
		return collector;
	}

}
