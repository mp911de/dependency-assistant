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

import java.util.Collections;
import java.util.List;

import biz.paluch.dap.assistant.DependencyDocumentationProvider;
import biz.paluch.dap.util.PsiElements;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mark Paluch
 */
public class NpmDependencyDocumentationTargetProvider implements DocumentationTargetProvider {

	DependencyDocumentationProvider delegate = new DependencyDocumentationProvider();

	@Override
	public @NotNull List<? extends @NotNull DocumentationTarget> documentationTargets(@NotNull PsiFile file,
			int offset) {

		if (!(file instanceof JsonFile)) {
			return Collections.emptyList();
		}

		PsiElement element = file.findElementAt(offset);
		if (element == null && offset > 0) {
			element = file.findElementAt(offset - 1);
		}


		if (element == null || !(PsiElements.unleaf(element) instanceof JsonStringLiteral literal)) {
			return Collections.emptyList();
		}

		DocumentationTarget documentationTarget = delegate.documentationTarget(literal, null);
		return documentationTarget != null ? List.of(documentationTarget) : Collections.emptyList();
	}

}
