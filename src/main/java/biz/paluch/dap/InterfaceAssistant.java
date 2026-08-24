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

package biz.paluch.dap;

import javax.swing.Icon;

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

/**
 * User-interface metadata SPI for a build-tool integration.
 *
 * <p>Instances are used by context-independent presentation code for names,
 * icons, and version highlight ranges. Implementations must not depend on a
 * {@link ProjectDependencyContext} or retain project and file state.
 *
 * @author Mark Paluch
 */
public interface InterfaceAssistant {

	/**
	 * Return the human-readable integration name.
	 * @return the integration name for presentation to users.
	 */
	String getDisplayName();

	/**
	 * Return the human-readable integration name for the given file.
	 * <p>The default returns {@link #getDisplayName()}. Integrations whose name
	 * depends on the concrete file (such as Gradle Groovy vs Kotlin DSL) override
	 * this method.
	 * @param file the file to get the display name for.
	 * @return the integration name for the file.
	 */
	default String getDisplayName(VirtualFile file) {
		return getDisplayName();
	}

	/**
	 * Return the gutter action icon to use for the given declaration.
	 * @param declaration the declaration that should use the icon.
	 * @return the icon for dependency actions in the gutter.
	 */
	Icon getGutterIcon(ArtifactDeclaration declaration);

	/**
	 * Return the gutter navigation icon to use for the given declaration.
	 * @param declaration the declaration that should use the icon.
	 * @return the icon for dependency navigation in the gutter.
	 */
	Icon getNavigateIcon(ArtifactDeclaration declaration);

	/**
	 * Return the table icon to use for the given {@link Dependency}.
	 * @param dependency the dependency for which to return the icon.
	 * @return the icon representing the dependency in tables.
	 */
	Icon getTableIcon(Dependency dependency);

	/**
	 * Return the document range used by the annotator and gutter line marker to
	 * highlight the version portion of {@code element}. Implementations narrow the
	 * range to a build-tool-specific sub-range, such as the version string inside a
	 * quoted TOML literal or the ref segment of a GitHub Actions {@code uses:}
	 * declaration. The default returns the element's own range so unsupported
	 * inputs degrade gracefully.
	 * @param element the element whose version sub-range should be highlighted.
	 * @return the document range to highlight.
	 */
	default TextRange getHighlightRange(PsiElement element) {
		return element.getTextRange();
	}

}
