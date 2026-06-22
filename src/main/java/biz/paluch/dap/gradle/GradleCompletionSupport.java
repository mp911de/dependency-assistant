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
import java.util.Locale;

import biz.paluch.dap.artifact.VersionCaretRemap;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Shared helpers for Gradle Groovy/Kotlin version completion.
 *
 * <p>Inline dependency-notation completion (a {@code "group:name:version"} GAV
 * string, possibly carrying a rich-version range or a {@code ${property}}
 * expression) cannot route through {@code UpdateGradleFile.applyUpdate}: that
 * writer rewrites the whole version segment with a fixed range-bound policy and
 * never sees the caret position or the completion select character. Completion
 * here is caret- and select-char-aware (it inserts at the caret, trims only the
 * in-progress token, and either keeps or replaces a following expression), so
 * it keeps a dedicated insert handler. The handler still positions the caret
 * from a {@link VersionCaretRemap} so caret placement matches the quickfix
 * path.
 *
 * @author Mark Paluch
 */
class GradleCompletionSupport {

	private GradleCompletionSupport() {
	}

	static String getVersionPrefix(CompletionParameters parameters, PsiElement literal) {

		String text = literal.getText();
		int caretInLiteral = parameters.getOffset() - literal.getTextRange().getStartOffset();
		int prefixEnd = getPrefixEnd(text, caretInLiteral);
		if (prefixEnd < 0 || prefixEnd > text.length()) {
			return "";
		}

		int prefixStart = prefixEnd;
		int contentStart = getStringContentStart(text);
		while (prefixStart > contentStart && isVersionChar(text.charAt(prefixStart - 1))) {
			prefixStart--;
		}

		if (prefixStart < 0 || prefixStart > prefixEnd) {
			return "";
		}

		return text.substring(prefixStart, prefixEnd);
	}

	/**
	 * Clean up the version the completion popup pre-inserted into an inline GAV
	 * literal and position the caret behind the inserted version digits.
	 *
	 * <p>The popup inserts the selected version at the prefix-match position but
	 * leaves the rest of the original value (a stale range bound, a following
	 * {@code ${property}} expression) in place. This removes the leftover
	 * in-progress token to the right of the caret, and, when the user committed
	 * with the replace key, the immediately following Gradle expression. The caret
	 * then lands behind the inserted version through a {@link VersionCaretRemap} so
	 * it matches the quickfix path.
	 * @param context the completion insertion context; must not be {@literal null}.
	 * @param insertedVersion the version text the completion popup inserted; must
	 * not be {@literal null}.
	 * @param replaceFollowingExpression whether to also remove a following
	 * {@code ${...}}, {@code $var}, or {@code {...}} expression (true for the
	 * replace select key).
	 */
	static void insertVersion(InsertionContext context, String insertedVersion, boolean replaceFollowingExpression) {

		Document document = context.getDocument();
		int tailOffset = context.getTailOffset();
		if (tailOffset < 0 || tailOffset > document.getTextLength()) {
			return;
		}

		CharSequence text = document.getCharsSequence();
		int deleteEnd = tailOffset;
		while (deleteEnd < text.length() && isVersionChar(text.charAt(deleteEnd))) {
			deleteEnd++;
		}

		if (replaceFollowingExpression) {
			deleteEnd = extendFollowingExpression(text, deleteEnd, text.length());
		}

		if (deleteEnd > tailOffset) {
			document.deleteString(tailOffset, deleteEnd);
		}

		int versionStart = Math.max(0, tailOffset - insertedVersion.length());
		TextRange versionRange = new TextRange(versionStart, tailOffset);
		VersionCaretRemap remap = VersionCaretRemap.of(List.of(versionRange), List.of(versionRange));
		context.getEditor().getCaretModel().moveToOffset(remap.translate(versionStart));
	}

	private static int extendFollowingExpression(CharSequence text, int offset, int contentEnd) {

		if (offset < contentEnd && text.charAt(offset) == '{') {
			return findBraceExpressionEnd(text, offset, contentEnd);
		}

		if (offset + 1 < contentEnd && text.charAt(offset) == '$' && text.charAt(offset + 1) == '{') {
			return findBraceExpressionEnd(text, offset + 1, contentEnd);
		}

		if (offset < contentEnd && text.charAt(offset) == '$') {
			int end = offset + 1;
			while (end < contentEnd && Character.isJavaIdentifierPart(text.charAt(end))) {
				end++;
			}
			return end;
		}

		return offset;
	}

	private static int findBraceExpressionEnd(CharSequence text, int openBraceOffset, int contentEnd) {

		if (openBraceOffset >= contentEnd || text.charAt(openBraceOffset) != '{') {
			return openBraceOffset;
		}

		int depth = 0;
		for (int i = openBraceOffset; i < contentEnd; i++) {
			char c = text.charAt(i);
			if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					return i + 1;
				}
			}
		}

		return openBraceOffset;
	}

	private static int getPrefixEnd(String text, int caretInLiteral) {

		int dummy = text.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
		if (dummy == -1) {
			dummy = text.toLowerCase(Locale.ROOT)
					.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED.toLowerCase(Locale.ROOT));
		}

		int prefixEnd = dummy != -1 ? dummy : caretInLiteral;
		return Math.clamp(prefixEnd, 0, text.length());
	}

	private static int getStringContentStart(String text) {

		if (text.startsWith("\"\"\"") || text.startsWith("'''")) {
			return 3;
		}

		return text.startsWith("\"") || text.startsWith("'") ? 1 : 0;
	}

	private static boolean isVersionChar(char c) {
		return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == '+';
	}

}
