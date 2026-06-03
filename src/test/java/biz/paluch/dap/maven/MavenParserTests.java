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

import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectId;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link MavenParser}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class MavenParserTests {

	private @TestFixture Project project;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(project);
	}

	// -------------------------------------------------------------------------
	// Dependencies
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "pom.xml", content = """
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
	void directDependenciesWithInlineVersionsAreDiscovered(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("commons-lang3")
				.hasVersion("3.19.0")
				.hasDeclaration(DeclarationSource.dependency());
		assertThat(collector).hasDependencyUsage("junit-jupiter");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
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
	void managedDependencyWithInlineVersionIsDiscovered(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("spring-boot-dependencies")
				.hasVersion("3.5.0")
				.hasDeclaration(DeclarationSource.managed());
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<profiles>
					<profile>
						<id>foo</id>
						<dependencies>
							<dependency>
								<groupId>org.springframework.boot</groupId>
								<artifactId>spring-boot-dependencies</artifactId>
								<version>3.5.0</version>
							</dependency>
						</dependencies>
					</profile>
				</profiles>
			</project>
			""")
	void parsesProfileDependency(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("spring-boot-dependencies")
				.hasVersion("3.5.0")
				.hasDeclaration(DeclarationSource.profileDependency("foo"));
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
		MavenParser parser = new MavenParser(collector);
		parser.parsePomFile(new Cache(), parent);
		parser.parsePomFile(new Cache(), child);

		assertThat(collector).hasDependencyUsage("assertj-core");
		assertThat(collector).hasDependencyUsage("commons-lang3");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<parent>
					<groupId>org.junit.jupiter</groupId>
					<artifactId>junit-jupiter</artifactId>
					<version>5.11.0</version>
				</parent>
				<groupId>foo</groupId>
				<artifactId>module</artifactId>
			</project>
			""")
	void shouldParseParent(XmlFile pomFile) {

		DependencyCollector collector = new DependencyCollector();
		MavenParser parser = new MavenParser(collector);
		parser.parsePomFile(new Cache(), pomFile);

		assertThat(collector)
				.hasDependencyUsage("junit-jupiter")
				.hasVersion("5.11.0");
	}

	// -------------------------------------------------------------------------
	// Plugins
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "pom.xml", content = """
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
	void pluginWithInlineVersionIsDiscovered(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("org.apache.maven.plugins", "maven-compiler-plugin")
				.hasVersion("3.14.0")
				.hasDeclaration(DeclarationSource.plugin());
	}

	// -------------------------------------------------------------------------
	// Build extensions / Reporting plugins
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<build>
					<extensions>
						<extension>
							<groupId>org.springframework.build</groupId>
							<artifactId>aws-maven</artifactId>
							<version>5.0.0</version>
						</extension>
					</extensions>
				</build>
			</project>
			""")
	void buildExtensionWithInlineVersionIsDiscovered(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("org.springframework.build", "aws-maven")
				.hasVersion("5.0.0")
				.hasDeclaration(DeclarationSource.plugin());
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<properties>
					<extension.version>5.0.0</extension.version>
				</properties>
				<build>
					<extensions>
						<extension>
							<groupId>org.springframework.build</groupId>
							<artifactId>aws-maven</artifactId>
							<version>${extension.version}</version>
						</extension>
					</extensions>
				</build>
			</project>
			""")
	void buildExtensionVersionResolvedViaProperty(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("org.springframework.build", "aws-maven")
				.hasVersion("5.0.0")
				.hasPropertyVersion("extension.version");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<build>
					<extensions>
						<extension>
							<artifactId>aws-maven</artifactId>
							<version>5.0.0</version>
						</extension>
					</extensions>
				</build>
			</project>
			""")
	void buildExtensionWithoutGroupIdIsSkipped(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector).hasNoDependencyUsage("aws-maven");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<reporting>
					<plugins>
						<plugin>
							<groupId>org.apache.maven.plugins</groupId>
							<artifactId>maven-javadoc-plugin</artifactId>
							<version>3.11.2</version>
						</plugin>
					</plugins>
				</reporting>
			</project>
			""")
	void reportingPluginWithInlineVersionIsDiscovered(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("org.apache.maven.plugins", "maven-javadoc-plugin")
				.hasVersion("3.11.2")
				.hasDeclaration(DeclarationSource.plugin());
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<profiles>
					<profile>
						<id>release</id>
						<build>
							<extensions>
								<extension>
									<groupId>org.springframework.build</groupId>
									<artifactId>aws-maven</artifactId>
									<version>5.0.0</version>
								</extension>
							</extensions>
						</build>
					</profile>
				</profiles>
			</project>
			""")
	void profileBuildExtensionIsDiscovered(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("org.springframework.build", "aws-maven")
				.hasVersion("5.0.0")
				.hasDeclaration(DeclarationSource.profilePlugin("release"));
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<profiles>
					<profile>
						<id>docs</id>
						<reporting>
							<plugins>
								<plugin>
									<artifactId>maven-javadoc-plugin</artifactId>
									<version>3.11.2</version>
								</plugin>
							</plugins>
						</reporting>
					</profile>
				</profiles>
			</project>
			""")
	void profileReportingPluginIsDiscovered(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("org.apache.maven.plugins", "maven-javadoc-plugin")
				.hasVersion("3.11.2")
				.hasDeclaration(DeclarationSource.profilePlugin("docs"));
	}

	// -------------------------------------------------------------------------
	// Repositories
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<repositories>
					<repository>
						<id>central</id>
						<name>Maven Central</name>
						<url>https://repo.maven.apache.org/maven2</url>
					</repository>
				</repositories>
			</project>
			""")
	void parsesTopLevelRepository(XmlFile file) {

		List<MavenRemoteRepository> repositories = MavenParser.parseRepositories(file);

		assertThat(repositories).extracting(MavenRemoteRepository::getId, MavenRemoteRepository::getUrl)
				.containsExactly(tuple("central", "https://repo.maven.apache.org/maven2"));
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<pluginRepositories>
					<pluginRepository>
						<id>plugins</id>
						<url>https://plugins.example.com/maven2</url>
					</pluginRepository>
				</pluginRepositories>
			</project>
			""")
	void parsesTopLevelPluginRepository(XmlFile file) {

		List<MavenRemoteRepository> repositories = MavenParser.parseRepositories(file);

		assertThat(repositories).extracting(MavenRemoteRepository::getId, MavenRemoteRepository::getUrl)
				.containsExactly(tuple("plugins", "https://plugins.example.com/maven2"));
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<profiles>
					<profile>
						<id>release</id>
						<repositories>
							<repository>
								<id>profile-repo</id>
								<url>https://profile.example.com/maven2</url>
							</repository>
						</repositories>
						<pluginRepositories>
							<pluginRepository>
								<id>profile-plugins</id>
								<url>https://profile-plugins.example.com/maven2</url>
							</pluginRepository>
						</pluginRepositories>
					</profile>
				</profiles>
			</project>
			""")
	void parsesProfileRepositories(XmlFile file) {

		List<MavenRemoteRepository> repositories = MavenParser.parseRepositories(file);

		assertThat(repositories).extracting(MavenRemoteRepository::getId, MavenRemoteRepository::getUrl)
				.containsExactly(tuple("profile-repo", "https://profile.example.com/maven2"),
						tuple("profile-plugins", "https://profile-plugins.example.com/maven2"));
	}

	// -------------------------------------------------------------------------
	// Properties
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "pom.xml", content = """
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
	void dependencyVersionResolvedViaProperty(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector)
				.hasDependencyUsage("commons-lang3")
				.hasVersion("3.19.0")
				.hasPropertyVersion("commons.version");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
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
	void parsePropertiesCollectsProjectAndProfileProperties(XmlFile file) {

		Map<String, String> props = MavenParser.getProperties(file);

		assertThat(props).containsEntry("root.prop", "root-value").containsEntry("profile.prop", "profile-value");
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
		MavenParser parser = new MavenParser(propertyCollector);
		parser.parsePomFile(cache, child);
		cache.getProject(ProjectId.of("com.example", "module")).setProperties(propertyCollector);

		DependencyCollector collector = new DependencyCollector();
		parser = new MavenParser(collector);
		parser.parsePomFile(cache, parent);

		assertThat(collector)
				.hasDependencyUsage("junit-jupiter")
				.hasVersion("5.11.0")
				.hasPropertyVersion("junit.version");
	}

	// -------------------------------------------------------------------------
	// Declaration-only
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "pom.xml", content = """
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
	void dependenciesWithoutVersionAreNotUpdateCandidates(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		// TODO: Record as usage for upgrade suggestions in the parent
		assertThat(collector).isEmpty();
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
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
	void singlePomFullDiscovery(XmlFile file) {

		DependencyCollector collector = MavenFixtures.analyze(file);

		assertThat(collector).hasDependencyUsage("commons-lang3");
		assertThat(collector)
				.hasDependencyUsage("spring-modulith-bom")
				.hasVersion("2.0.4")
				.hasPropertyVersion("bom.version");
		assertThat(collector).hasDependencyUsage("org.apache.maven.plugins", "maven-surefire-plugin");
	}

}
