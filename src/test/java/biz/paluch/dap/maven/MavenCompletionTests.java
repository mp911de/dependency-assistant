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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.fixtures.Releases;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for Maven version completion.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenCompletionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>6.0.<caret></version>
					</dependency>
				</dependencies>
			</project>
			""")
	void completesInlineVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("6.0.3");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version><caret>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void ordersReleaseCompletionsNewestFirst(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		fixture.completeBasic();
		List<String> releases = List.of("6.1.0-M1", "6.0.3", "6.0.2", "5.14.3", "5.14.2", "5.14.1",
				"5.14.0");
		List<String> actual = fixture.getLookupElementStrings().stream().filter(releases::contains).toList();

		assertThat(actual).containsExactlyElementsOf(releases);
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>6.<caret>0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void completesInlineVersionWithinWord(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(pomFile).containsText("<version>6.1.0-M1</version>");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<properties>
					<junit>6.0.<caret></junit>
				</properties>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>${junit}</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void completesPropertyVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("6.0.3");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<properties>
					<junit>6.<caret>0.0</junit>
				</properties>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>${junit}</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void completesPropertyVersionWithinWord(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(pomFile).containsText("<junit>6.1.0-M1</junit>");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>5.14<caret></version>
					</dependency>
				</dependencies>
			</project>
			""")
	void completesVersionInOrder(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		ProjectState projectState = StateService.getInstance(pomFile.getProject())
				.getProjectState(MavenFixtures.PROJECT_ID);
		DependencyCollector collector = new DependencyCollector();
		collector.registerUsage(Releases.JUNIT_BOM.toArtifactId(), ArtifactVersion.of("5.14.1"),
				DeclarationSource.dependency(), VersionSource.declared("5.14.1"));
		projectState.setDependencies(collector);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("5.14.1");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>org.springframework.modulith</groupId>
							<artifactId>spring-modulith-bom</artifactId>
							<version>2.0.<caret></version>
							<type>pom</type>
							<scope>import</scope>
						</dependency>
					</dependencies>
				</dependencyManagement>
			</project>
			""")
	void completesInlineManagedDependencyVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("2.0.5");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<build>
					<extensions>
						<extension>
							<groupId>org.junit</groupId>
							<artifactId>junit-bom</artifactId>
							<version>6.0.<caret></version>
						</extension>
					</extensions>
				</build>
			</project>
			""")
	void completesBuildExtensionVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("6.0.3");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<reporting>
					<plugins>
						<plugin>
							<groupId>org.springframework.modulith</groupId>
							<artifactId>spring-modulith-bom</artifactId>
							<version>2.0.<caret></version>
						</plugin>
					</plugins>
				</reporting>
			</project>
			""")
	void completesReportingPluginVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("2.0.5");
	}

}
