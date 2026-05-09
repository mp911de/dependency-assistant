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

import java.util.List;
import java.util.function.BiConsumer;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.UpdateBuildFile;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
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

	private static final Logger LOG = Logger.getInstance(BuildActionDelegate.class);

	private final Project project;

	public final BiConsumer<PsiFile, List<DependencyUpdate>> updateAction;

	private final VirtualFile buildFile;

	/**
	 * Create a delegate using the update action from the given dependency context.
	 */
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
				Notifications.error(project, MessageBundle.message("UpdateBuildFile.notification.error.title"),
						MessageBundle.message("UpdateBuildFile.notification.no-file", buildFile.getPresentableUrl()));
				return;
			}
			try {
				updateAction.accept(psiFile, updates);
			} catch (Exception ex) {
				LOG.warn("Build file update failed", ex);
				Notifications.error(project, MessageBundle.message("UpdateBuildFile.notification.error.title"),
						MessageBundle.message("UpdateBuildFile.notification.failed", buildFile.getPresentableUrl(),
								Notifications.errorMessage(ex)));
			}
		};

		WriteCommandAction.runWriteCommandAction(project, MessageBundle.message("UpdateBuildFile.title"), null,
				updatePom);
	}

}
