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

package biz.paluch.dap.github;

import java.util.List;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.fixtures.Inspections;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Highlighting and quick-fix integration tests for
 * {@link UnpinnedGitHubActionInspection}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class UnpinnedGitHubActionInspectionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GitHubFixtures.setup(fixture.getProject());
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.2.0
			""")
	void flagsSymbolicRefWithCachedShaOnTheRefToken(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		assertThat(inspect(workflowFile)).singleElement().satisfies(problem -> {
			assertThat(problem.getDescriptionTemplate()).isEqualTo("Unpinned GitHub Action reference");
			assertThat(problem.getHighlightType()).isEqualTo(ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
			assertThat(highlightText(workflowFile, problem)).isEqualTo("v4.2.0");
			assertThat(problem.getFixes()).extracting(QuickFix::getName)
					.containsExactly("Pin to commit SHA " + GitHubFixtures.SHA_V4.substring(0, 8) + "…");
			assertThat(problem.getFixes()).extracting(QuickFix::getFamilyName).containsExactly("Pin to commit SHA");
		});
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.2.0
			""")
	void pinsSymbolicRefToCommitShaWithVersionComment(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		applyFix(workflowFile);

		assertThat(workflowFile)
				.containsText("uses: actions/checkout@" + GitHubFixtures.SHA_V4 + " # v4.2.0")
				.doesNotContainText("@v4.2.0");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4<caret>.2.0
			""")
	void pinFixLeavesCaretAfterShaNotInsideComment(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		applyFixInEditor(workflowFile);

		assertThat(workflowFile)
				.containsText("actions/checkout@" + GitHubFixtures.SHA_V4 + " # v4.2.0")
				.caretAfter("actions/checkout@" + GitHubFixtures.SHA_V4);
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@d1185ce59f7757407fe6a5febb1e03e3dba2a530
			""")
	void doesNotFlagShaPinnedRef(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		assertThat(inspect(workflowFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.1.0
			""")
	void doesNotFlagSymbolicRefWithoutCachedSha(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		assertThat(inspect(workflowFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@main
			""")
	void doesNotFlagBranchRef(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		assertThat(inspect(workflowFile)).isEmpty();
	}

	private List<ProblemDescriptor> inspect(PsiFile file) {
		return Inspections.inspect(fixture.getProject(), file, new UnpinnedGitHubActionInspection());
	}

	private void applyFix(PsiFile file) {

		ProblemDescriptor problem = inspect(file).getFirst();
		LocalQuickFix fix = (LocalQuickFix) problem.getFixes()[0];
		WriteCommandAction.runWriteCommandAction(fixture.getProject(),
				() -> fix.applyFix(fixture.getProject(), problem));
	}

	private void applyFixInEditor(PsiFile file) {

		ProblemDescriptor problem = inspect(file).getFirst();
		ModCommandQuickFix fix = (ModCommandQuickFix) problem.getFixes()[0];
		Editor editor = fixture.getEditor();
		ModCommand command = ReadAction.compute(() -> fix.perform(fixture.getProject(), problem));
		WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> ModCommandExecutor.getInstance()
				.executeInteractively(ActionContext.from(editor, file), command, editor));
	}

	private static String highlightText(PsiFile file, ProblemDescriptor problem) {
		return ReadAction.compute(() -> {
			TextRange inElement = problem.getTextRangeInElement();
			int base = problem.getPsiElement().getTextRange().getStartOffset();
			return file.getText().substring(base + inElement.getStartOffset(), base + inElement.getEndOffset());
		});
	}

}
