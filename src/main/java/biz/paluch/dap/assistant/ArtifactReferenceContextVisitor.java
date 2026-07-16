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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;

/**
 * {@link PsiElementVisitor} base that resolves visited version elements to an
 * {@link ArtifactReferenceContext} and forwards each resolved reference to
 * {@link #visitArtifactReference(PsiElement, ArtifactReferenceContext)}.
 *
 * @author Mark Paluch
 * @see ProjectDependencyContext
 * @see ArtifactReference
 */
public class ArtifactReferenceContextVisitor extends PsiElementVisitor {

	private final ProjectDependencyContext dependencyContext;

	/**
	 * Create a new {@code ArtifactReferenceContextVisitor}.
	 *
	 * @param dependencyContext the build-tool context used to recognize version
	 * elements and resolve them to artifact references.
	 */
	public ArtifactReferenceContextVisitor(ProjectDependencyContext dependencyContext) {
		this.dependencyContext = dependencyContext;
	}

	/**
	 * Resolve the visited element and forward a successful resolution to
	 * {@link #visitArtifactReference(PsiElement, ArtifactReferenceContext)}.
	 *
	 * <p>Elements that the context does not recognize as a version declaration, and
	 * references that do not resolve, are skipped without invoking the hook.
	 *
	 * @param element the element offered by the platform during the visit.
	 */
	@Override
	public void visitElement(PsiElement element) {

		ArtifactReferenceContext context = ArtifactReferenceContext.from(element, it -> dependencyContext);
		if (context.isAbsent()) {
			return;
		}

		visitArtifactReference(element, context);
	}

	/**
	 * Callback invoked once per resolved version element.
	 *
	 * <p>The default implementation does nothing. Subclasses override it to inspect
	 * or report the declaration. It is invoked only for elements the context
	 * recognizes as version declarations and whose {@link ArtifactReferenceContext}
	 * resolved successfully.
	 *
	 * @param element the version element that produced the reference.
	 * @param context the resolved artifact reference context for the element.
	 */
	protected void visitArtifactReference(PsiElement element, ArtifactReferenceContext context) {

	}

}
