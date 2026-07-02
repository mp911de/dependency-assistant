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

import java.util.Locale;

import biz.paluch.dap.assistant.completion.ReleaseCompletionProvider;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;

/**
 * Shared helpers for Gradle Groovy/Kotlin version completion.
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
		while (prefixStart > contentStart
				&& ReleaseCompletionProvider.isVersionTokenCharacter(text.charAt(prefixStart - 1))) {
			prefixStart--;
		}

		if (prefixStart < 0 || prefixStart > prefixEnd) {
			return "";
		}

		return text.substring(prefixStart, prefixEnd);
	}

	static void trimInsertedVersionSuffix(InsertionContext context, boolean replaceFollowingExpression) {

		Document document = context.getDocument();
		int tailOffset = context.getTailOffset();
		if (tailOffset < 0 || tailOffset > document.getTextLength()) {
			return;
		}

		CharSequence text = document.getCharsSequence();
		int deleteEnd = tailOffset;
		while (deleteEnd < text.length() && ReleaseCompletionProvider.isVersionTokenCharacter(text.charAt(deleteEnd))) {
			deleteEnd++;
		}

		if (replaceFollowingExpression) {
			deleteEnd = extendFollowingExpression(text, deleteEnd, text.length());
		}

		if (deleteEnd > tailOffset) {
			document.deleteString(tailOffset, deleteEnd);
		}

		context.getEditor().getCaretModel().moveToOffset(tailOffset);
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

	private static int getStringContentStart(String text) {

		if (text.startsWith("\"\"\"") || text.startsWith("'''")) {
			return 3;
		}

		return text.startsWith("\"") || text.startsWith("'") ? 1 : 0;
	}

}
