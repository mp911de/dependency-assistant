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

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * Service to read Gradle build files and update the dependency state.
 *
 * @author Mark Paluch
 */
class UpdateProjectState {

	private final Project project;
	private final DependencyAssistantService service;
	private final PsiManager psiManager;
	private final MavenDependencyCollector collector = new MavenDependencyCollector(Map.of());
	private final MavenProjectsManager manager;

	public UpdateProjectState(Project project) {
		this(project, PsiManager.getInstance(project), DependencyAssistantService.getInstance(project));
	}

	public UpdateProjectState(Project project, DependencyAssistantService service) {
		this(project, PsiManager.getInstance(project), service);
	}

	public UpdateProjectState(Project project, PsiManager psiManager, DependencyAssistantService service) {
		this(project, psiManager, service, MavenProjectsManager.getInstance(project));
	}

	public UpdateProjectState(Project project, PsiManager psiManager, DependencyAssistantService service,
			MavenProjectsManager projectsManager) {
		this.project = project;
		this.service = service;
		this.psiManager = psiManager;
		this.manager = projectsManager;
	}

	public void readAndUpdate(PsiFile file) {
		if (MavenUtils.isMavenPomFile(file)) {
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

		List<MavenProject> projects = manager.getProjects();

		forEachProject(projects, (psi, file) -> {

			PsiFile psiFile = psiManager.findFile(file);
			if (psiFile != null) {
				action.accept(psiFile);
			}
		}, indicator);
	}

	private void forEachProject(Collection<MavenProject> projects, BiConsumer<PsiManager, VirtualFile> action,
			ProgressIndicator indicator) {

		double current = 0;
		for (MavenProject mavenProject : projects) {

			indicator
					.setText(MessageBundle.message("action.check.dependencies.progress.collecting", mavenProject.getMavenId()));

			action.accept(psiManager, mavenProject.getFile());

			current += 1;
			indicator.setFraction(current / projects.size());
		}
	}

	public void update(PsiFile file) {

		if (MavenUtils.isMavenPomFile(file)) {

			MavenProjectContext context = MavenProjectContext.of(project, file);

			if (context.isAvailable()) {
				DependencyCollector collector = this.collector.collect(file);
				ProjectState projectState = this.service.getProjectState(context.getProjectId());

				projectState.setDependencies(collector);
			}
		}
	}

}
