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

package biz.paluch.dap;

import javax.swing.Icon;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

/**
 * User-interface metadata for a build-tool integration.
 *
 * @author Mark Paluch
 */
public interface InterfaceAssistant {

	/**
	 * Return the human-readable integration name.
	 */
	String getDisplayName();

	/**
	 * Return the human-readable integration name for the given file.
	 * @param file the file to get the display name for.
	 */
	String getDisplayName(VirtualFile file);

	/**
	 * Return the human-readable name for an {@link ArtifactId}.
	 * @param artifactId the artifact Id to render.
	 */
	default String getDisplayName(ArtifactId artifactId){
		return artifactId.toString();
	}

	/**
	 * Return the gutter action icon to use for the given declaration.
	 * @param declaration the declaration that should use the icon.
	 */
	Icon getGutterIcon(ArtifactDeclaration declaration);

	/**
	 * Return the gutter navigation icon to use for the given declaration.
	 * @param declaration the declaration that should use the icon.
	 */
	Icon getNavigateIcon(ArtifactDeclaration declaration);

	/**
	 * Return the table icon to use for the given {@link Dependency}.
	 * @param dependency the dependency for which to return the icon.
	 */
	Icon getTableIcon(Dependency dependency);

	/**
	 * Return the documentation text for the given {@link ArtifactVersion} for usage
	 * in a documentation popup.
	 * @param artifactVersion the version to get the documentation text for.
	 * @return the rendered (pretty) documentation text.
	 */
	default String getDocumentationText(ArtifactVersion artifactVersion) {
		return artifactVersion.toString();
	}

	/**
	 * Return the document range used by the annotator and gutter line marker to
	 * highlight the version portion of {@code element}. Implementations narrow the
	 * range to a build-tool-specific sub-range (e.g. the version string inside a
	 * quoted TOML literal, the committish of a Git URL, the ref segment of a GitHub
	 * Actions {@code uses:} declaration); the default returns the element's own
	 * range so unsupported inputs degrade gracefully.
	 * @param element the element whose version sub-range should be highlighted.
	 * @return the highlight range; guaranteed to be not {@literal null}.
	 */
	default TextRange getHighlightRange(PsiElement element) {
		return element.getTextRange();
	}

}
