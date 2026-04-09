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
package biz.paluch.dap.gradle;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.MessageBundle;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Tools menu action to check Gradle dependency versions for the build file currently open in the editor.
 *
 * @author Mark Paluch
 */
public class UpdateDependenciesAction extends AnAction {

	@Override
	public ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void actionPerformed(AnActionEvent e) {

		Project project = e.getProject();
		if (project == null) {
			return;
		}
		Editor editor = e.getData(CommonDataKeys.EDITOR);
		if (editor == null) {
			Messages.showMessageDialog(project, MessageBundle.message("gradle.action.check.dependencies.noEditorOpen"),
					MessageBundle.message("gradle.action.check.dependencies.noEditorOpen.title"), Messages.getInformationIcon());
			return;
		}

		PsiFile buildFile = e.getData(CommonDataKeys.PSI_FILE);
		if (!GradleUtils.isGradleFile(buildFile)) {
			Messages.showMessageDialog(project, MessageBundle.message("gradle.action.check.dependencies.noFile"),
					MessageBundle.message("action.check.dependencies.noEditorOpen.title"), Messages.getInformationIcon());
			return;
		}

		ProgressManager.getInstance().run(new DependencyCheckTask(project, buildFile));
	}

	@Override
	public void update(AnActionEvent e) {

		Project project = e.getProject();
		Presentation presentation = e.getPresentation();

		presentation.setText(MessageBundle.message("biz.paluch.dap.gradle.UpdateDependencies.text"));
		presentation.setDescription(MessageBundle.message("gradle.action.description"));
		presentation.setIcon(DependencyAssistantIcons.GRADLE_ICON);

		if (project == null) {
			presentation.setEnabled(false);
			return;
		}

		presentation.setVisible(GradleProjectContext.isGradleProject(project));

		VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
		presentation.setEnabled(file != null && GradleUtils.isGradleFile(file));
	}

}
