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

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import org.springframework.util.StringUtils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.progress.StepsProgressIndicator;

/**
 * Service to read Gradle build files and update the dependency state.
 *
 * @author Mark Paluch
 */
class UpdateProjectState {

	private final Project project;
	private final DependencyAssistantService service;
	private final PsiManager psiManager;
	private final GradleDependencyCollector collector;

	public UpdateProjectState(Project project) {
		this(project, PsiManager.getInstance(project), DependencyAssistantService.getInstance(project));
	}

	public UpdateProjectState(Project project, DependencyAssistantService service) {
		this(project, PsiManager.getInstance(project), service);
	}

	public UpdateProjectState(Project project, PsiManager psiManager, DependencyAssistantService service) {
		this.project = project;
		this.service = service;
		this.psiManager = psiManager;
		this.collector = new GradleDependencyCollector(project);
	}

	public void readAndUpdate(PsiFile file) {
		if (GradleUtils.isGradleFile(file)) {
			ApplicationManager.getApplication().runReadAction(() -> update(file));
		}
	}

	public void readAndUpdateAll(ProgressIndicator indicator) {
		ApplicationManager.getApplication().runReadAction(() -> updateAll(indicator));
	}

	public void updateAll(ProgressIndicator indicator) {
		doWithAllFiles(this::update, indicator);
	}

	public DependencyCollector getAllDependencies(ProgressIndicator indicator) {

		DependencyCollector collector = new DependencyCollector();
		doWithAllFiles(it -> {
			this.collector.doCollect(it, collector);
		}, indicator);

		return collector;
	}

	public void doWithAllFiles(Consumer<PsiFile> action, ProgressIndicator indicator) {

		Collection<GradleProjectSettings> settings = GradleSettings.getInstance(project).getLinkedProjectsSettings();
		StepsProgressIndicator steps = new StepsProgressIndicator(indicator, 2);

		forEachSetting(settings, (psi, file) -> {
			forEachFile(psiManager, file, GradleUtils.GRADLE_SCRIPT_NAMES, action);
			forEachFile(psiManager, file, List.of(GradleUtils.LIBS_VERSIONS_TOML), action);
		}, steps);

		steps.nextStep();
		forEachSetting(settings, (psi, file) -> {
			forEachFile(psiManager, file, List.of(GradleUtils.GRADLE_PROPERTIES), action);
		}, steps);
	}

	private void forEachSetting(Collection<GradleProjectSettings> settings, BiConsumer<PsiManager, VirtualFile> action,
			ProgressIndicator indicator) {

		double current = 0;
		LocalFileSystem lfs = LocalFileSystem.getInstance();

		for (GradleProjectSettings setting : settings) {

			indicator.setText(
					MessageBundle.message("action.check.dependencies.progress.collecting", setting.getExternalProjectPath()));

			String path = setting.getExternalProjectPath();
			if (!StringUtils.hasText(path)) {
				continue;
			}
			VirtualFile directory = lfs.findFileByPath(path);
			if (directory == null || !directory.isDirectory()) {
				continue;
			}

			action.accept(psiManager, directory);

			current += 1;
			indicator.setFraction(current / settings.size());
		}
	}

	public void update(PsiFile file) {

		if (GradleUtils.isGradleFile(file)) {

			GradleProjectContext context = GradleProjectContext.of(project, file);

			if (context.isAvailable()) {
				DependencyCollector collector = this.collector.collect(file);
				ProjectState projectState = this.service.getProjectState(context.getProjectId());

				projectState.setDependencies(collector);
			}
		}
	}

	private void forEachFile(PsiManager psiManager, VirtualFile directory, Collection<String> fileNames,
			Consumer<PsiFile> action) {

		for (String name : fileNames) {
			VirtualFile child = directory.findChild(name);
			if (child == null || child.isDirectory()) {
				continue;
			}

			PsiFile file = psiManager.findFile(child);
			if (file != null) {
				action.accept(file);
			}
		}
	}

}
