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

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.LookupContext;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.VersionUpgradeLookup;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValuesManager;
import icons.MavenIcons;

import org.springframework.util.Assert;

/**
 * Maven implementation of {@link DependencyAssistant}.
 *
 * @author Mark Paluch
 */
class MavenAssistant implements DependencyAssistant {

	@Override
	public String getId() {
		return "maven";
	}

	@Override
	public String getDisplayName() {
		return MavenInterface.INSTANCE.getDisplayName();
	}

	@Override
	public boolean supports(Project project) {
		return true;
	}

	@Override
	public boolean supports(PsiFile file) {
		return MavenUtils.isMavenPomFile(file);
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
			throw new IllegalStateException("Maven integration does not support " + anchor);
		}

		MavenProjectContext context = anchor.getUserData(MavenProjectContext.KEY);
		if (context == null) {
			context = CachedValuesManager.getProjectPsiDependentCache(anchor,
					it -> MavenProjectContext.of(project, anchor.getVirtualFile()));
		}

		return new MavenDependencyContext(project, anchor, anchor.getVirtualFile(),
				context);
	}

	private static VersionUpgradeLookup createLookup(PsiFile pom) {

		Project project = pom.getProject();
		MavenProjectContext buildContext = MavenProjectContext.of(project, pom);
		LookupContext context = LookupContext.create(project, buildContext);
		return new VersionUpgradeLookup(context, new MavenArtifactReferenceResolver(context, pom, buildContext));
	}

	static class MavenDependencyContext implements ProjectDependencyContext {

		private final MavenProjectContext projectContext;

		private final Project project;

		private final MavenPropertyResolver propertyResolver;

		private final VirtualFile anchor;

		private final StateService service;

		MavenDependencyContext(Project project, PsiFile pomFile, VirtualFile anchor,
				MavenProjectContext projectContext) {

			this.project = project;
			this.propertyResolver = MavenPropertyResolver.create(projectContext, pomFile);
			this.projectContext = projectContext;
			this.anchor = anchor;
			this.service = StateService.getInstance(project);
		}

		@Override
		public boolean isAvailable() {
			return projectContext.isAvailable();
		}

		@Override
		public ProjectId getProjectId() {
			return projectContext.getProjectId();
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return projectContext.getReleaseSources();
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return MavenInterface.INSTANCE;
		}

		@Override
		public void invalidateState(PsiFile file) {
			new UpdateProjectState(project).readAndUpdate(file, propertyResolver);
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			PsiFile psiFile = projectContext.findFile(anchor);
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
					MavenAssistant::createLookup);
		}

		@Override
		public void applyUpdate(PsiElement anchor, DependencyUpdate update) {
			new UpdatePomFile(propertyResolver).applyUpdate(anchor, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdatePomFile(propertyResolver).applyUpdates(psiFile, updates);
		}

		private DependencyCollector collect(PsiFile file) {

			MavenDependencyCollector dependencyCollector = new MavenDependencyCollector(service.getCache());
			DependencyCollector collector = dependencyCollector.collect(file, propertyResolver);
			collector.addAllReleaseSources(getReleaseSources());
			return collector;
		}

		@Override
		public String toString() {
			return "MavenDependencyContext[%s] %s".formatted(anchor, projectContext);
		}

	}

	/**
	 * Maven-specific user interface support.
	 */
	enum MavenInterface implements InterfaceAssistant {

		INSTANCE;

		@Override
		public String getDisplayName() {
			return MessageBundle.message("assistant.maven");
		}

		@Override
		public String getDisplayName(VirtualFile file) {
			return getDisplayName();
		}

		@Override
		public Icon getGutterIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.UPGRADE_MAVEN_ICON;
		}

		@Override
		public Icon getNavigateIcon(ArtifactDeclaration declaration) {

			if (declaration.getVersionSource() instanceof VersionSource.VersionProperty) {
				return DependencyAssistantIcons.PROPERTY_NAVIGATE;
			}

			return DependencyAssistantIcons.ICON;
		}

		@Override
		public Icon getTableIcon(Dependency dependency) {

			for (DeclarationSource declarationSource : dependency.getDeclarationSources()) {
				if (declarationSource instanceof DeclarationSource.Plugin) {
					return AllIcons.Nodes.Plugin;
				}
			}

			return MavenIcons.MavenProject;
		}

	}

}
