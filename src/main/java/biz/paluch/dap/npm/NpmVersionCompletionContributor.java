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

package biz.paluch.dap.npm;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.ReleaseCompletionProvider;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;

/**
 * Completion contributor for NPM dependency version strings.
 *
 * @author Mark Paluch
 */
public class NpmVersionCompletionContributor extends CompletionContributor {

	private static final ReleaseCompletionProvider provider = new ReleaseCompletionProvider() {

		@Override
		protected LookupElementBuilder postProcess(CompletionParameters parameters,
				LookupElementBuilder builder, PsiElement element, ArtifactRelease option) {

			JsonStringLiteral literal = NpmPsiUtils.findDependencyLiteral(element);
			if (literal == null) {
				literal = NpmPsiUtils.findDependencyLiteral(parameters.getPosition());
			}
			boolean closed = literal == null || NpmPsiUtils.isClosed(literal);
			String originalValue = literal != null ? literal.getValue() : null;
			NpmVersionExpression expression = NpmVersionExpression.parse(originalValue);
			int caretInValue = literal != null ? getCaretInValue(parameters, literal) : 0;
			int valueStart = literal != null ? getValueStart(literal) : -1;

			return builder.withInsertHandler((context, lookupElement) -> {

				if (valueStart >= 0 && originalValue != null) {
					String replacement = renderCompletion(originalValue, expression, caretInValue,
							option.getVersion(), lookupElement.getLookupString());
					replaceLiteralValue(context, valueStart, closed, replacement);
					return;
				}

				if (!closed) {
					context.getDocument().insertString(context.getTailOffset(), "\"");
				}
			});
		}

	};

	private static final ElementPattern<JsonProperty> DEPENDENCIES = PlatformPatterns.or(
			PlatformPatterns.psiElement(JsonProperty.class).withName("dependencies"),
			PlatformPatterns.psiElement(JsonProperty.class).withName("devDependencies"));

	private static final ElementPattern<JsonStringLiteral> DEPENDENCY_VALUE = PlatformPatterns
			.psiElement(JsonStringLiteral.class)
			.withParent(PlatformPatterns.psiElement(JsonProperty.class)
					.withParent(PlatformPatterns.psiElement(JsonObject.class)
							.withParent(DEPENDENCIES)));

	private static final ElementPattern<PsiElement> LOCATION = PlatformPatterns.psiElement()
			.inside(DEPENDENCY_VALUE)
			.inside(PlatformPatterns.psiFile().withName(NpmUtils.PACKAGE_JSON));

	public NpmVersionCompletionContributor() {
		extend(CompletionType.BASIC, LOCATION, provider);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return (ReleaseCompletionProvider.isVersionCharacter(typeChar) || typeChar == '#' || typeChar == '@')
				&& LOCATION.accepts(position);
	}

	private static int getCaretInValue(CompletionParameters parameters, JsonStringLiteral literal) {

		TextRange valueRange = ElementManipulators.getValueTextRange(literal);
		int valueStart = literal.getTextRange().getStartOffset() + valueRange.getStartOffset();
		return Math.clamp(parameters.getOffset() - valueStart, 0, literal.getValue().length());
	}

	private static int getValueStart(JsonStringLiteral literal) {

		TextRange valueRange = ElementManipulators.getValueTextRange(literal);
		return literal.getTextRange().getStartOffset() + valueRange.getStartOffset();
	}

	private static String renderCompletion(String originalValue, NpmVersionExpression expression,
			int caretInValue, ArtifactVersion version, String lookupString) {

		if (expression == null) {
			return lookupString;
		}

		TextRange replaceableRange = expression.replaceableRange(originalValue);
		int start = Math.clamp(replaceableRange.getStartOffset(), 0, originalValue.length());
		int end = Math.clamp(replaceableRange.getEndOffset(), start, originalValue.length());
		if (caretInValue < start) {
			return originalValue.substring(0, caretInValue) + lookupString + originalValue.substring(end);
		}

		String rendered = UpdatePackageJsonFile.render(expression, originalValue, version);
		if (rendered != null) {
			return rendered;
		}

		return originalValue.substring(0, start) + lookupString + originalValue.substring(end);
	}

	private static void replaceLiteralValue(InsertionContext context, int valueStart, boolean closed,
			String replacement) {

		Document document = context.getDocument();
		int valueEnd = findValueEnd(document, valueStart, closed);
		document.replaceString(valueStart, valueEnd, closed ? replacement : replacement + "\"");
		context.getEditor().getCaretModel().moveToOffset(valueStart + replacement.length());
	}

	private static int findValueEnd(Document document, int valueStart, boolean closed) {

		CharSequence chars = document.getCharsSequence();
		for (int i = valueStart; i < chars.length(); i++) {
			char c = chars.charAt(i);
			if (closed && c == '"' && !isEscaped(chars, i)) {
				return i;
			}
			if (!closed && (c == '\n' || c == '\r')) {
				return i;
			}
		}
		return document.getTextLength();
	}

	private static boolean isEscaped(CharSequence chars, int quoteOffset) {

		int backslashes = 0;
		for (int i = quoteOffset - 1; i >= 0 && chars.charAt(i) == '\\'; i--) {
			backslashes++;
		}
		return backslashes % 2 == 1;
	}

}
