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

package biz.paluch.dap.antora;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
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
 * Service to read Antora playbook files and update the dependency state.
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
	 * Read and update dependency state for every supported Antora playbook.
	 * @param contextFunction the function returning the project dependency context
	 * for a given playbook file.
	 * @param indicator the progress indicator to report to.
	 */
	public void readAndUpdateAll(Function<PsiFile, ProjectDependencyContext> contextFunction,
			ProgressIndicator indicator) {
		ApplicationManager.getApplication().runReadAction(() -> updateAll(contextFunction, indicator));
	}

	/**
	 * Update dependency state for every supported Antora playbook.
	 * @param contextFunction the function returning the project dependency context
	 * for a given playbook file.
	 * @param indicator the progress indicator to report to.
	 */
	public void updateAll(Function<PsiFile, ProjectDependencyContext> contextFunction, ProgressIndicator indicator) {
		doWithAllFiles(file -> update(file, contextFunction), indicator);
	}

	/**
	 * Refresh dependency state for every supported Antora playbook and return
	 * aggregate release-source metadata for the project.
	 * @param indicator the progress indicator to report to.
	 */
	public DependencyCollector getAllDependencies(ProgressIndicator indicator) {

		DependencyCollector aggregate = new DependencyCollector();
		AntoraDependencyCollector collector = new AntoraDependencyCollector();

		doWithAllFiles(psiFile -> collector.doCollect(psiFile, aggregate), indicator);

		aggregate.addAllReleaseSources(AntoraProjectContext.getReleaseSources(project));
		return aggregate;
	}

	/**
	 * Update dependency state for the given file.
	 * @param file the Antora playbook file to inspect.
	 * @param contextFunction the function returning the project dependency context
	 * for the playbook file.
	 */
	public void update(PsiFile file, Function<PsiFile, ProjectDependencyContext> contextFunction) {

		if (!AntoraUtils.isPlaybookFile(file)) {
			return;
		}

		ProjectDependencyContext context = contextFunction.apply(file);
		if (context.isAbsent()) {
			return;
		}

		DependencyCollector collector = new AntoraDependencyCollector().collect(file);
		ProjectState projectState = service.getProjectState(context.getProjectId());
		Cache cache = service.getCache();

		for (DeclaredDependency declaration : collector.getDeclarations()) {

			Dependency dependency = context.resolveDependency(declaration,
					cache.getReleases(declaration.getArtifactId()));
			if (dependency != null) {

				DeclarationSource declarationSource = dependency.getDeclarationSources().iterator().next();
				VersionSource versionSource = dependency.getVersionSources().iterator().next();
				collector.registerUsage(dependency.getArtifactId(), dependency.getCurrentVersion(),
						declarationSource, versionSource);
			}
		}

		projectState.invalidateDependencies();
		projectState.setDependencies(collector);
	}

	private void doWithAllFiles(Consumer<PsiFile> action, ProgressIndicator indicator) {

		List<VirtualFile> playbookFiles = findPlaybookFiles(project);

		double current = 0;
		for (VirtualFile playbookFile : playbookFiles) {

			indicator.checkCanceled();
			PsiFile file = psiManager.findFile(playbookFile);
			if (file != null) {
				action.accept(file);
			}
			current += 1;
			indicator.setFraction(current / playbookFiles.size());
		}
	}

	private static List<VirtualFile> findPlaybookFiles(Project project) {

		List<VirtualFile> result = new ArrayList<>();
		Collection<VirtualFile> yamlFiles = FileTypeIndex.getFiles(YAMLFileType.YML,
				GlobalSearchScope.projectScope(project));

		for (VirtualFile yaml : yamlFiles) {
			if (AntoraUtils.isPlaybookFile(yaml)) {
				result.add(yaml);
			}
		}
		return result;
	}

}
