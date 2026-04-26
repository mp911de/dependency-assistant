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
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.support.UpdatedBuildFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;
import static biz.paluch.dap.maven.UpdateTestSupport.*;

/**
 * PSI-level integration tests for {@link UpdatePomFile}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class UpdatePomFileTests {

	private @TestFixture CodeInsightTestFixture fixture;

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

		UpdatedBuildFile updated = applyUpdate(pom, "org.apache.commons", "commons-lang3", "3.19.0", "3.20.0");

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

		UpdatedBuildFile updated = applyUpdate(pom, "org.springframework.boot", "spring-boot-dependencies", "3.5.0",
				DeclarationSource.managed(), VersionSource.declared("3.5.0"), "3.5.1");

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

		UpdatedBuildFile updated = applyUpdate(pom, "org.springframework", "spring-core", "6.1.0",
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

		UpdatedBuildFile updated = applyUpdate(pom, "org.apache.maven.plugins", "maven-compiler-plugin", "3.13.0",
				DeclarationSource.plugin(), VersionSource.declared("3.13.0"), "3.14.0");

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

		UpdatedBuildFile updated = applyUpdate(pom, "org.springframework", "spring-core", "6.1.0",
				DeclarationSource.profileDependency("dev"), VersionSource.profileProperty("dev", "spring.version"),
				"6.2.0");

		assertThat(updated).hasProperty("spring.version", "6.2.0");
	}

}
