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

import biz.paluch.dap.assistant.ReleasesCompletionProvider;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;

/**
 * Completion contributor for Gradle Kotlin DSL.
 *
 * @author Mark Paluch
 */
public class KotlinCompletionContributor extends CompletionContributor {

	private static final ReleasesCompletionProvider provider = new ReleasesCompletionProvider();

	public KotlinCompletionContributor() {

		PatternCondition<PsiFile> isGradleKotlinDslFile = new PatternCondition<>("isGradleKotlinDslFile") {

			@Override
			public boolean accepts(PsiFile psiFile, ProcessingContext processingContext) {
				return GradleUtils.isKotlinDsl(psiFile);
			}

		};

		extend(CompletionType.BASIC, PlatformPatterns.psiElement()
				.inside(PlatformPatterns.psiFile().with(isGradleKotlinDslFile)), provider);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {

		if (typeChar == ':') {
			String existingText = position.getText();
			return StringUtils.hasText(existingText) && StringUtil.countChars(existingText, ':') == 1;
		}

		return ReleasesCompletionProvider.isVersionCharacter(typeChar);
	}

}
