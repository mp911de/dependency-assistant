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

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;

/**
 * Tools menu action to check dependency versions for the active build file.
 *
 * @author Mark Paluch
 */
public class UpgradeDependenciesAction extends AnAction implements DumbAware {

	@Override
	public ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void actionPerformed(AnActionEvent event) {

		Project project = event.getProject();
		if (project == null) {
			return;
		}

		Editor editor = event.getData(CommonDataKeys.EDITOR);
		if (editor == null) {
			Messages.showMessageDialog(project, MessageBundle.message("action.check.dependencies.noEditorOpen"),
					MessageBundle.message("action.check.dependencies.noEditorOpen.title"),
					Messages.getInformationIcon());
			return;
		}

		PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);

		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project, psiFile);
		if (psiFile == null || context.isAbsent()) {
			Messages.showMessageDialog(project, MessageBundle.message("action.check.dependencies.noSupportedFile"),
					MessageBundle.message("action.check.dependencies.noEditorOpen.title"),
					Messages.getInformationIcon());
			return;
		}

		ProgressManager.getInstance().run(new DependencyCheckTask(project, psiFile.getVirtualFile(), context));
	}

	@Override
	public void update(AnActionEvent event) {

		Project project = event.getProject();
		Presentation presentation = event.getPresentation();

		presentation.setText(MessageBundle.message("intention.UpgradeDependencies.text"));
		presentation.setDescription(MessageBundle.message("action.description"));
		presentation.setIcon(DependencyAssistantIcons.ICON);

		if (project == null) {
			presentation.setEnabled(false);
			presentation.setVisible(false);
			return;
		}

		boolean supports = DependencyAssistantDispatcher.supports(project);
		presentation.setVisible(supports);
		presentation.setEnabled(supports);
	}

}
