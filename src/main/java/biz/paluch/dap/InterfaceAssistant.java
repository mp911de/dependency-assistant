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

	String getDisplayName();

	/**
	 * Return the integration name for a file.
	 * <p>The default uses the context-independent name.
	 */
	default String getDisplayName(VirtualFile file) {
		return getDisplayName();
	}

	/**
	 * Return the icon for dependency actions in the gutter.
	 */
	Icon getGutterIcon(ArtifactDeclaration declaration);

	/**
	 * Return the icon for dependency navigation in the gutter.
	 */
	Icon getNavigateIcon(ArtifactDeclaration declaration);

	Icon getTableIcon(Dependency dependency);

	/**
	 * Return the document range covering the dependency version.
	 * <p>The default uses the entire element range.
	 */
	default TextRange getHighlightRange(PsiElement element) {
		return element.getTextRange();
	}

}
