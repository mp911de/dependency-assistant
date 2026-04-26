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
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValuesManager;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Gradle implementation of {@link DependencyAssistant}.
 *
 * @author Mark Paluch
 */
class GradleAssistant implements DependencyAssistant {

	@Override
	public String getId() {
		return "gradle";
	}

	@Override
	public String getDisplayName() {
		return "Gradle";
	}

	@Override
	public boolean supports(Project project) {
		return GradleProjectContext.isGradleProject(project);
	}

	@Override
	public boolean supports(VirtualFile file) {
		return GradleUtils.isGradleFile(file);
	}

	@Override
	public boolean supports(PsiFile file) {
		return GradleUtils.isGradleFile(file);
	}

	@Override
	public DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator) {
		return new UpdateProjectState(project).getAllDependencies(indicator);
	}

	@Override
	public void initializeState(Project project, ProgressIndicator indicator) {
		new UpdateProjectState(project).readAndUpdateAll(indicator);
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Gradle integration does not support " + anchor);
		}

		return CachedValuesManager.getProjectPsiDependentCache(anchor,
				it -> createContext(project, anchor.getVirtualFile()));
	}

	private ProjectDependencyContext createContext(Project project, VirtualFile anchor) {

		GradleProjectContext context = GradleProjectContext.of(project, anchor);
		if (!context.isAvailable()) {
			throw new IllegalStateException("No Gradle project found for " + anchor);
		}

		return new GradleDependencyContext(project, anchor, context, context.getProjectId());
	}

	private static class GradleDependencyContext implements ProjectDependencyContext {

		private final Project project;

		private final @Nullable VirtualFile anchor;

		private final @Nullable GradleProjectContext delegate;

		private final ProjectId projectId;

		private final DependencyAssistantService service;

		GradleDependencyContext(Project project, @Nullable VirtualFile anchor, @Nullable GradleProjectContext delegate,
				ProjectId projectId) {

			this.project = project;
			this.anchor = anchor;
			this.delegate = delegate;
			this.projectId = projectId;
			this.service = DependencyAssistantService.getInstance(project);
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public ProjectId getProjectId() {
			return projectId;
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return delegate.getReleaseSources();
		}

		@Override
		public void invalidateState(PsiFile file) {

			if (!GradleUtils.isGradleFile(file)) {
				return;
			}

			GradleProjectContext changedContext = GradleProjectContext.of(project, file);
			if (!changedContext.isAvailable()) {
				return;
			}

			ProjectState projectState = service.getProjectState(changedContext.getProjectId());
			projectState.invalidateDependencies();
			projectState.setDependencies(new GradleDependencyCollector(project).collect(file));
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			if (anchor == null) {
				return new UpdateProjectState(project, service).getAllDependencies(indicator);
			}

			PsiFile psiFile = PsiManager.getInstance(project).findFile(anchor);
			if (psiFile == null) {
				return new DependencyCollector();
			}

			return new GradleDependencyCollector(project).collect(psiFile);
		}

		@Override
		public ArtifactReference resolveReference(PsiElement element) {
			return VersionUpgradeLookupService.create(element).resolveArtifactReference(element);
		}

		@Override
		public VersionUpgradeLookupSupport getLookup(PsiElement element) {
			return VersionUpgradeLookupService.create(element);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			Assert.state(anchor != null, "Cannot apply Gradle updates without a build file");
			new UpdateGradleFile(project).applyUpdates(psiFile, updates);
		}

	}

}
