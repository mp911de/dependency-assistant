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
 * suppression in Gradle build files (Groovy and Kotlin DSL scripts, version
 * catalogs, and {@code gradle.properties}) when the context is supported by the
 * {@link DependencyAssistantDispatcher}.
 *
 * <p>Registered once per language; the file type decides which contributor's
 * completion-site check applies.
 *
 * @author Mark Paluch
 */
public class GradleCompletionConfidence extends CompletionConfidence {

	@Override
	public ThreeState shouldSkipAutopopup(Editor editor, PsiElement contextElement, PsiFile psiFile,
			int offset) {

		if (!GradleUtils.isGradleFile(psiFile) || !DependencyAssistantDispatcher.contextSupports(psiFile)) {
			return ThreeState.UNSURE;
		}

		return isSupportedCompletionSite(contextElement, psiFile) ? ThreeState.NO : ThreeState.UNSURE;
	}

	// Branch guards double as class-loading guards: each contributor links
	// language-specific PSI (Groovy, Kotlin, TOML) that may be absent at runtime,
	// so a contributor must only be referenced for its own file type. Do not
	// collapse this dispatch into an OR chain.
	private static boolean isSupportedCompletionSite(PsiElement contextElement, PsiFile psiFile) {

		if (GradleUtils.isGroovyDsl(psiFile)) {
			return GroovyCompletionContributor.isSupportedCompletionSite(contextElement);
		}

		if (GradleUtils.isKotlinDsl(psiFile)) {
			return KotlinCompletionContributor.isSupportedCompletionSite(contextElement);
		}

		if (GradleUtils.isVersionCatalog(psiFile)) {
			return TomlCompletionContributor.isSupportedCompletionSite(contextElement);
		}

		if (GradleUtils.isGradlePropertiesFile(psiFile)) {
			return GradlePropertiesCompletionContributor.isSupportedCompletionSite(contextElement);
		}

		return false;
	}

}
