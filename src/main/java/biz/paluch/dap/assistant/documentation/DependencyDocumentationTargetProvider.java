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
 * Offset-based Quick Documentation for dependency literals.
 * <p>Plain build-file values often have no named declaration or resolving
 * reference, so the platform's PSI-target lookup does not reach them.
 *
 * @author Mark Paluch
 * @see DependencyDocumentationProvider
 */
public class DependencyDocumentationTargetProvider implements DocumentationTargetProvider {

	@Override
	public List<? extends DocumentationTarget> documentationTargets(PsiFile file, int offset) {

		PsiElement element = file.findElementAt(offset);
		if (element == null) {
			return List.of();
		}

		DocumentationTarget target = DependencyDocumentationProvider.createTarget(PsiElements.unleaf(element));
		return target != null ? List.of(target) : List.of();
	}

	private static boolean hasPlatformTarget(PsiFile file, PsiElement element, int offset) {

		if (file.findReferenceAt(offset) != null) {
			return true;
		}

		PsiNamedElement named = PsiTreeUtil.getParentOfType(element, PsiNamedElement.class, false);
		return named != null && !(named instanceof PsiFile)
				&& named.getTextOffset() == element.getTextRange().getStartOffset();
	}

}
