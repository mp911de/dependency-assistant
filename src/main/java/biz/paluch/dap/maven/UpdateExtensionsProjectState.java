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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * Service to read Maven extension files and update the dependency state.
 *
 * @author Mark Paluch
 */
class UpdateExtensionsProjectState {

	private final Project project;

	private final StateService service;

	private final PsiManager psiManager;

	private final MavenDependencyCollector collector;

	/**
	 * Create a new {@code UpdateProjectState}.
	 * @param project the IntelliJ project.
	 */
	public UpdateExtensionsProjectState(Project project) {
		this(project, PsiManager.getInstance(project), StateService.getInstance(project));
	}

	/**
	 * Create a new {@code UpdateProjectState}.
	 * @param project the IntelliJ project.
	 * @param psiManager the PSI manager to use.
	 * @param service the dependency assistant service to update.
	 */
	public UpdateExtensionsProjectState(Project project, PsiManager psiManager, StateService service) {
		this.project = project;
		this.service = service;
		this.psiManager = psiManager;
		this.collector = new MavenDependencyCollector(service.getCache());
	}

	/**
	 * Read and update dependency state for the given file.
	 * @param file the file to inspect.
	 */
	public void readAndUpdate(PsiFile file) {
		if (MavenUtils.isMavenExtensionsFile(file)) {
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

		DependencyCollector collector = new DependencyCollector();
		doWithAllFiles(it -> {
			this.collector.doCollect(it, PropertyResolver.empty(), collector);
		}, indicator);

		collector.addAllReleaseSources(MavenUtils.getReleaseSources(project));

		return collector;
	}

	/**
	 * Update dependency state for the given Maven POM file.
	 *
	 * @param file the Maven POM file to inspect.
	 */
	public void update(PsiFile file) {

		if (!MavenUtils.isMavenExtensionsFile(file)) {
			return;
		}

		doUpdate(file);
	}

	DependencyCollector doUpdate(PsiFile file) {

		DependencyCollector collector = this.collector.collect(file, PropertyResolver.empty());
		ProjectState projectState = this.service.getProjectState(ProjectId.of(file.getVirtualFile()));
		projectState.invalidateDependencies();
		projectState.setDependencies(collector);
		return collector;
	}

	/**
	 * Invoke the given action for every supported Maven extensions file.
	 * @param action the file callback.
	 * @param indicator the progress indicator to report to.
	 */
	private void doWithAllFiles(Consumer<PsiFile> action, ProgressIndicator indicator) {

		List<VirtualFile> extensionFiles = findExtensionFiles(project);

		double current = 0;
		for (VirtualFile extensionFile : extensionFiles) {

			indicator.checkCanceled();
			PsiFile file = psiManager.findFile(extensionFile);
			if (file != null) {
				action.accept(file);
			}
			current += 1;
			indicator.setFraction(current / extensionFiles.size());
		}
	}

	private static List<VirtualFile> findExtensionFiles(Project project) {

		List<VirtualFile> result = new ArrayList<>();
		Collection<VirtualFile> xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE,
				GlobalSearchScope.projectScope(project));

		for (VirtualFile xmlFile : xmlFiles) {
			if (MavenUtils.isMavenExtensionsFile(xmlFile)) {
				result.add(xmlFile);
			}
		}

		return result;
	}

}
