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

package biz.paluch.dap.assistant.action;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.DependencyAssistantDispatcher;
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Listener that re-collects dependency state when build files are saved,
 * complementing {@link FlushStateOnEdit} as a safety net for changes that reach
 * a document without firing PSI change events.
 *
 * @author Mark Paluch
 * @see StateRefresher
 */
public class FlushStateOnSave extends ActionsOnSaveFileDocumentManagerListener.ActionOnSave {

	private final FileDocumentManager documentManager = FileDocumentManager.getInstance();

	@Override
	public boolean isEnabledForProject(Project project) {
		return DependencyAssistantDispatcher.supports(project);
	}

	@Override
	public void processDocuments(Project project, Document[] documents) {

		StateRefresher refresher = StateRefresher.getInstance(project);

		if (documents.length == 0) {
			return;
		}

		if (documents.length == 1) {

			VirtualFile virtualFile = documentManager.getFile(documents[0]);
			if (virtualFile != null) {
				refresher.refresh(virtualFile);
			}
			return;
		}

		List<VirtualFile> files = new ArrayList<>(documents.length);
		for (Document document : documents) {
			VirtualFile virtualFile = documentManager.getFile(document);
			if (virtualFile == null) {
				continue;
			}
			files.add(virtualFile);
		}

		refresher.refresh(files);
	}

}
