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

package biz.paluch.dap.assistant.action;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.assistant.check.UpgradeScope;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Selects the build files for a dependency check.
 * <p>Explicit selection takes precedence over an available editor context, then
 * project-wide discovery. Directories are ignored without recursion. An
 * unusable explicit selection yields an empty scope instead of falling back.
 *
 * @author Mark Paluch
 */
class UpgradeScopeResolver {

	private UpgradeScopeResolver() {
	}

	/**
	 * Resolve the requested scope.
	 * @return the empty scope and its reason if an explicit selection is unusable.
	 * Project-wide discovery may return a successful empty scope.
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
