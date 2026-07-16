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

package biz.paluch.dap.assistant.review;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.assistant.Notifications;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.util.IncorrectOperationException;

/**
 * Menu action that creates or opens a project-local
 * {@code dependencyfile.json}, seeding a fresh descriptor with the project's
 * known artifacts.
 *
 * @author Mark Paluch
 */
public class CreateDependencyfileAction extends AnAction implements DumbAware, Iconable {

	@Override
	public ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public Icon getIcon(int flags) {
		return AllIcons.FileTypes.Json;
	}

	@Override
	public void update(AnActionEvent event) {

		Project project = event.getProject();
		Presentation presentation = event.getPresentation();

		presentation.setText(MessageBundle.message("dependencyfile.create.action"));
		presentation.setDescription(MessageBundle.message("dependencyfile.create.description"));
		presentation.setIcon(getIcon(0));

		presentation.setEnabledAndVisible(project != null
				&& project.getBasePath() != null
				&& DependencyAssistantDispatcher.supports(project));
	}

	@Override
	public void actionPerformed(AnActionEvent event) {

		Project project = event.getProject();
		if (project == null) {
			return;
		}

		try {
			new DependencyfileArtifactWriter(project).createOrOpen(artifactIds(project));
		} catch (IOException | IncorrectOperationException ex) {
			Notifications.error(project,
					MessageBundle.message("dependencyfile.create.error.title"),
					MessageBundle.message("dependencyfile.create.error", Notifications.errorMessage(ex)));
		}
	}

	private static Set<ArtifactId> artifactIds(Project project) {

		TreeSet<ArtifactId> artifactIds = new TreeSet<>(ArtifactId.COMPARATOR);
		StateService.getInstance(project).doWithDependencies(dependency -> artifactIds.add(dependency.getArtifactId()));
		return artifactIds;
	}

}
