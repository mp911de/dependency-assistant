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

package biz.paluch.dap.npm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * Service to read npm {@code package.json} files and update the dependency
 * state.
 *
 * @author Mark Paluch
 */
class UpdateProjectState {

	private final Project project;

	private final StateService service;

	private final PsiManager psiManager;

	UpdateProjectState(Project project) {
		this(project, PsiManager.getInstance(project), StateService.getInstance(project));
	}

	UpdateProjectState(Project project, PsiManager psiManager, StateService service) {
		this.project = project;
		this.service = service;
		this.psiManager = psiManager;
	}

	/**
	 * Read and update dependency state for every supported {@code package.json}.
	 * @param indicator the progress indicator to report to.
	 */
	public void readAndUpdateAll(ProgressIndicator indicator) {
		ApplicationManager.getApplication().runReadAction(() -> updateAll(indicator));
	}

	/**
	 * Update dependency state for every supported {@code package.json}.
	 * @param indicator the progress indicator to report to.
	 */
	public void updateAll(ProgressIndicator indicator) {
		doWithAllFiles(this::update, indicator);
	}

	/**
	 * Refresh dependency state for every supported {@code package.json} and return
	 * aggregate release-source metadata for the project.
	 * @param indicator the progress indicator to report to.
	 */
	public DependencyCollector getAllDependencies(ProgressIndicator indicator) {

		DependencyCollector aggregate = new DependencyCollector();
		NpmDependencyCollector collector = new NpmDependencyCollector(service.getCache());

		doWithAllFiles(psiFile -> {
			collector.doCollect(psiFile, aggregate);
		}, indicator);

		aggregate.addAllReleaseSources(NpmProjectContext.getReleaseSources(project));

		return aggregate;
	}

	/**
	 * Update dependency state for the given file.
	 * @param file the {@code package.json} file to inspect.
	 */
	public void update(PsiFile file) {

		if (!NpmUtils.isPackageJson(file)) {
			return;
		}

		DependencyCollector collector = new NpmDependencyCollector(service.getCache()).collect(file);
		NpmProjectContext context = NpmProjectContext.of(file);
		ProjectState projectState = this.service.getProjectState(context.getProjectId());
		projectState.invalidateDependencies();
		projectState.setDependencies(collector);
	}

	private void doWithAllFiles(Consumer<PsiFile> action, ProgressIndicator indicator) {

		List<VirtualFile> packageFiles = findPackageJsonFiles(project);

		double current = 0;
		for (VirtualFile file : packageFiles) {

			indicator.checkCanceled();
			if (!file.getPath().contains("node_modules")) {

				PsiFile psiFile = psiManager.findFile(file);
				if (psiFile != null) {
					action.accept(psiFile);
				}
			}
			current += 1;
			if (!packageFiles.isEmpty()) {
				indicator.setFraction(current / packageFiles.size());
			}
		}
	}

	private static List<VirtualFile> findPackageJsonFiles(Project project) {

		List<VirtualFile> result = new ArrayList<>();
		Collection<VirtualFile> jsonFiles = FileTypeIndex.getFiles(JsonFileType.INSTANCE,
				GlobalSearchScope.projectScope(project));

		for (VirtualFile file : jsonFiles) {
			if (NpmUtils.isPackageJson(file)) {
				result.add(file);
			}
		}
		return result;
	}

}
