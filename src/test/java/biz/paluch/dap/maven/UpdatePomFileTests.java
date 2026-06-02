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

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assertions.UpdatedBuildFile;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;
import static biz.paluch.dap.maven.UpdateTestSupport.*;

/**
 * PSI-level integration tests for {@link UpdatePomFile}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdatePomFileTests {

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
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
				</dependencies>
			</project>
			""")
	void dependencyInlineVersionIsUpdated(PsiFile pom) {

		UpdatedBuildFile updated = applyUpdate(pom, "org.apache.commons", "commons-lang3", "3.20.0");

		assertThat(updated).hasDependency("commons-lang3", "3.20.0");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
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
	void dependencyManagementInlineVersionIsUpdated(PsiFile pom) {

		UpdatedBuildFile updated = applyUpdate(pom, "org.springframework.boot", "spring-boot-dependencies",
				DeclarationSource.managed(), "3.5.1");

		assertThat(updated).hasDependency("spring-boot-dependencies", "3.5.1");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<properties>
					<spring.version>6.1.0</spring.version>
				</properties>
				<dependencies>
					<dependency>
						<groupId>org.springframework</groupId>
						<artifactId>spring-core</artifactId>
						<version>${spring.version}</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void propertyVersionIsUpdated(PsiFile pom) {

		UpdatedBuildFile updated = applyUpdate(pom, "org.springframework", "spring-core",
				DeclarationSource.dependency(), VersionSource.property("spring.version"), "6.2.0");

		assertThat(updated).hasProperty("spring.version", "6.2.0");
	}

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
							<groupId>org.apache.maven.plugins</groupId>
							<artifactId>maven-compiler-plugin</artifactId>
							<version>3.13.0</version>
						</plugin>
					</plugins>
				</build>
			</project>
			""")
	void pluginInlineVersionIsUpdated(PsiFile pom) {

		UpdatedBuildFile updated = applyUpdate(pom, "org.apache.maven.plugins", "maven-compiler-plugin",
				DeclarationSource.plugin(), "3.14.0");

		assertThat(updated).hasDependency("maven-compiler-plugin", "3.14.0");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<profiles>
					<profile>
						<id>dev</id>
						<properties>
							<spring.version>6.1.0</spring.version>
						</properties>
						<dependencies>
							<dependency>
								<groupId>org.springframework</groupId>
								<artifactId>spring-core</artifactId>
								<version>${spring.version}</version>
							</dependency>
						</dependencies>
					</profile>
				</profiles>
			</project>
			""")
	void profilePropertyVersionIsUpdated(PsiFile pom) {

		UpdatedBuildFile updated = applyUpdate(pom, "org.springframework", "spring-core",
				DeclarationSource.profileDependency("dev"), VersionSource.profileProperty("dev", "spring.version"),
				"6.2.0");

		assertThat(updated).hasProperty("spring.version", "6.2.0");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
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
	void buildExtensionInlineVersionIsUpdated(PsiFile pom) {

		UpdatedBuildFile updated = applyUpdate(pom, "org.springframework.build", "aws-maven",
				DeclarationSource.plugin(), "5.0.1");

		assertThat(updated).hasDependency("aws-maven", "5.0.1");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<reporting>
					<plugins>
						<plugin>
							<groupId>org.apache.maven.plugins</groupId>
							<artifactId>maven-javadoc-plugin</artifactId>
							<version>3.11.1</version>
						</plugin>
					</plugins>
				</reporting>
			</project>
			""")
	void reportingPluginInlineVersionIsUpdated(PsiFile pom) {

		UpdatedBuildFile updated = applyUpdate(pom, "org.apache.maven.plugins", "maven-javadoc-plugin",
				DeclarationSource.plugin(), "3.11.2");

		assertThat(updated).hasDependency("maven-javadoc-plugin", "3.11.2");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
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
	void profileBuildExtensionInlineVersionIsUpdated(PsiFile pom) {

		UpdatedBuildFile updated = applyUpdate(pom, "org.springframework.build", "aws-maven",
				DeclarationSource.profilePlugin("release"), "5.0.1");

		assertThat(updated).hasDependency("aws-maven", "5.0.1");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
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
									<groupId>org.apache.maven.plugins</groupId>
									<artifactId>maven-javadoc-plugin</artifactId>
									<version>3.11.1</version>
								</plugin>
							</plugins>
						</reporting>
					</profile>
				</profiles>
			</project>
			""")
	void profileReportingPluginInlineVersionIsUpdated(PsiFile pom) {

		UpdatedBuildFile updated = applyUpdate(pom, "org.apache.maven.plugins", "maven-javadoc-plugin",
				DeclarationSource.profilePlugin("docs"), "3.11.2");

		assertThat(updated).hasDependency("maven-javadoc-plugin", "3.11.2");
	}

}
