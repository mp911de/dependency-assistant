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

import biz.paluch.dap.DependencyAssistantDispatcher;
import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;

/**
 * {@link CompletionConfidence} implementation that prevents autopopup
 * suppression in Gradle Groovy DSL files when the context is supported by the
 * {@link DependencyAssistantDispatcher}.
 *
 * @author Mark Paluch
 */
public class GroovyCompletionConfidence extends CompletionConfidence {

	@Override
	public ThreeState shouldSkipAutopopup(Editor editor, PsiElement contextElement, PsiFile psiFile,
			int offset) {

		if (GradleUtils.isGradleFile(psiFile) && DependencyAssistantDispatcher.supports(psiFile)
				&& GroovyCompletionContributor.isSupportedCompletionSite(contextElement)) {
			return ThreeState.NO;
		}

		return ThreeState.UNSURE;
	}

}
