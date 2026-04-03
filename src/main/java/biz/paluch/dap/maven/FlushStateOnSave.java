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

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * Listener that invalidates and re-collects the dependency state for build files when they are saved or reloaded.
 *
 * @author Mark Paluch
 */
public class FlushStateOnSave extends ActionsOnSaveFileDocumentManagerListener.ActionOnSave
		implements FileDocumentManagerListener {

	private final FileDocumentManager DOCUMENT_MANAGER = FileDocumentManager.getInstance();

	@Override
	public void fileContentReloaded(VirtualFile file, Document document) {

		Project project = ProjectLocator.getInstance().guessProjectForFile(file);
		if (project == null) {
			return;
		}

		DependencyAssistantService state = DependencyAssistantService.getInstance(project);
		MavenProjectContext mavenContext = MavenProjectContext.of(project, file);
		if (!mavenContext.isAvailable()) {
			return;
		}

		ProjectState projectState = state.getProjectState(mavenContext.getProjectId());
		projectState.invalidateDependencies();

		ApplicationManager.getApplication().runReadAction(() -> {
			PsiManager psiManager = PsiManager.getInstance(project);
			PsiFile buildFile = psiManager.findFile(file);
			if (buildFile != null) {
				DependencyCollector collector = MavenDependencyCheckService.getInstance(project).collectArtifacts(buildFile);
				projectState.setDependencies(collector);
			}
		});
	}

	@Override
	public void afterDocumentSaved(Document document) {

		VirtualFile virtualFile = DOCUMENT_MANAGER.getFile(document);
		if (virtualFile != null) {
			fileContentReloaded(virtualFile, document);
		}
	}

	@Override
	public boolean isEnabledForProject(Project project) {
		return true;
	}

	@Override
	public void processDocuments(Project project, Document[] documents) {

		DependencyAssistantService state = DependencyAssistantService.getInstance(project);

		for (Document document : documents) {
			VirtualFile virtualFile = DOCUMENT_MANAGER.getFile(document);
			if (virtualFile == null) {
				continue;
			}

			MavenProjectContext mavenContext = MavenProjectContext.of(project, virtualFile);
			if (mavenContext.isAvailable()) {
				state.getProjectState(mavenContext.getProjectId()).invalidateDependencies();
			}
		}

		super.processDocuments(project, documents);
	}

}
