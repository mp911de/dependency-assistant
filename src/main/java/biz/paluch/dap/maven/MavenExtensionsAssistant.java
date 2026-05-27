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

import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.LookupContext;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.VersionUpgradeLookup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValuesManager;

import org.springframework.util.Assert;

/**
 * Maven Extension implementation of {@link DependencyAssistant}.
 *
 * @author Mark Paluch
 */
class MavenExtensionsAssistant implements DependencyAssistant {

	@Override
	public String getId() {
		return "maven";
	}

	@Override
	public String getDisplayName() {
		return MavenAssistant.MavenInterface.INSTANCE.getDisplayName();
	}

	@Override
	public boolean supports(Project project) {
		return true;
	}

	@Override
	public boolean supports(PsiFile file) {
		return MavenUtils.isMavenExtensionsFile(file);
	}

	@Override
	public DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator) {
		return new UpdateExtensionsProjectState(project).getAllDependencies(indicator);
	}

	@Override
	public void initializeState(Project project, ProgressIndicator indicator) {
		new UpdateExtensionsProjectState(project).readAndUpdateAll(indicator);
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Maven integration does not support " + anchor);
		}

		return new MavenExtensionContext(project, anchor.getVirtualFile());
	}

	/**
	 * {@link ProjectDependencyContext} for Maven extension files.
	 * @author Mark Paluch
	 */
	static class MavenExtensionContext implements ProjectDependencyContext {

		private final Project project;

		private final VirtualFile anchor;

		private final StateService service;

		MavenExtensionContext(Project project, VirtualFile anchor) {

			this.project = project;
			this.anchor = anchor;
			this.service = StateService.getInstance(project);
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public ProjectId getProjectId() {
			return ProjectId.of(anchor);
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return List.of(ReleaseSource.mavenCentral());
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return MavenAssistant.MavenInterface.INSTANCE;
		}

		@Override
		public void invalidateState(PsiFile file) {
			new UpdateExtensionsProjectState(project).readAndUpdate(file);
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			PsiFile psiFile = PsiManager.getInstance(project).findFile(anchor);
			if (psiFile == null) {
				return new DependencyCollector();
			}

			return collect(psiFile);
		}

		@Override
		public boolean isVersionElement(PsiElement element) {
			return MavenUtils.isVersionElement(element);
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			return CachedValuesManager.getProjectPsiDependentCache(element.getContainingFile(),
					MavenExtensionContext::createLookup);
		}

		@Override
		public void applyUpdate(PsiElement anchor, DependencyUpdate update) {
			new UpdateExtensionsFile().applyUpdate(anchor, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdateExtensionsFile().applyUpdates(psiFile, updates);
		}

		private DependencyCollector collect(PsiFile file) {

			MavenDependencyCollector dependencyCollector = new MavenDependencyCollector(service.getCache());
			DependencyCollector collector = dependencyCollector.collect(file, PropertyResolver.empty());
			collector.addAllReleaseSources(getReleaseSources());
			return collector;
		}

		@Override
		public String toString() {
			return "MavenExtensionsDependencyContext[%s]".formatted(anchor);
		}

		private static VersionUpgradeLookup createLookup(PsiFile extensions) {

			Project project = extensions.getProject();
			MavenExtensionContext buildContext = new MavenExtensionContext(project, extensions.getVirtualFile());
			LookupContext context = LookupContext.create(project, buildContext);
			return new VersionUpgradeLookup(context, new MavenExtensionsReferenceResolver(extensions));
		}

	}

}
