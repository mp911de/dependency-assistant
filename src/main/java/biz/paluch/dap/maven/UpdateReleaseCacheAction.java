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

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.MessageBundle;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;

/**
 * Menu action to refresh Gradle dependency versions.
 *
 * @author Mark Paluch
 */
public class UpdateReleaseCacheAction extends AnAction {

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
		ProgressManager.getInstance().run(new ReleasesRetrievalTask(project));
	}

	@Override
	public void update(AnActionEvent e) {

		Project project = e.getProject();
		Presentation presentation = e.getPresentation();

		presentation.setText(MessageBundle.message("action.update.releases"));
		presentation.setIcon(DependencyAssistantIcons.ICON);

		if (project == null) {
			presentation.setEnabled(false);
			return;
		}

		presentation.setEnabledAndVisible(MavenProjectContext.isMavenProject(project));
	}

}
