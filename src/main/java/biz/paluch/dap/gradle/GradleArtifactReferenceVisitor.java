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

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;

/**
 * Recursively resolves every element in a Gradle file through an
 * {@link ArtifactReferenceResolver} and collects the resolved references, so a
 * Dependency Site Find can reuse the same resolution that powers line markers.
 *
 * <p>Duplicated from the {@code assistant} package for now.
 *
 * @author Mark Paluch
 */
class GradleArtifactReferenceVisitor extends PsiRecursiveElementVisitor {

	private final ArtifactReferenceResolver resolver;

	private final List<Match> matches = new ArrayList<>();

	GradleArtifactReferenceVisitor(ArtifactReferenceResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public void visitElement(PsiElement element) {

		ArtifactReference reference = resolver.resolveArtifactReference(element);
		if (reference.isResolved()) {
			matches.add(new Match(element, reference));
		}

		super.visitElement(element);
	}

	List<Match> getMatches() {
		return matches;
	}

	/**
	 * A resolved artifact reference together with the element that produced it.
	 */
	record Match(PsiElement element, ArtifactReference reference) {
	}

}
