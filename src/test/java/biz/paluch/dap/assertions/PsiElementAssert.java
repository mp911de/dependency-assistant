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

package biz.paluch.dap.assertions;

import java.util.Arrays;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;
import org.jspecify.annotations.Nullable;

/**
 * AssertJ assertions for {@link PsiElement} instances.
 *
 * @author Mark Paluch
 */
public class PsiElementAssert
		extends AbstractAssert<PsiElementAssert, PsiElement>
		implements AssertProvider<PsiElementAssert> {

	PsiElementAssert(PsiElement element) {
		super(element, PsiElementAssert.class);
	}

	@Override
	public PsiElementAssert assertThat() {
		return this;
	}

	/**
	 * Verify that the PSI element text contains all of the given values.
	 */
	public PsiElementAssert containsText(CharSequence... values) {
		isNotNull();
		String text = text();
		for (CharSequence value : values) {
			if (!text.contains(value)) {
				failWithMessage("Expected PSI element text to contain %s but was:\n%s",
						Arrays.asList(values), text);
			}
		}
		return this;
	}

	/**
	 * Verify that the PSI element text contains none of the given values.
	 */
	public PsiElementAssert doesNotContainText(CharSequence... values) {
		isNotNull();
		String text = text();
		for (CharSequence value : values) {
			if (text.contains(value)) {
				failWithMessage("Expected PSI element text not to contain %s but was:\n%s",
						Arrays.asList(values), text);
			}
		}
		return this;
	}

	/**
	 * Verify that the editor caret is immediately before the given text.
	 */
	public PsiElementAssert caretBefore(String text) {
		CaretPosition caret = caretPosition();
		if (!caret.text().substring(caret.offset()).startsWith(text)) {
			failWithMessage("Expected caret before '%s' but was at offset %d in:\n%s",
					text, caret.offset(), caret.renderedText());
		}
		return this;
	}

	/**
	 * Verify that the editor caret is immediately after the given text.
	 */
	public PsiElementAssert caretAfter(String text) {
		CaretPosition caret = caretPosition();
		if (!caret.text().substring(0, caret.offset()).endsWith(text)) {
			failWithMessage("Expected caret after '%s' but was at offset %d in:\n%s",
					text, caret.offset(), caret.renderedText());
		}
		return this;
	}

	/**
	 * Verify that the editor caret is immediately between the given prefix and
	 * suffix.
	 */
	public PsiElementAssert caretBetween(String prefix, String suffix) {
		CaretPosition caret = caretPosition();
		String before = caret.text().substring(0, caret.offset());
		String after = caret.text().substring(caret.offset());
		if (!before.endsWith(prefix) || !after.startsWith(suffix)) {
			failWithMessage(
					"Expected caret between '%s' and '%s' but was at offset %d in:\n%s",
					prefix, suffix, caret.offset(), caret.renderedText());
		}
		return this;
	}

	/**
	 * Navigate to gutter mark assertions for all gutters found in the containing
	 * PSI file.
	 */
	public GutterMarksAssert gutters() {
		isNotNull();
		return LineMarkers.of(containingFile()).assertThat();
	}

	/**
	 * Navigate to the gutter mark at the given index.
	 */
	public GutterMarkAssert gutter(int index) {
		return gutters().gutter(index);
	}

	/**
	 * Navigate to the gutter mark at the given index.
	 */
	public GutterMarkAssert gutterAt(int index) {
		return gutters().gutterAt(index);
	}

	/**
	 * Verify that exactly the given number of gutter marks are present.
	 */
	public GutterMarksAssert hasSize(int expected) {
		return gutters().hasSize(expected);
	}

	/**
	 * Verify that exactly one gutter mark is present and navigate to it.
	 */
	public GutterMarkAssert hasSingleGutter() {
		return gutters().hasSingleGutter();
	}

	/**
	 * Verify a single gutter mark is present and that its tooltip contains all of
	 * the given strings.
	 */
	public GutterMarksAssert hasSingleGutterContaining(String... expected) {
		return gutters().hasSingleGutterContaining(expected);
	}

	/**
	 * Verify that no gutter marks are present.
	 */
	public GutterMarksAssert hasNoGutterMarks() {
		return gutters().isEmpty();
	}

	private String text() {
		String text = this.actual.getText();
		if (text == null) {
			failWithMessage("Expected PSI element text to be present but was null");
		}
		return text;
	}

	private PsiFile containingFile() {
		PsiFile file = this.actual instanceof PsiFile psiFile ? psiFile : this.actual.getContainingFile();
		if (file == null) {
			failWithMessage("Expected PSI element to have a containing file but it had none");
		}
		return file;
	}

	private CaretPosition caretPosition() {
		isNotNull();

		PsiFile file = containingFile();
		Document document = PsiDocumentManager.getInstance(this.actual.getProject()).getDocument(file);
		if (document == null) {
			failWithMessage("Expected PSI file '%s' to have an editor document but it had none",
					file.getName());
		}

		Editor editor = findEditor(document);
		if (editor == null) {
			failWithMessage("Expected PSI file '%s' to have an open editor but it had none",
					file.getName());
		}

		int offset = editor.getCaretModel().getOffset();
		assertCaretWithinElement(offset);

		return new CaretPosition(document.getText(), offset);
	}

	private @Nullable Editor findEditor(Document document) {
		Editor[] editors = EditorFactory.getInstance().getEditors(document, this.actual.getProject());
		for (Editor editor : editors) {
			if (!editor.isDisposed() && !editor.isViewer()) {
				return editor;
			}
		}
		for (Editor editor : editors) {
			if (!editor.isDisposed()) {
				return editor;
			}
		}
		return null;
	}

	private void assertCaretWithinElement(int offset) {
		if (this.actual instanceof PsiFile) {
			return;
		}

		TextRange range = this.actual.getTextRange();
		if (range == null) {
			failWithMessage("Expected PSI element to have a text range but it had none");
		}
		if (offset < range.getStartOffset() || offset > range.getEndOffset()) {
			failWithMessage("Expected caret offset %d to be within PSI element range %s", offset, range);
		}
	}

	private record CaretPosition(String text, int offset) {

		private String renderedText() {
			return text.substring(0, offset) + "<caret>" + text.substring(offset);
		}

	}

}
