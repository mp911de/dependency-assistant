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

import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.assistant.check.UpgradeScope;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Action to upgrade dependencies across an {@link UpgradeScope upgrade scope},
 * surfaced both in the Tools menu and the Project View popup.
 *
 * <p>The scope is resolved by {@link UpgradeScopeResolver}: an explicit Project
 * View selection, else the active editor's build file, else the whole project.
 * The action is a thin shell; all resolution logic lives in the resolver.
 *
 * @author Mark Paluch
 */
public class UpgradeDependenciesAction extends AnAction implements DumbAware, Iconable {

	@Override
	public ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public Icon getIcon(int flags) {
		return DependencyAssistantIcons.ICON;
	}

	@Override
	public void update(AnActionEvent event) {

		Project project = event.getProject();
		Presentation presentation = event.getPresentation();

		presentation.setText(MessageBundle.message("intention.UpgradeDependencies.text"));
		presentation.setDescription(MessageBundle.message("action.description"));
		presentation.setIcon(getIcon(0));

		if (project == null) {
			presentation.setEnabled(false);
			presentation.setVisible(false);
			return;
		}

		boolean supports = DependencyAssistantDispatcher.supports(project);
		presentation.setVisible(supports);
		presentation.setEnabled(supports);
	}

	@Override
	public void actionPerformed(AnActionEvent event) {

		Project project = event.getProject();
		if (project == null) {
			return;
		}

		VirtualFile[] selectedFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
		List<VirtualFile> selection = (selectedFiles != null ? List.of(selectedFiles) : List.of());
		selection = selection.stream().filter(it -> !it.isDirectory() && it.isValid()).toList();
		PsiFile editorFile = event.getData(CommonDataKeys.PSI_FILE);

		ProgressManager.getInstance().run(new DependencyCheckTask(project, new UpgradeRequest(selection, editorFile)));
	}

}
