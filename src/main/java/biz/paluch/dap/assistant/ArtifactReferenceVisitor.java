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
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;

/**
 * {@link PsiElementVisitor} base that resolves visited version elements to an
 * {@link ArtifactReference} and forwards each resolved reference to
 * {@link #visitArtifactReference(PsiElement, ArtifactReference)}.
 *
 * <p>This is the shared extension point for local inspections that need
 * per-element artifact resolution. The platform drives the visitor across a
 * file during the highlighting pass; for every element this base asks the
 * supplied {@link ProjectDependencyContext} whether the element is a version
 * declaration, resolves it through the matching {@link VersionUpgradeLookup},
 * and only then invokes the subclass hook. Elements that are not version
 * declarations and references that fail to resolve are skipped, so subclasses
 * never see unresolved input.
 *
 * <p>Subclasses extend this type and override
 * {@link #visitArtifactReference(PsiElement, ArtifactReference)} to react to
 * resolved declarations; they should not override {@link #visitElement} unless
 * they intend to bypass the resolution gate. Each instance is bound to a single
 * {@link PsiFile} and is intended to be created fresh per inspection run rather
 * than shared. As a visitor invoked from the highlighting pass, the hook runs
 * inside a read action and must stay fast and free of blocking work.
 *
 * @author Mark Paluch
 * @see ProjectDependencyContext
 * @see ArtifactReference
 */
public class ArtifactReferenceVisitor extends PsiElementVisitor {

	private final ProjectDependencyContext dependencyContext;

	private final PsiFile file;

	private final VirtualFile virtualFile;

	/**
	 * Create a new {@code ArtifactReferenceVisitor} for the given file.
	 *
	 * @param dependencyContext the build-tool context used to recognize version
	 * elements and resolve them to artifact references.
	 * @param file the file whose elements are visited; its backing
	 * {@link VirtualFile} is captured for lookup resolution.
	 */
	public ArtifactReferenceVisitor(ProjectDependencyContext dependencyContext, PsiFile file) {
		this.dependencyContext = dependencyContext;
		this.file = file;
		this.virtualFile = file.getVirtualFile();
	}

	/**
	 * Resolve the visited element and forward a successful resolution to
	 * {@link #visitArtifactReference(PsiElement, ArtifactReference)}.
	 *
	 * <p>Elements that the context does not recognize as a version declaration, and
	 * references that do not resolve, are skipped without invoking the hook.
	 *
	 * @param element the element offered by the platform during the visit.
	 */
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

	/**
	 * Callback invoked once per resolved version element.
	 *
	 * <p>The default implementation does nothing. Subclasses override it to inspect
	 * or report the declaration. It is invoked only for elements the context
	 * recognizes as version declarations and whose {@link ArtifactReference}
	 * resolved successfully.
	 *
	 * @param element the version element that produced the reference.
	 * @param reference the resolved artifact reference for the element; guaranteed
	 * to be {@link ArtifactReference#isResolved() resolved}.
	 */
	public void visitArtifactReference(PsiElement element, ArtifactReference reference) {

	}

}
