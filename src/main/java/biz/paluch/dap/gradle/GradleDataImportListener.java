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

import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StepsProgressIndicator;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jspecify.annotations.Nullable;

/**
 * Declarative project listener re-indexing the Gradle assistant's project state
 * after a completed Gradle data import so members resolved from the persisted
 * dependency graph surface in highlighting.
 *
 * @author Mark Paluch
 * @see ProjectStateIndexer#refreshAfterImport
 */
class GradleDataImportListener implements ProjectDataImportListener {

	private final Project project;

	GradleDataImportListener(Project project) {
		this.project = project;
	}

	@Override
	public void onImportFinished(@Nullable String projectPath) {

		if (projectPath == null
				|| GradleSettings.getInstance(project).getLinkedProjectSettings(projectPath) == null) {
			return;
		}

		new Task.Backgroundable(project, MessageBundle.message("refreshAfterImport.task"), true) {

			@Override
			public void run(ProgressIndicator indicator) {
				List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll();

				if (assistants.isEmpty()) {
					return;
				}

				StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, assistants.size());
				steps.setIndeterminate(false);
				ProjectStateIndexer indexer = new ProjectStateIndexer(project, indicator);
				for (DependencyAssistant assistant : assistants) {
					if (assistant instanceof GradleAssistant) {
						indexer.refreshAfterImport(assistant);
					}
					steps.nextStep();
				}
			}

		}.queue();
	}

}
