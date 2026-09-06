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

package biz.paluch.dap.maven;

import biz.paluch.dap.lookup.ArtifactReferenceResolver;
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
 * Resolves version references in {@code extensions.xml}.
 *
 * @author Mark Paluch
 */
class MavenExtensionsReferenceResolver implements ArtifactReferenceResolver {

	private final @Nullable SmartPsiElementPointer<XmlFile> extensionsFile;

	private final boolean candidate;

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
	 * Use only the value node so one declaration does not produce duplicate gutter
	 * markers.
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
		MavenParser parser = new MavenParser();
		for (ArtifactDeclaration declaration : parser.parseExtensionsFile(file)) {
			if (declaration.getDeclarationElement() == parentTag) {
				return ArtifactReference.from(declaration);
			}
		}
		return ArtifactReference.unresolved();
	}

}
