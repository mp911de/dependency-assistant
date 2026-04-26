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

import javax.swing.*;

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.openapi.vfs.VirtualFile;

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

}
