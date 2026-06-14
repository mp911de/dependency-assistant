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

package biz.paluch.dap.gradle;

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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Highlighting integration tests for {@link DependencyVersionDriftInspection}
 * on Gradle version catalogs.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class TomlVersionDriftInspectionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			jooqNew = "3.21.2"

			[libraries]
			jooq = { module = "org.jooq:jooq", version = "3.14.16" }
			jooqLatest = { module = "org.jooq:jooq", version.ref = "jooqNew" }
			""")
	void reportsDriftOnTheAliasNotTheSharedVersionsEntry(PsiFile toml) {

		GradleFixtures.analyze(toml);
		storeModule("other", "org.jooq", "jooq", "3.99.0");

		List<String> highlighted = inspect(toml).stream()
				.map(problem -> problem.getPsiElement().getText())
				.toList();

		assertThat(highlighted).containsExactlyInAnyOrder("\"3.14.16\"", "\"jooqNew\"");
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
