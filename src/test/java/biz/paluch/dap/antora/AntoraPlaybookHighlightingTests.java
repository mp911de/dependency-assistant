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
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for highlighting Antora playbook files.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class AntoraPlaybookHighlightingTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		AntoraFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip
			""")
	void suggestsVersionUpgrade(PsiFile playbookFile) {

		AntoraFixtures.analyze(playbookFile);

		assertThat(playbookFile).hasSingleGutterContaining("0.4.26");
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip
			""")
	void noGutterForCurrentVersion(PsiFile playbookFile) {

		AntoraFixtures.analyze(playbookFile);

		assertThat(playbookFile).hasNoGutterMarks();
	}

	@Test
	@EditorFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip
			""")
	void highlightRangeCoversOnlyTheVersionSegment(PsiFile playbookFile) {

		AntoraFixtures.analyze(playbookFile);

		assertThat(playbookFile).hasSingleGutter().highlights("v0.4.25");
	}

}
