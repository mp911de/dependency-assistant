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

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for highlighting Maven pom.xml.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenHighlightingTests {

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
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void directDependencyInlineVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
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
							<groupId>org.springframework.modulith</groupId>
							<artifactId>spring-modulith-bom</artifactId>
							<version>2.0.4</version>
							<type>pom</type>
							<scope>import</scope>
						</dependency>
					</dependencies>
				</dependencyManagement>
			</project>
			""")
	void managedDependencyInlineVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "2.0.5");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<parent>
					<groupId>org.junit</groupId>
					<artifactId>junit-bom</artifactId>
					<version>6.0.0</version>
				</parent>
				<groupId>foo</groupId>
				<artifactId>module</artifactId>
			</project>
			""")
	void parentVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<parent>
					<groupId>org.junit</groupId>
					<artifactId>junit-bom</artifactId>
					<version>6.0.0</version>
				</parent>
				<artifactId>module</artifactId>
			</project>
			""")
	void ignoresInheritedParent(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		assertThat(fixture).hasNoGutterMarks();
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
	void dependencyWithoutVersionHasNoGutter(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		assertThat(fixture).hasNoGutterMarks();
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<build>
					<extensions>
						<extension>
							<groupId>org.junit</groupId>
							<artifactId>junit-bom</artifactId>
							<version>6.0.0</version>
						</extension>
					</extensions>
				</build>
			</project>
			""")
	void buildExtensionInlineVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<reporting>
					<plugins>
						<plugin>
							<groupId>org.springframework.modulith</groupId>
							<artifactId>spring-modulith-bom</artifactId>
							<version>2.0.4</version>
						</plugin>
					</plugins>
				</reporting>
			</project>
			""")
	void reportingPluginInlineVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "2.0.5");
	}

}
