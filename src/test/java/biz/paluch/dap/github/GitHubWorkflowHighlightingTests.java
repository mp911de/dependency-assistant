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

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for highlighting GitHub Actions workflow files.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GitHubWorkflowHighlightingTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GitHubFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.1.0
			""")
	void suggestsVersionUpgrade(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		assertThat(workflowFile).hasSingleGutter().tooltipContains("Minor", "4.2.0").hasNoNavigation();
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.2.0
			""")
	void noGutterForCurrentVersion(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		assertThat(workflowFile).hasNoGutterMarks();
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v3.6.0
			""")
	void suggestsMajorUpgrade(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		assertThat(workflowFile).hasSingleGutterContaining("Major", "4.2.0");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: ./.github/actions/local-action
			      - uses: ../shared/foo
			""")
	void ignoresLocalUsesDeclarations(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		assertThat(workflowFile).hasNoGutterMarks();
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v3.6.0
			""")
	void highlightRangeCoversOnlyTheRefSegment(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		YAMLScalar usesScalar = findFirstUsesScalar(workflowFile);
		assertThat(usesScalar).isNotNull();

		TextRange refRange = GitHubUtils.getVersionRange(usesScalar);
		String fileText = workflowFile.getText();
		String scalarText = usesScalar.getText();
		int atIndex = scalarText.indexOf('@');

		assertThat(refRange.getStartOffset())
				.isEqualTo(usesScalar.getTextRange().getStartOffset() + atIndex + 1);
		assertThat(refRange.getEndOffset()).isEqualTo(usesScalar.getTextRange().getEndOffset());
		assertThat(fileText.substring(refRange.getStartOffset(), refRange.getEndOffset())).isEqualTo("v3.6.0");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1
			""")
	void highlightsShaStyleRefForUpgrade(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		assertThat(workflowFile).hasSingleGutterContaining("4.2.0");
	}

	private static YAMLScalar findFirstUsesScalar(PsiFile file) {

		for (PsiElement element : SyntaxTraverser.psiTraverser(file)) {
			if (element instanceof YAMLKeyValue keyValue && "uses".equals(keyValue.getKeyText())
					&& keyValue.getValue() instanceof YAMLScalar scalar) {
				return scalar;
			}
		}
		return null;
	}

}
