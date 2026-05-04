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

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectId;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link MavenParser}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenParserTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
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
			</project>
			""")
	void directDependenciesWithInlineVersionsAreDiscovered(PsiFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("commons-lang3")
				.hasVersion("3.19.0")
				.hasDeclaration(DeclarationSource.dependency());
		assertThat(collector).hasDependencyUsage("junit-jupiter");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
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
			</project>
			""")
	void managedDependencyWithInlineVersionIsDiscovered(PsiFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("spring-boot-dependencies")
				.hasVersion("3.5.0")
				.hasDeclaration(DeclarationSource.managed());
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
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
			</project>
			""")
	void dependencyVersionResolvedViaProperty(PsiFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("commons-lang3")
				.hasVersion("3.19.0")
				.hasPropertyVersion("commons.version");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
					</dependency>
				</dependencies>
			</project>
			""")
	void dependenciesWithoutVersionAreNotUpdateCandidates(PsiFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector).isEmpty();
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project>
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
			</project>
			""")
	void pluginWithInlineVersionIsDiscovered(PsiFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("org.apache.maven.plugins", "maven-compiler-plugin")
				.hasVersion("3.14.0")
				.hasDeclaration(DeclarationSource.plugin());
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project>
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
			</project>
			""")
	void parsePropertiesCollectsProjectAndProfileProperties(PsiFile file) {

		Map<String, String> props = MavenParser.getProperties((XmlFile) file);

		assertThat(props).containsEntry("root.prop", "root-value").containsEntry("profile.prop", "profile-value");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project>
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
			</project>
			""")
	void singlePomFullDiscovery(PsiFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector).hasDependencyUsage("commons-lang3");
		assertThat(collector)
				.hasDependencyUsage("spring-modulith-bom")
				.hasVersion("2.0.4")
				.hasPropertyVersion("bom.version");
		assertThat(collector).hasDependencyUsage("org.apache.maven.plugins", "maven-surefire-plugin");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>parent</artifactId>
				<version>1.0.0</version>
				<properties>
					<junit.version>5.11.0</junit.version>
				</properties>
			</project>
			""")
	@ProjectFile(name = "module/pom.xml", content = """
				<project>
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
				</project>
			""")
	void multiModuleParentThenChildResolvesVersionFromParentProperty(XmlFile parent, XmlFile child) {

		Cache cache = new Cache();
		DependencyCollector propertyCollector = new DependencyCollector();
		MavenParser parser = new MavenParser(propertyCollector, new HashMap<>());
		parser.parsePomFile(cache, child);
		cache.getProject(ProjectId.of("com.example", "module")).setProperties(propertyCollector);

		DependencyCollector collector = new DependencyCollector();
		parser = new MavenParser(collector, new HashMap<>());
		parser.parsePomFile(cache, parent);

		assertThat(collector)
				.hasDependencyUsage("junit-jupiter")
				.hasVersion("5.11.0")
				.hasPropertyVersion("junit.version");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
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
			</project>
			""")
	@ProjectFile(name = "module/pom.xml", content = """
			<project>
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
			</project>
			""")
	void multiModuleChildAddsDependenciesAlongsideParent(XmlFile parent, XmlFile child) {

		DependencyCollector collector = new DependencyCollector();
		MavenParser parser = new MavenParser(collector, new HashMap<>());
		parser.parsePomFile(new Cache(), parent);
		parser.parsePomFile(new Cache(), child);

		assertThat(collector).hasDependencyUsage("assertj-core");
		assertThat(collector).hasDependencyUsage("commons-lang3");
	}

}
