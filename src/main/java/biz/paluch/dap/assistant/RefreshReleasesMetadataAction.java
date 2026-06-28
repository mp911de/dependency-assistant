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
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;

/**
 * Menu action to refresh dependency release metadata.
 *
 * @author Mark Paluch
 */
public class RefreshReleasesMetadataAction extends AnAction {

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

		ProgressManager.getInstance().run(new RefreshReleaseMetadata(project));
	}

	@Override
	public void update(AnActionEvent event) {

		Project project = event.getProject();
		Presentation presentation = event.getPresentation();

		presentation.setText(MessageBundle.message("action.refresh-releases"));
		presentation.setIcon(DependencyAssistantIcons.ICON);

		if (project == null) {
			presentation.setEnabled(false);
			presentation.setVisible(false);
			return;
		}

		presentation.setEnabledAndVisible(DependencyAssistantDispatcher.supports(project));
	}

}
