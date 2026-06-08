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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.DependencyVersionDriftInspection;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
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
						<groupId>biz.paluch.drift</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void highlightsDriftingVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		storeModule("other", "biz.paluch.drift", "drift-bom", "6.0.3");

		List<ProblemDescriptor> problems = inspect(pomFile);

		assertThat(problems).singleElement().satisfies(problem -> {
			assertThat(problem.getHighlightType()).isEqualTo(ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
			assertThat(problem.getDescriptionTemplate()).contains("drift-bom")
					.contains("6.0.3");
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
						<groupId>biz.paluch.drift</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void alignToHighestRewritesToHighestDeclaredVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		storeModule("other", "biz.paluch.drift", "drift-bom", "6.0.3");

		applyFix(pomFile, "Update to highest used version '6.0.3'");

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
						<groupId>biz.paluch.drift</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void doesNotFlagWhenModulesAgree(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		storeModule("other", "biz.paluch.drift", "drift-bom", "6.0.0");

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
						<groupId>biz.paluch.drift</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.3</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void alignToLowestDowngradesToLowestDeclaredVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		storeModule("other", "biz.paluch.drift", "drift-bom", "6.0.0");

		applyFix(pomFile, "Downgrade to lowest used version '6.0.0'");

		assertThat(pomFile).containsText("<version>6.0.0</version>");
	}

	private void applyFix(PsiFile file, String name) {

		ProblemDescriptor problem = inspect(file).get(0);
		assertThat(problem.getFixes()).extracting(QuickFix::getName).contains(name);
		LocalQuickFix fix = (LocalQuickFix) Arrays.stream(problem.getFixes())
				.filter(it -> name.equals(it.getName())).findFirst().orElseThrow();
		fix.applyFix(fixture.getProject(), problem);
	}

	private void storeModule(String moduleId, String groupId, String artifactId, String version) {

		DependencyCollector collector = new DependencyCollector();
		collector.registerUsage(ArtifactId.of(groupId, artifactId), ArtifactVersion.of(version),
				DeclarationSource.dependency(), VersionSource.declared(version));
		StateService.getInstance(fixture.getProject())
				.getProjectState(ProjectId.of("com.example", moduleId))
				.setDependencies(collector);
	}

	private List<ProblemDescriptor> inspect(PsiFile file) {

		DependencyVersionDriftInspection inspection = new DependencyVersionDriftInspection();
		return ReadAction.compute(() -> {
			InspectionManager manager = InspectionManager.getInstance(fixture.getProject());
			ProblemsHolder holder = new ProblemsHolder(manager, file, true);
			PsiElementVisitor visitor = inspection.buildVisitor(holder, true);
			SyntaxTraverser.psiTraverser(file).forEach(visitor::visitElement);
			return holder.getResults();
		});
	}

}
