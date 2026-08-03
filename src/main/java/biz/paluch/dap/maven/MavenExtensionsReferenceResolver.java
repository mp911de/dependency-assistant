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

package biz.paluch.dap.maven;

import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jspecify.annotations.Nullable;

/**
 * Maven extensions implementation of {@link ArtifactReferenceResolver}.
 *
 * <p>Resolves version tags in {@code extensions.xml} files into an
 * {@link ArtifactReference}.
 *
 * @author Mark Paluch
 */
class MavenExtensionsReferenceResolver implements ArtifactReferenceResolver {

	private final @Nullable SmartPsiElementPointer<XmlFile> extensionsFile;

	private final boolean candidate;

	/**
	 * Create a resolver for the given build file.
	 * @param extensionsFile the {@code extensions.xml} file to inspect.
	 */
	MavenExtensionsReferenceResolver(PsiFile extensionsFile) {

		this.extensionsFile = extensionsFile instanceof XmlFile xmlFile
				? SmartPointerManager.createPointer(xmlFile)
				: null;
		this.candidate = MavenUtils.isMavenExtensionsFile(extensionsFile);
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (!isResolvableElement(element) || !canResolve()) {
			return ArtifactReference.unresolved();
		}

		if (XmlUtil.findVersionTag(element) instanceof XmlTag versionTag) {
			return resolveArtifactDeclaration(versionTag);
		}

		return ArtifactReference.unresolved();
	}

	private boolean canResolve() {
		return candidate && extensionsFile != null && extensionsFile.getElement() != null;
	}

	/**
	 * Resolution is anchored to the {@link XmlText} value of a version tag. Line
	 * markers and highlighting fire on every element of a tag (the angle brackets,
	 * the tag name, the value text, and the surrounding text node); pinning to the
	 * single text node keeps the gutter from duplicating across them.
	 */
	private boolean isResolvableElement(PsiElement element) {
		return element.isValid() && element instanceof XmlText;
	}

	private ArtifactReference resolveArtifactDeclaration(XmlTag versionTag) {

		XmlTag parentTag = versionTag.getParentTag();
		XmlFile file = extensionsFile != null ? extensionsFile.getElement() : null;
		if (parentTag == null || file == null) {
			return ArtifactReference.unresolved();
		}
		MavenParser parser = new MavenParser(StateService.getInstance(parentTag.getProject()).getCache());
		for (ArtifactDeclaration declaration : parser.parseExtensionsFile(file)) {
			if (declaration.getDeclarationElement() == parentTag) {
				return ArtifactReference.from(declaration);
			}
		}
		return ArtifactReference.unresolved();
	}

}
