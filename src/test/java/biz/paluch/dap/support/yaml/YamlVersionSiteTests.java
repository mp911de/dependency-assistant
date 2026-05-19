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

package biz.paluch.dap.support.yaml;

import java.util.function.Predicate;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link YamlVersionSite}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class YamlVersionSiteTests {

	private static final Predicate<YAMLKeyValue> IS_USES = kv -> "uses".equals(kv.getKeyText());

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/check<caret>out@v4.1.0
			""")
	void locatesSiteFromCaretOnScalarValue(PsiFile file) {

		YamlVersionSite site = locateAtCaret();

		assertThat(site).isNotNull();
		assertThat(site.rawValue()).isEqualTo("actions/checkout@v4.1.0");
		assertThat(site.quoteStyle()).isEqualTo(QuoteStyle.NONE);
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: "actions/check<caret>out@v4.1.0"
			""")
	void locatesSiteFromCaretOnChildTokenInsideQuotedScalar(PsiFile file) {

		YamlVersionSite site = locateAtCaret();

		assertThat(site).isNotNull();
		assertThat(site.rawValue()).isEqualTo("actions/checkout@v4.1.0");
		assertThat(site.quoteStyle()).isEqualTo(QuoteStyle.DOUBLE);
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - us<caret>es: actions/checkout@v4.1.0
			""")
	void returnsNullWhenCaretSitsOnKey(PsiFile file) {
		assertThat(locateAtCaret()).isNull();
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			# top-of-file comm<caret>ent before any mapping
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.1.0
			""")
	void returnsNullWhenCaretIsOutsideAnyMapping(PsiFile file) {
		assertThat(locateAtCaret()).isNull();
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - name: actions/check<caret>out@v4.1.0
			""")
	void returnsNullWhenPredicateRejectsKey(PsiFile file) {
		assertThat(locateAtCaret()).isNull();
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: 'actions/check<caret>out@v4.1.0'
			""")
	void detectsSingleQuoteStyle(PsiFile file) {
		assertThat(locateAtCaret().quoteStyle()).isEqualTo(QuoteStyle.SINGLE);
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: "actions/check<caret>out@v4.1.0"
			""")
	void detectsDoubleQuoteStyle(PsiFile file) {
		assertThat(locateAtCaret().quoteStyle()).isEqualTo(QuoteStyle.DOUBLE);
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/check<caret>out@v4.1.0
			""")
	void detectsUnquotedStyle(PsiFile file) {
		assertThat(locateAtCaret().quoteStyle()).isEqualTo(QuoteStyle.NONE);
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: 'actions/check<caret>out@v4.1.0'
			""")
	void replacementPreservesSingleQuotes(PsiFile file) {

		replaceAtCaret("actions/checkout@v4.2.0");

		assertThat(file).containsText("'actions/checkout@v4.2.0'").doesNotContainText("\"actions");
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: "actions/check<caret>out@v4.1.0"
			""")
	void replacementPreservesDoubleQuotes(PsiFile file) {

		replaceAtCaret("actions/checkout@v4.2.0");

		assertThat(file).containsText("\"actions/checkout@v4.2.0\"").doesNotContainText("'actions");
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/check<caret>out@v4.1.0
			""")
	void replacementPreservesUnquotedForm(PsiFile file) {

		replaceAtCaret("actions/checkout@v4.2.0");

		assertThat(file).containsText("uses: actions/checkout@v4.2.0").doesNotContainText("'").doesNotContainText("\"");
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: "actions/check<caret>out@v4.1.0"
			""")
	void replacementContainingOriginalQuoteCharacterRoundTrips(PsiFile file) {

		replaceAtCaret("actions/checkout@v4.2.0-\"quoted\"");

		assertThat(file).containsText("actions/checkout@v4.2.0-").containsText("quoted");
		assertThat(locateAtCaret().rawValue()).isEqualTo("actions/checkout@v4.2.0-\"quoted\"");
	}

	@Test
	@EditorFile(name = "ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: 'actions/check<caret>out@v4.1.0'
			""")
	void singleQuotedReplacementContainingOriginalQuoteCharacterRoundTrips(PsiFile file) {

		replaceAtCaret("actions/checkout@v4.2.0-it's");

		assertThat(locateAtCaret().rawValue()).isEqualTo("actions/checkout@v4.2.0-it's");
	}

	private YamlVersionSite locateAtCaret() {

		PsiElement element = fixture.getFile().findElementAt(fixture.getCaretOffset());
		return YamlVersionSite.locate(element, IS_USES);
	}

	private void replaceAtCaret(String newValue) {

		WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> {
			YamlVersionSite site = locateAtCaret();
			site.replaceRawValue(newValue, new YAMLElementGenerator(fixture.getProject()));
		});
	}

}
