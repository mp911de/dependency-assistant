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

	public NpmVersionCompletionContributor() {
		extend(CompletionType.BASIC,
				PlatformPatterns.psiElement().with(new PatternCondition<>("inNpmDependencyValue") {

					@Override
					public boolean accepts(PsiElement element, ProcessingContext context) {
						return NpmPsiUtils.findDependencyLiteral(element) != null;
					}

				}),
				new ReleasesCompletionProvider() {

					@Override
					protected LookupElementBuilder postProcess(CompletionParameters parameters,
							LookupElementBuilder builder, PsiElement element, ArtifactRelease option) {
						return builder.withInsertHandler((context, lookupElement) -> {
						});
					}

				});
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return ReleasesCompletionProvider.isVersionCharacter(typeChar)
				&& NpmPsiUtils.findDependencyLiteral(position) != null;
	}

}
