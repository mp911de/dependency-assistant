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
import java.util.Map;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.AppExecutorUtil;

/**
 * Listener that re-collects dependency state when supported build files change.
 *
 * @author Mark Paluch
 */
public class FlushStateOnSave extends ActionsOnSaveFileDocumentManagerListener.ActionOnSave {

	private final FileDocumentManager documentManager = FileDocumentManager.getInstance();

	@Override
	public boolean isEnabledForProject(Project project) {
		return DependencyAssistantDispatcher.supports(project);
	}

	@Override
	public void processDocuments(Project project, Document[] documents) {

		List<VirtualFile> files = new ArrayList<>(documents.length);
		for (Document document : documents) {
			VirtualFile virtualFile = documentManager.getFile(document);
			if (virtualFile == null) {
				continue;
			}
			files.add(virtualFile);
		}
		invalidateState(project, files);

		super.processDocuments(project, documents);
	}

	private void invalidateState(Project project, List<VirtualFile> files) {

		if (files.isEmpty()) {
			return;
		}

		ReadAction.nonBlocking(() -> {

			PsiManager psiManager = PsiManager.getInstance(project);
			List<PsiFile> psiFiles = new ArrayList<>(files.size());
			for (VirtualFile file : files) {
				PsiFile psiFile = psiManager.findFile(file);
				if (psiFile != null) {
					psiFiles.add(psiFile);
				}
			}

			List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll(project);
			Map<DependencyAssistant, List<PsiFile>> grouped = SaveStateRefresh.groupByOwner(assistants, psiFiles);
			if (grouped.isEmpty()) {
				return null;
			}

			new SaveStateRefresh(project).refresh(grouped, new EmptyProgressIndicator());
			return null;
		}).inSmartMode(project).expireWith(project).submit(AppExecutorUtil.getAppExecutorService());
	}

}
