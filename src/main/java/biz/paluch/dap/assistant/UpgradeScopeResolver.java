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

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Resolves the {@link UpgradeScope}: the set of build files a single
 * {@link DependencyCheck} runs over.
 *
 * <p>Resolution follows a fixed precedence:
 * <ol>
 * <li>an explicit Project View selection of supported build files,</li>
 * <li>else the active editor's build file,</li>
 * <li>else every supported and applicable build file in the project.</li>
 * </ol>
 *
 * <p>Directories contribute nothing (files only, no recursion). When an
 * explicit selection yields no usable build file the result carries an
 * {@link UpgradeScope#reason() empty reason}:
 * {@link UpgradeScope.Reason#NO_BUILD_FILES} when nothing selected was
 * supported by an integration, or {@link UpgradeScope.Reason#NOT_IMPORTED} when
 * a supported build file's project model is not imported.
 *
 * @author Mark Paluch
 * @see UpgradeScope
 */
class UpgradeScopeResolver {

	private UpgradeScopeResolver() {
	}

	/**
	 * Resolve the {@link UpgradeScope} for the given inputs.
	 */
	public static UpgradeScope resolve(Project project, UpgradeRequest request) {

		if (request.hasSelection()) {
			return resolveSelection(project, request.selection());
		}

		if (request.hasEditorFile()) {
			PsiFile editorFile = request.getEditorFile();
			ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project, editorFile);
			if (context.isAvailable()) {
				return UpgradeScope.resolved(List.of(new UpgradeScope.Entry(context, editorFile)));
			}
		}

		return projectScope(project);
	}

	private static UpgradeScope resolveSelection(Project project, List<VirtualFile> selection) {

		BetterPsiManager psiManager = BetterPsiManager.getInstance(project);
		List<UpgradeScope.Entry> entries = new ArrayList<>();
		boolean supportedButUnavailable = false;

		for (VirtualFile file : selection) {

			if (file.isDirectory()) {
				continue;
			}

			PsiFile psiFile = psiManager.findFile(file);
			if (psiFile == null || !DependencyAssistantDispatcher.supports(psiFile)) {
				continue;
			}

			ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project, psiFile);
			if (context.isAvailable()) {
				entries.add(new UpgradeScope.Entry(context, psiFile));
			} else {
				supportedButUnavailable = true;
			}
		}

		if (!entries.isEmpty()) {
			return UpgradeScope.resolved(entries);
		}

		return UpgradeScope.notFound(
				supportedButUnavailable ? UpgradeScope.Reason.NOT_IMPORTED : UpgradeScope.Reason.NO_BUILD_FILES);
	}

	private static UpgradeScope projectScope(Project project) {

		List<UpgradeScope.Entry> entries = new ArrayList<>();

		for (DependencyAssistant integration : DependencyAssistantDispatcher.findAll(project)) {
			for (PsiFile anchor : integration.enumerate(project)) {
				ProjectDependencyContext context = integration.createContext(project, anchor);
				if (context.isAvailable()) {
					entries.add(new UpgradeScope.Entry(context, anchor));
				}
			}
		}

		return UpgradeScope.resolved(entries);
	}

}
