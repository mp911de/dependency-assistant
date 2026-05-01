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
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for GitHub Actions workflow completion.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GitHubWorkflowCompletionTests {

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
			      - uses: actions/checkout@4.1.0
			""")
	void completesVersionRefsWithoutVPrefix(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);
		openAtCaretAfter(workflowFile, "@");

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("4.2.0", "4.1.0", "3.6.0");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1
			""")
	void completesShaRefsWithFullSha(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);
		openAtCaretAfter(workflowFile, "@");

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("4.2.0", "4.1.0", "3.6.0");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@7b4f3880ef3a2616<caret>e5c519a35b7a4f07f7b3b2a1
			""")
	void completesShaRefWithinSha(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("4.2.0", "4.1.0", "3.6.0");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@7b4f3880<caret> # foo # bar
			""")
	void completesShortShaRef(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(fixture.getEditor().getDocument().getText())
				.contains("actions/checkout@d1185ce5 # 4.2.0");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@<caret> # replace me # here to stay
			""")
	void completesFullShaWithVersionComment(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(fixture.getEditor().getDocument().getText())
				.contains("checkout@d1185ce59f7757407fe6a5febb1e03e3dba2a530 # 4.2.0 # here to stay");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: "actions/checkout@<caret>" # replace me
			""")
	void completesFullShaWithVersionCommentInQuoted(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(fixture.getEditor().getDocument().getText())
				.contains("\"actions/checkout@d1185ce59f7757407fe6a5febb1e03e3dba2a530\" # 4.2.0");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.1<caret> # keep me
			""")
	void retainsCommentAfterVersionCompletion(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(fixture.getEditor().getDocument().getText())
				.contains("actions/checkout@v4.2.0 # keep me");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: "actions/checkout@v4.1<caret>" # keep me
			""")
	void retainsCommentBehindQuotedAfterVersionCompletion(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(fixture.getEditor().getDocument().getText())
				.contains("\"actions/checkout@v4.2.0\" # keep me");
	}

	@Test
	@EditorFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: "actions/checkout@v4.<caret>" # keep me?
			""")
	void preservesClosingQuoteAndTrailingComment(PsiFile workflowFile) {

		GitHubFixtures.analyze(workflowFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(fixture.getEditor().getDocument().getText())
				.contains("\"actions/checkout@v4.2.0\"")
				.contains("# keep me?");
	}

	private void openAtCaretAfter(PsiFile workflowFile, String anchor) {

		String text = workflowFile.getText();
		int caret = text.indexOf(anchor) + anchor.length();
		fixture.getEditor().getCaretModel().moveToOffset(Math.min(caret, text.length()));
	}

}
