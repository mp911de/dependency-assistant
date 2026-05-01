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

package biz.paluch.dap.github;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.github.GitRepositoryResolver.GitRepositoryMetadata;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.yaml.YAMLFileType;

/**
 * Service to read supported GitHub Actions YAML files and update dependency
 * state.
 *
 * @author Mark Paluch
 */
class UpdateProjectState {

	private final Project project;

	private final DependencyAssistantService service;

	private final PsiManager psiManager;

	/**
	 * Create a new {@code UpdateProjectState}.
	 * @param project the IntelliJ project.
	 */
	public UpdateProjectState(Project project) {
		this(project, PsiManager.getInstance(project), DependencyAssistantService.getInstance(project));
	}

	/**
	 * Create a new {@code UpdateProjectState}.
	 * @param project the IntelliJ project.
	 * @param psiManager the PSI manager to use.
	 * @param service the dependency assistant service to update.
	 */
	public UpdateProjectState(Project project, PsiManager psiManager, DependencyAssistantService service) {
		this.project = project;
		this.service = service;
		this.psiManager = psiManager;
	}

	/**
	 * Read and update dependency state for all supported GitHub Actions files.
	 * @param indicator the progress indicator to report to.
	 */
	public void readAndUpdateAll(ProgressIndicator indicator) {
		ApplicationManager.getApplication().runReadAction(() -> updateAll(indicator));
	}

	/**
	 * Update dependency state for all supported GitHub Actions files.
	 * @param indicator the progress indicator to report to.
	 */
	public void updateAll(ProgressIndicator indicator) {
		doWithAllFiles(this::update, indicator);
	}

	/**
	 * Refresh dependency state for all supported GitHub Actions files and return
	 * aggregate release-source metadata for the project.
	 * @param indicator the progress indicator to report to.
	 */
	public DependencyCollector getAllDependencies(ProgressIndicator indicator) {

		DependencyCollector aggregate = new DependencyCollector();
		DependencyAssistantService service = DependencyAssistantService.getInstance(project);
		GitRepositoryResolver repositoryResolver = new GitRepositoryResolver(project);
		GitHubDependencyCollector collector = new GitHubDependencyCollector();
		Map<GitRepositoryMetadata, GitHubReleaseSource> releaseSources = new java.util.HashMap<>();

		doWithAllFiles(psiFile -> {

			DependencyCollector fileCollector = new DependencyCollector();
			collector.doCollect(psiFile, fileCollector);
			GitHubProjectContext context = GitHubProjectContext.of(project, psiFile.getVirtualFile());
			GitRepositoryMetadata coordinates = repositoryResolver.resolveOwnerAndRepository(psiFile.getVirtualFile());
			if (coordinates != null) {
				releaseSources.computeIfAbsent(coordinates,
						it -> GitHubReleaseSource.from(project, coordinates.host()));
			}

			if (coordinates != null) {
				releaseSources.computeIfAbsent(coordinates,
						it -> GitHubReleaseSource.from(project, coordinates.host()));
			}
			service.getProjectState(context.getProjectId()).setDependencies(fileCollector);
		}, indicator);

		if (releaseSources.isEmpty()) {
			aggregate.addAllReleaseSources(GitHubReleaseSource.from(project));
		} else {
			aggregate.addAllReleaseSources(releaseSources.values());
		}
		return aggregate;
	}

	/**
	 * Update dependency state for the given GitHub Actions file.
	 * @param file the GitHub Actions file to inspect.
	 */
	public void update(PsiFile file) {

		if (GitHubUtils.isWorkflowFile(file)) {

			GitHubProjectContext context = GitHubProjectContext.of(project, file.getVirtualFile());
			DependencyCollector collector = new GitHubDependencyCollector().collect(file);
			ProjectState projectState = this.service.getProjectState(context.getProjectId());

			projectState.setDependencies(collector);
		}
	}

	/**
	 * Invoke the given action for every supported GitHub Actions file.
	 * @param action the file callback.
	 * @param indicator the progress indicator to report to.
	 */
	private void doWithAllFiles(Consumer<PsiFile> action, ProgressIndicator indicator) {

		List<VirtualFile> workflowFiles = findWorkflowFiles(project);

		double current = 0;
		for (VirtualFile workflowFile : workflowFiles) {

			PsiFile file = psiManager.findFile(workflowFile);
			if (file != null) {
				action.accept(file);
			}
			current += 1;
			indicator.setFraction(current / workflowFiles.size());
		}
	}

	/**
	 * Enumerate all supported GitHub Actions files in the project's content scope.
	 */
	private static List<VirtualFile> findWorkflowFiles(Project project) {

		List<VirtualFile> result = new ArrayList<>();
		Collection<VirtualFile> yamlFiles = FileTypeIndex.getFiles(YAMLFileType.YML,
				GlobalSearchScope.projectScope(project));

		for (VirtualFile yaml : yamlFiles) {
			if (GitHubUtils.isWorkflowFile(yaml)) {
				result.add(yaml);
			}
		}

		return result;
	}

}
