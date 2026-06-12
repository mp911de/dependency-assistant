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

package biz.paluch.dap.assistant;

import java.util.List;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.maven.MavenFixtures;
import biz.paluch.dap.rule.DependencyRules;
import biz.paluch.dap.rule.DependencyfileService;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Highlighting integration tests for {@link DependencyRuleInspection}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class DependencyRuleInspectionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(fixture.getProject());
	}

	@AfterEach
	void tearDown() {
		DependencyfileService.getInstance(fixture.getProject()).setRules(null);
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.springframework</groupId>
						<artifactId>spring-core</artifactId>
						<version>7.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void flagsVersionThatDriftsFromGeneration(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		install(DependencyRules.builder().artifact("org.springframework:*", "6.0")
				.build());

		assertThat(inspect(pomFile)).singleElement().satisfies(problem -> {
			assertThat(problem.getHighlightType()).isEqualTo(ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
			assertThat(problem.getDescriptionTemplate()).contains("spring-core")
					.contains("6.0");
		});
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.springframework</groupId>
						<artifactId>spring-core</artifactId>
						<version>6.0.5</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void doesNotFlagVersionThatCompliesWithGeneration(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		install(DependencyRules.builder().artifact("org.springframework:*", "6.0")
				.build());

		assertThat(inspect(pomFile)).isEmpty();
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
						<version>5.9.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void doesNotFlagUngovernedArtifact(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		install(DependencyRules.builder().artifact("org.springframework:*", "6.0")
				.build());

		assertThat(inspect(pomFile)).isEmpty();
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.springframework</groupId>
						<artifactId>spring-core</artifactId>
						<version>7.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void doesNotFlagWhenNoRulesArePresent(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		assertThat(inspect(pomFile)).isEmpty();
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>2.1.0</version>
				<dependencies>
					<dependency>
						<groupId>org.springframework</groupId>
						<artifactId>spring-core</artifactId>
						<version>7.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void selectsBranchRuleByProjectVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		install(DependencyRules.builder()
				.artifact("org.springframework:*", "7.0")
				.branch("2.*", branch -> branch.artifact("org.springframework:*", "5.0"))
				.build());

		assertThat(inspect(pomFile)).singleElement()
				.satisfies(problem -> assertThat(problem.getDescriptionTemplate())
						.contains("spring-core").contains("5.0"));
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
						<version>5.14.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void offersFixWhenCompliantReleaseCached(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		install(DependencyRules.builder().artifact("org.junit:*", "6.0").build());

		assertThat(inspect(pomFile)).singleElement()
				.satisfies(problem -> assertThat(problem.getFixes()).hasSize(1));
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
						<version>5.14.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void appliesFixToNewestCompliantRelease(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		install(DependencyRules.builder().artifact("org.junit:*", "6.0").build());

		applyFix(inspect(pomFile).getFirst());

		assertThat(pomFile).containsText("6.0.3").doesNotContainText("5.14.0");
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
						<version>5.14.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void omitsFixWhenNoCompliantReleaseCached(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		install(DependencyRules.builder().artifact("org.junit:*", "9.9").build());

		assertThat(inspect(pomFile)).singleElement()
				.satisfies(problem -> assertThat(problem.getFixes()).isNullOrEmpty());
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>io.vavr</groupId>
						<artifactId>vavr</artifactId>
						<version>0.11.0</version>
					</dependency>
					<dependency>
						<groupId>io.vavr</groupId>
						<artifactId>vavr-match</artifactId>
						<version>0.11.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void sharesFamilyNameAcrossProblemsForBatching(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		install(DependencyRules.builder().artifact("io.vavr:*", "1.0").build());

		List<ProblemDescriptor> problems = inspect(pomFile);
		assertThat(problems).hasSize(2);
		assertThat(problems.get(0).getFixes()[0].getFamilyName())
				.isEqualTo("Align dependency with required generation")
				.isEqualTo(problems.get(1).getFixes()[0].getFamilyName());
	}

	private void install(DependencyRules rules) {
		DependencyfileService.getInstance(fixture.getProject()).setRules(rules);
	}

	private void applyFix(ProblemDescriptor problem) {

		LocalQuickFix fix = (LocalQuickFix) problem.getFixes()[0];
		WriteCommandAction.runWriteCommandAction(fixture.getProject(),
				() -> fix.applyFix(fixture.getProject(), problem));
	}

	private List<ProblemDescriptor> inspect(PsiFile file) {

		DependencyRuleInspection inspection = new DependencyRuleInspection();
		return ReadAction.compute(() -> {
			InspectionManager manager = InspectionManager.getInstance(fixture.getProject());
			ProblemsHolder holder = new ProblemsHolder(manager, file, true);
			PsiElementVisitor visitor = inspection.buildVisitor(holder, true);
			SyntaxTraverser.psiTraverser(file).forEach(visitor::visitElement);
			return holder.getResults();
		});
	}

}
