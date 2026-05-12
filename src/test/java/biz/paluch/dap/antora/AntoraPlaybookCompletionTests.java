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

package biz.paluch.dap.antora;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for Antora playbook completion.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class AntoraPlaybookCompletionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		AntoraFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/<caret>/ui-bundle.zip
			""")
	void completesVersionsInsideSegment(PsiFile playbookFile) {

		AntoraFixtures.analyze(playbookFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("v0.4.26", "v0.4.25", "v0.3.0");

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(playbookFile).containsText("/download/v0.4.26/ui-bundle.zip");
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/<caret>/ui-bundle.zip
			""")
	void completesVersionsInsideSegmentWithTab(PsiFile playbookFile) {

		AntoraFixtures.analyze(playbookFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("v0.4.26", "v0.4.25", "v0.3.0");

		fixture.completeBasic();
		fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR);

		assertThat(playbookFile).containsText("/download/v0.4.26/ui-bundle.zip");
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github<caret>.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip
			""")
	void noCompletionInsideHost(PsiFile playbookFile) {

		AntoraFixtures.analyze(playbookFile);

		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings()).doesNotContain("v0.4.26");
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bun<caret>dle.zip
			""")
	void noCompletionInsideAsset(PsiFile playbookFile) {

		AntoraFixtures.analyze(playbookFile);

		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings()).doesNotContain("v0.4.26");
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.<caret>25/ui-bundle.zip
			""")
	void insertHandlerUpdatesOnlyVersionSegment(PsiFile playbookFile) {

		AntoraFixtures.analyze(playbookFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(playbookFile).containsText("/download/v0.4.26/ui-bundle.zip");
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: 'https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.<caret>25/ui-bundle.zip'
			""")
	void insertHandlerPreservesSingleQuotes(PsiFile playbookFile) {

		AntoraFixtures.analyze(playbookFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(playbookFile).containsText(
				"'https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip'");
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: "https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.<caret>25/ui-bundle.zip"
			""")
	void insertHandlerPreservesDoubleQuotes(PsiFile playbookFile) {

		AntoraFixtures.analyze(playbookFile);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(playbookFile).containsText("releases/download/v0.4.26/ui-bundle.zip\"");
	}

}
