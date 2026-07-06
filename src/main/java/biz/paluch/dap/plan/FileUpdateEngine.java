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

package biz.paluch.dap.plan;

import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeResult;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

/**
 * File-mutation engine shared by plan apply, apply-and-commit, and preview.
 *
 * @author Mark Paluch
 */
class FileUpdateEngine {

	private final Project project;

	private final List<DependencyAssistant> assistants;

	private final BetterPsiManager psiManager;

	private final PsiDocumentManager documentManager;

	private final FileDocumentManager fileDocumentManager;

	FileUpdateEngine(Project project) {
		this.project = project;
		this.assistants = DependencyAssistantDispatcher.findAll(project);
		this.psiManager = BetterPsiManager.getInstance(project);
		this.documentManager = PsiDocumentManager.getInstance(project);
		this.fileDocumentManager = FileDocumentManager.getInstance();
	}

	/**
	 * Apply one update set inside the caller's write action. No IDE command, thread
	 * switch, save, or undo boundary is created.
	 */
	UpgradeResult apply(FileScope scope, List<DependencyUpdate> updates) {

		ApplicationManager.getApplication().assertWriteAccessAllowed();

		if (updates.isEmpty() || scope.isEmpty()) {
			return UpgradeResult.none();
		}

		UpgradeResult applied = UpgradeResult.none();
		for (VirtualFile file : scope.toList()) {
			Document document = fileDocumentManager.getDocument(file);
			if (document != null) {
				documentManager.commitDocument(document);
			}

			PsiFile psiFile = psiManager.findFile(file);
			if (psiFile == null) {
				throw new IllegalStateException("Cannot resolve build file " + file.getPresentableUrl());
			}
			applied = applied.merge(applyToFile(psiFile, psiFile, updates));
			if (document != null) {
				documentManager.commitDocument(document);
				fileDocumentManager.saveDocument(document);
			}
		}

		return applied;
	}

	/**
	 * Apply updates through the assistants supporting the source file. Preview uses
	 * a non-physical target copy while real apply uses the source as target.
	 */
	UpgradeResult applyToFile(PsiFile source, PsiFile target, List<DependencyUpdate> updates) {

		UpgradeResult result = UpgradeResult.none();

		for (DependencyAssistant assistant : assistants) {

			if (!assistant.supports(source)) {
				continue;
			}

			ProjectDependencyContext context = assistant.createContext(source);
			if (!context.isAvailable()) {
				continue;
			}

			result = result.merge(context.applyUpdates(target, updates));
		}

		return result;
	}

}
