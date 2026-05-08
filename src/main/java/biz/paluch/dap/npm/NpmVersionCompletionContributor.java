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
import biz.paluch.dap.assistant.ReleasesCompletionProvider;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;

/**
 * Completion contributor for NPM dependency version strings.
 *
 * @author Mark Paluch
 */
public class NpmVersionCompletionContributor extends CompletionContributor {

	private static final ReleasesCompletionProvider contributor = new ReleasesCompletionProvider() {

		@Override
		protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters,
				CompletionResultSet result) {

			JsonStringLiteral literal = NpmPsiUtils.findDependencyLiteral(parameters.getPosition());

			if (parameters.getInvocationCount() > 1) {
				return result.withPrefixMatcher("");
			}
			if (literal != null) {
				return result.withPrefixMatcher(getPrefix(parameters, literal));
			}

			return result;
		}

		@Override
		protected LookupElementBuilder postProcess(CompletionParameters parameters,
				LookupElementBuilder builder, PsiElement element, ArtifactRelease option) {
			return builder.withInsertHandler((context, lookupElement) -> {
			});
		}

	};

	public NpmVersionCompletionContributor() {
		extend(CompletionType.BASIC,
				PlatformPatterns.psiElement().with(new PatternCondition<>("inNpmDependencyValue") {

					@Override
					public boolean accepts(PsiElement element, ProcessingContext context) {
						return NpmPsiUtils.findDependencyLiteral(element) != null;
					}

				}), contributor);
	}

	public static TextRange getVersionRange(JsonStringLiteral literal) {

		// Use raw text to keep offsets aligned with the document, even for quoted
		// scalars.
		String text = literal.getText();
		int atIndex = text.indexOf('#');
		if (atIndex == -1) {
			atIndex = text.indexOf('@');
		}
		if (atIndex < 0) {
			return literal.getTextRange();
		}

		TextRange scalarRange = literal.getTextRange();
		int refStart = scalarRange.getStartOffset() + atIndex + 1;
		int refEnd = scalarRange.getEndOffset();
		// Trim a trailing matching quote when the scalar is quoted.

		return new TextRange(refStart, refEnd);
	}

	private static String getPrefix(CompletionParameters parameters, JsonStringLiteral literal) {
		String text = literal.getText();
		int atIndex = text.indexOf("IntellijIdeaRulezzz");
		if (atIndex == -1) {
			atIndex = text.indexOf('@');
		}
		if (atIndex < 0) {
			return "";
		}
		int caretInScalar = parameters.getOffset() - literal.getTextRange().getStartOffset();
		int refStart = atIndex + 1;
		if (caretInScalar < refStart || caretInScalar > text.length()) {
			return "";
		}
		return text.substring(refStart, caretInScalar);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {

		// TODO: @ and # only for Git dependencies/or npm:?
		// TODO: "bootstrap-vue": "npm:@ankurk91/bootstrap-vue#2.23.1" missing
		// highlighting
		return (ReleasesCompletionProvider.isVersionCharacter(typeChar) || typeChar == '#' || typeChar == '@')
				&& NpmPsiUtils.findDependencyLiteral(position) != null;
	}

}
