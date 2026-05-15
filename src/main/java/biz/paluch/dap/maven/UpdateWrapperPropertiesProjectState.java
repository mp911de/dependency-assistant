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
import java.util.function.Consumer;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.ProjectScope;

/**
 * Service to read Maven wrapper files and update the dependency state.
 *
 * @author Mark Paluch
 */
class UpdateWrapperPropertiesProjectState {

	private final Project project;

	private final StateService service;

	private final PsiManager psiManager;

	private final MavenWrapperDependencyCollector collector;

	/**
	 * Create a new {@code UpdateWrapperPropertiesProjectState}.
	 * @param project the IntelliJ project.
	 */
	public UpdateWrapperPropertiesProjectState(Project project) {
		this(project, PsiManager.getInstance(project), StateService.getInstance(project));
	}

	/**
	 * Create a new {@code UpdateWrapperPropertiesProjectState}.
	 * @param project the IntelliJ project.
	 * @param psiManager the PSI manager to use.
	 * @param service the dependency assistant service to update.
	 */
	public UpdateWrapperPropertiesProjectState(Project project, PsiManager psiManager, StateService service) {
		this.project = project;
		this.service = service;
		this.psiManager = psiManager;
		this.collector = new MavenWrapperDependencyCollector();
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
			this.collector.doCollect(it, collector);
		}, indicator);

		return collector;
	}

	/**
	 * Invoke the given action for every Maven wrapper properties file in the
	 * project.
	 * @param action the file callback.
	 * @param indicator the progress indicator to report to.
	 */
	public void doWithAllFiles(Consumer<PsiFile> action, ProgressIndicator indicator) {

		double current = 0;
		Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(MavenUtils.WRAPPER_FILENAME,
				ProjectScope.getProjectScope(project));
		for (VirtualFile file : files) {
			indicator.checkCanceled();
			indicator.setText(MessageBundle.message("action.index-dependencies.indexing.assistant",
					"Wrapper"));

			PsiFile psiFile = psiManager.findFile(file);
			if (MavenUtils.isWrapperFileExact(file) && psiFile != null) {
				action.accept(psiFile);
			}

			current += 1;
			indicator.setFraction(current / files.size());
		}
	}

	/**
	 * Update dependency state for the given Maven wrapper properties file.
	 * @param file the {@code maven-wrapper.properties} file to inspect.
	 */
	public DependencyCollector update(PsiFile file) {

		if (!MavenUtils.isWrapperFile(file)) {
			return new DependencyCollector();
		}

		ProjectId projectId = MavenProjectContext.createWrapperProjectId(file.getVirtualFile());
		DependencyCollector collector = this.collector.collect(file);
		ProjectState projectState = this.service.getProjectState(projectId);
		projectState.invalidateDependencies();
		projectState.setDependencies(collector);

		return collector;
	}

}
