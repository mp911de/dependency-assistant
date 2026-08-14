/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.assistant.documentation;

import java.util.List;

import biz.paluch.dap.util.PsiElements;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Offset-based entry point for dependency Quick Documentation.
 *
 * <p>The platform consults
 * {@link com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider}
 * only after it has found a target PSI element at the hover offset (a resolving
 * reference or a named declaration). Version literals and dependency
 * coordinates in build files are often plain values without either, so
 * {@link DependencyDocumentationProvider} is never reached there. This provider
 * runs before the target-element machinery and resolves the artifact
 * declaration directly from the file offset.
 *
 * @author Mark Paluch
 * @see DependencyDocumentationProvider
 */
public class DependencyDocumentationTargetProvider implements DocumentationTargetProvider {

	@Override
	public List<? extends DocumentationTarget> documentationTargets(PsiFile file, int offset) {

		PsiElement element = file.findElementAt(offset);
		if (element == null || hasPlatformTarget(file, element, offset)) {
			return List.of();
		}

		DocumentationTarget target = DependencyDocumentationProvider.createTarget(PsiElements.unleaf(element));
		return target != null ? List.of(target) : List.of();
	}

	/**
	 * Whether the platform's target-element machinery would find a documentation
	 * target at the offset on its own (a reference or a named declaration starting
	 * at the caret leaf). Returning a target from this provider suppresses the
	 * entire PSI fallback chain, so such positions must be left alone;
	 * {@link DependencyDocumentationProvider} still handles them through the
	 * fallback.
	 */
	private static boolean hasPlatformTarget(PsiFile file, PsiElement element, int offset) {

		if (file.findReferenceAt(offset) != null) {
			return true;
		}

		PsiNamedElement named = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, false);
		return named != null && !(named instanceof PsiFile)
				&& named.getTextOffset() == element.getTextRange().getStartOffset();
	}

}
