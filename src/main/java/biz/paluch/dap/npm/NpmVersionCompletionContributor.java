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
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;

/**
 * Completion contributor for NPM dependency version strings.
 *
 * @author Mark Paluch
 */
public class NpmVersionCompletionContributor extends CompletionContributor {

	private static final ReleasesCompletionProvider provider = new ReleasesCompletionProvider() {

		@Override
		protected LookupElementBuilder postProcess(CompletionParameters parameters,
				LookupElementBuilder builder, PsiElement element, ArtifactRelease option) {

			JsonStringLiteral literal = NpmPsiUtils.findDependencyLiteral(parameters.getPosition());
			boolean closed = literal == null || NpmPsiUtils.isClosed(literal);

			return builder.withInsertHandler((context, lookupElement) -> {

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
		return (ReleasesCompletionProvider.isVersionCharacter(typeChar) || typeChar == '#' || typeChar == '@')
				&& LOCATION.accepts(position);
	}

}
