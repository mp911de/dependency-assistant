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

import java.util.Arrays;
import java.util.List;

import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.DependencyVersionDriftInspection;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.fixtures.Inspections;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Highlighting and quick-fix integration tests for
 * {@link DependencyVersionDriftInspection}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class DependencyVersionDriftInspectionTests {

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
						<groupId>hello.world</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void highlightsDriftingVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		Inspections.registerDependency(fixture.getProject(), "other", "hello.world", "drift-bom", "6.0.3");

		List<ProblemDescriptor> problems = Inspections.inspect(fixture.getProject(), pomFile);

		assertThat(problems).hasSize(1);

		ProblemDescriptor problem = problems.get(0);
		assertThat(problem.getHighlightType()).isEqualTo(ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
		assertThat(problem.getDescriptionTemplate()).contains("drift-bom").contains("6.0.3");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>hello.world</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void alignToHighestRewritesToHighestDeclaredVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		Inspections.registerDependency(fixture.getProject(), "other", "hello.world", "drift-bom", "6.0.3");

		applyFix(pomFile, "Upgrade to highest used version '6.0.3'");

		assertThat(pomFile).containsText("<version>6.0.3</version>");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>hello.world</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void doesNotFlagWhenModulesAgree(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		Inspections.registerDependency(fixture.getProject(), "other", "hello.world", "drift-bom", "6.0.0");

		assertThat(Inspections.inspect(fixture.getProject(), pomFile)).isEmpty();
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>hello.world</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void highlightsDeclarationDriftWhenInlineAndPropertyDeclarationsAgree(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		Inspections.registerDependency(fixture.getProject(), "other", "hello.world", "drift-bom", "6.0.0",
				VersionSource.property("drift.version"));

		List<ProblemDescriptor> problems = Inspections.inspect(fixture.getProject(), pomFile);

		assertThat(problems).extracting(CommonProblemDescriptor::getDescriptionTemplate)
				.singleElement().asString().contains("drift-bom").contains("declaration drift");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>hello.world</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.3</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void alignToLowestDowngradesToLowestDeclaredVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		Inspections.registerDependency(fixture.getProject(), "other", "hello.world", "drift-bom", "6.0.0");

		applyFix(pomFile, "Downgrade to lowest used version '6.0.0'");

		assertThat(pomFile).containsText("<version>6.0.0</version>");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>hello.world</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.3</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void offersOnlyFixesThatChangeTheCurrentVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		Inspections.registerDependency(fixture.getProject(), "other", "hello.world", "drift-bom", "6.0.0");

		List<ProblemDescriptor> problems = Inspections.inspect(fixture.getProject(), pomFile);

		assertThat(problems).flatMap(it -> List.of(it.getFixes()))
				.extracting(QuickFix::getName).singleElement()
				.isEqualTo("Downgrade to lowest used version '6.0.0'");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>hello.world</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void clearsDriftAfterAligningCurrentFile(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		Inspections.registerDependency(fixture.getProject(), "other", "hello.world", "drift-bom", "6.0.3");

		applyFix(pomFile, "Upgrade to highest used version '6.0.3'");

		// the current file's runtime state is still stale at 6.0.0; the inspection
		// must read the open file live and no longer report drift
		assertThat(pomFile).containsText("<version>6.0.3</version>");
		assertThat(Inspections.inspect(fixture.getProject(), pomFile)).isEmpty();
	}

	private void applyFix(PsiFile file, String name) {

		ProblemDescriptor problem = Inspections.inspect(fixture.getProject(), file).get(0);
		assertThat(problem.getFixes()).extracting(QuickFix::getName).contains(name);
		LocalQuickFix fix = (LocalQuickFix) Arrays.stream(problem.getFixes())
				.filter(it -> name.equals(it.getName())).findFirst().orElseThrow();
		fix.applyFix(fixture.getProject(), problem);
	}

}
