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

package biz.paluch.dap.assistant;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.VersionUpgradeLookup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;

/**
 * @author Mark Paluch
 */
class ArtifactReferenceVisitor extends PsiElementVisitor {

	private final ProjectDependencyContext dependencyContext;

	private final PsiFile file;

	private final VirtualFile virtualFile;

	public ArtifactReferenceVisitor(ProjectDependencyContext dependencyContext, PsiFile file) {
		this.dependencyContext = dependencyContext;
		this.file = file;
		this.virtualFile = file.getVirtualFile();
	}

	@Override
	public void visitElement(PsiElement element) {

		if (!dependencyContext.isVersionElement(element)) {
			return;
		}

		VersionUpgradeLookup lookup = dependencyContext.getLookup(element, virtualFile);
		ArtifactReference reference = lookup.resolveArtifactReference(element);
		if (!reference.isResolved()) {
			return;
		}

		visitArtifactReference(element, reference);
	}

	public void visitArtifactReference(PsiElement element, ArtifactReference reference) {

	}

}
