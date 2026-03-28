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
package biz.paluch.dap;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Tools menu action to check Maven dependency versions for the current POM.
 */
public class UpdateMavenDependenciesAction extends AnAction {

	@Override
	public void actionPerformed(AnActionEvent e) {

		Project project = e.getProject();
		if (project == null) {
			return;
		}
		Editor editor = e.getData(CommonDataKeys.EDITOR);
		if (editor == null) {
			Messages.showMessageDialog(project, MessageBundle.message("action.check.dependencies.noEditorOpen"),
					MessageBundle.message("action.check.dependencies.noEditorOpen.title"),
					Messages.getInformationIcon());
			return;
		}

		String pomContent = editor.getDocument().getText();
		VirtualFile pomFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

		ProgressManager.getInstance().run(new DependencyCheckTask(project, pomFile, pomContent));
	}

	@Override
	public void update(AnActionEvent e) {

		Project project = e.getProject();
		Presentation presentation = e.getPresentation();

		presentation.setText(MessageBundle.message("biz.paluch.dap.UpdateMavenDependencies.text"));
		presentation.setDescription(MessageBundle.message("action.description"));
		presentation.setIcon(MavenUpdater.ICON);
		presentation.setVisible(true);

		if (project == null) {
			presentation.setEnabled(false);
			return;
		}

		Editor editor = e.getData(CommonDataKeys.EDITOR);
		if (editor == null) {
			presentation.setEnabled(false);
			return;
		}

		presentation.setEnabled(MavenUtils.isMavenPomFile(project, editor.getDocument()));
	}

}
