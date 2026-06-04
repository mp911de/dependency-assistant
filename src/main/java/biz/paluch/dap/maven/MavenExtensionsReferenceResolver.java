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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.ArtifactReferenceResolver;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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

	private final @Nullable XmlFile extensionsFile;

	private final boolean candidate;

	/**
	 * Create a resolver for the given build file.
	 * @param extensionsFile the {@code extensions.xml} file to inspect.
	 */
	MavenExtensionsReferenceResolver(PsiFile extensionsFile) {

		this.extensionsFile = extensionsFile instanceof XmlFile xmlFile ? xmlFile : null;
		this.candidate = MavenUtils.isMavenExtensionsFile(extensionsFile);
	}

	@Override
	public ArtifactReference resolveArtifactReference(PsiElement element) {

		if (!isResolvableElement(element) || !canResolve()) {
			return ArtifactReference.unresolved();
		}

		if (XmlUtil.findVersionTag(element) instanceof XmlTag versionTag && MavenUtils.isVersionElement(versionTag)) {
			return resolveArtifactDeclaration(versionTag);
		}

		return ArtifactReference.unresolved();
	}

	private boolean canResolve() {
		return candidate && extensionsFile != null;
	}

	/**
	 * Resolution is anchored to the {@link XmlText} value of a version or property
	 * tag. Line markers and highlighting fire on every element of a tag (the angle
	 * brackets, the tag name, the value text, and the surrounding text node);
	 * pinning to the single text node keeps the gutter from duplicating across
	 * them. Completion and documentation pre-unleaf to this same text node.
	 */
	private boolean isResolvableElement(PsiElement element) {
		return element.isValid() && element instanceof XmlText;
	}

	private ArtifactReference resolveArtifactDeclaration(XmlTag versionTag) {

		XmlTag parentTag = versionTag.getParentTag();
		ArtifactId artifactId = MavenParser.parseArtifactId(parentTag, PropertyResolver.empty());
		if (artifactId == null) {
			return ArtifactReference.unresolved();
		}
		DeclarationSource declarationSource = MavenParser.getDeclarationSource(parentTag);

		String version = versionTag.getValue().getText().trim();
		return ArtifactReference.from(it -> {
			it.artifact(artifactId).declarationElement(parentTag)
					.declarationSource(MavenParser.getDeclarationSource(parentTag))
					.versionSource(VersionSource.from(version))
					.declarationSource(declarationSource);
			ArtifactVersion.from(version).ifPresent(it::version);
			it.versionLiteral(versionTag);
		});
	}

}
