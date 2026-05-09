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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * Service to read Maven build files and update the dependency state.
 *
 * @author Mark Paluch
 */
class UpdateProjectState {

	private final Project project;

	private final StateService service;

	private final PsiManager psiManager;

	private final MavenDependencyCollector collector;

	private final MavenProjectsManager manager;

	/**
	 * Create a new {@code UpdateProjectState}.
	 * @param project the IntelliJ project.
	 */
	public UpdateProjectState(Project project) {
		this(project, PsiManager.getInstance(project), StateService.getInstance(project));
	}

	/**
	 * Create a new {@code UpdateProjectState}.
	 * @param project the IntelliJ project.
	 * @param service the dependency assistant service to update.
	 */
	public UpdateProjectState(Project project, StateService service) {
		this(project, PsiManager.getInstance(project), service);
	}

	/**
	 * Create a new {@code UpdateProjectState}.
	 * @param project the IntelliJ project.
	 * @param psiManager the PSI manager to use.
	 * @param service the dependency assistant service to update.
	 */
	public UpdateProjectState(Project project, PsiManager psiManager, StateService service) {
		this(project, psiManager, service, MavenProjectsManager.getInstance(project));
	}

	/**
	 * Create a new {@code UpdateProjectState}.
	 * @param project the IntelliJ project.
	 * @param psiManager the PSI manager to use.
	 * @param service the dependency assistant service to update.
	 * @param projectsManager the Maven projects manager to inspect.
	 */
	public UpdateProjectState(Project project, PsiManager psiManager, StateService service,
			MavenProjectsManager projectsManager) {
		this.project = project;
		this.service = service;
		this.psiManager = psiManager;
		this.manager = projectsManager;
		this.collector = new MavenDependencyCollector(service.getCache(), Map.of());
	}

	/**
	 * Read and update dependency state for the given file.
	 * @param file the file to inspect.
	 */
	public void readAndUpdate(PsiFile file) {
		if (MavenUtils.isMavenPomFile(file)) {
			ApplicationManager.getApplication().runReadAction(() -> update(file));
		}
	}

	/**
	 * Read and update dependency state for all Maven projects.
	 * @param indicator the progress indicator to report to.
	 */
	public void readAndUpdateAll(ProgressIndicator indicator) {
		ApplicationManager.getApplication().runReadAction(() -> updateAll(indicator));
	}

	/**
	 * Update dependency state for all Maven projects.
	 * @param indicator the progress indicator to report to.
	 */
	public void updateAll(ProgressIndicator indicator) {
		doWithAllFiles(this::update, indicator);
	}

	/**
	 * Return dependencies collected from all Maven projects.
	 * @param indicator the progress indicator to report to.
	 */
	public DependencyCollector getAllDependencies(ProgressIndicator indicator) {

		MavenProperties properties = new MavenProperties(project);
		DependencyCollector collector = new DependencyCollector();
		doWithAllFiles(it -> {

			MavenProjectContext context = MavenProjectContext.of(project, it);
			PropertyResolver propertyResolver = properties.getPropertyResolver(context.getMavenProject(), (XmlFile) it);

			this.collector.doCollect(it, collector, propertyResolver);
		}, indicator);

		collector.addAllReleaseSources(MavenUtils.getReleaseSources(project));

		return collector;
	}

	/**
	 * Invoke the given action for every Maven POM file.
	 * @param action the file callback.
	 * @param indicator the progress indicator to report to.
	 */
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

			indicator.checkCanceled();
			indicator
					.setText(MessageBundle.message("action.index-dependencies.indexing.assistant",
							mavenProject.getMavenId()));

			action.accept(psiManager, mavenProject.getFile());

			current += 1;
			indicator.setFraction(current / projects.size());
		}
	}

	/**
	 * Update dependency state for the given Maven POM file.
	 * @param file the Maven POM file to inspect.
	 */
	public void update(PsiFile file) {

		if (!MavenUtils.isMavenPomFile(file)) {
			return;
		}

		MavenProjectContext context = MavenProjectContext.of(project, file);

		if (context.isAvailable()) {
			DependencyCollector collector = this.collector.collect(file);
			ProjectState projectState = this.service.getProjectState(context.getProjectId());
			projectState.invalidateDependencies();
			projectState.setDependencies(collector);
		}
	}

}
