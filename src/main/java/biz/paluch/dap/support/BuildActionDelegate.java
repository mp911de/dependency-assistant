/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.paluch.dap.support;

import java.util.List;
import java.util.function.BiConsumer;

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DependencyUpdate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * Delegate to update the build file providing write action guarding.
 * 
 * @author Mark Paluch
 * @see UpdateBuildFile
 */
public class BuildActionDelegate implements UpdateBuildFile {

	private final Project project;

	public final BiConsumer<PsiFile, List<DependencyUpdate>> updateAction;

	private final VirtualFile buildFile;

	public BuildActionDelegate(Project project, ProjectDependencyContext dependencyContext, VirtualFile buildFile) {
		this(project, dependencyContext::applyUpdates, buildFile);
	}

	public BuildActionDelegate(Project project, BiConsumer<PsiFile, List<DependencyUpdate>> updateAction,
			VirtualFile buildFile) {
		this.project = project;
		this.updateAction = updateAction;
		this.buildFile = buildFile;
	}

	@Override
	public void updateBuildFile(List<DependencyUpdate> updates) {

		if (updates.isEmpty()) {
			return;
		}

		Runnable updatePom = () -> {
			Document document = FileDocumentManager.getInstance().getDocument(buildFile);
			if (document != null) {
				PsiDocumentManager.getInstance(project).commitDocument(document);
			}

			PsiFile psiFile = PsiManager.getInstance(project).findFile(buildFile);

			if (psiFile == null) {
				// TODO: Balloon notification
				return;
			}
			try {
				updateAction.accept(psiFile, updates);
			} catch (Exception e) {
				// TODO: Balloon notification
			}
		};

		ApplicationManager.getApplication().runWriteAction(() -> {
			CommandProcessor.getInstance().executeCommand(project, updatePom,
					MessageBundle.message("command.update.title"),
					null);
		});
	}

}
