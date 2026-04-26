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
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import javax.swing.*;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import icons.MavenIcons;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Maven implementation of {@link DependencyAssistant}.
 *
 * @author Mark Paluch
 */
class MavenAssistant implements DependencyAssistant {

	private final ConcurrentMap<VirtualFile, ProjectDependencyContext> contexts = ContainerUtil
			.createConcurrentWeakValueMap();

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
		return MavenProjectsManager.getInstance(project).isMavenizedProject();
	}

	@Override
	public boolean supports(VirtualFile file) {
		return MavenUtils.isMavenPomFile(file);
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

		MavenProjectContext injected = anchor.getUserData(MavenProjectContext.KEY);
		if (injected != null) {
			return new MavenDependencyContext(project, anchor.getVirtualFile(),
					MavenProjectsManager.getInstance(project), injected);
		}

		return CachedValuesManager.getProjectPsiDependentCache(anchor,
				it -> createContext(project, anchor.getVirtualFile()));
	}

	private ProjectDependencyContext createContext(Project project, VirtualFile anchor) {

		MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
		MavenProjectContext projectContext = MavenProjectContext.of(project, anchor);

		if (!projectContext.isAvailable()) {
			throw new IllegalStateException("No Maven project found for " + anchor);
		}

		return new MavenDependencyContext(project, anchor, manager, projectContext);
	}

	private static class MavenDependencyContext implements ProjectDependencyContext {

		private final MavenProjectContext projectContext;

		private final Project project;

		private final @Nullable VirtualFile anchor;

		private final MavenProjectsManager manager;

		private final DependencyAssistantService service;

		MavenDependencyContext(Project project, @Nullable VirtualFile anchor,
				MavenProjectsManager manager, MavenProjectContext projectContext) {

			this.project = project;
			this.projectContext = projectContext;
			this.anchor = anchor;
			this.manager = manager;
			this.service = DependencyAssistantService.getInstance(project);
		}

		@Override
		public boolean isAvailable() {
			return projectContext.isAvailable();
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return MavenInterface.INSTANCE;
		}

		@Override
		public ProjectId getProjectId() {
			return projectContext.getProjectId();
		}

		@Override
		public void invalidateState(PsiFile file) {

			if (!MavenUtils.isMavenPomFile(file)) {
				return;
			}

			ProjectState projectState = service.getProjectState(getProjectId());
			projectState.invalidateDependencies();
			projectState.setDependencies(collect(file, projectContext.getMavenProject()));
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return List.of();
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			if (anchor == null) {
				return new UpdateProjectState(project, service).getAllDependencies(indicator);
			}

			return ApplicationManager.getApplication().runReadAction((Computable<DependencyCollector>) () -> {

				PsiFile psiFile = PsiManager.getInstance(project).findFile(anchor);
				if (psiFile == null) {
					return new DependencyCollector();
				}

				return collect(psiFile, projectContext.getMavenProject());
			});
		}

		@Override
		public boolean isVersionElement(PsiElement element) {

			XmlTag currentTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
			if (currentTag == null) {
				return false;
			}

			XmlTag parentTag = currentTag.getParentTag();
			if (parentTag == null) {
				return false;
			}

			if (currentTag.getLocalName().equals("properties") || parentTag.getLocalName().equals("properties")
					|| currentTag.getLocalName().equals("dependency") || currentTag.getLocalName().equals("plugin")
					|| parentTag.getLocalName().equals("dependency") || parentTag.getLocalName().equals("plugin")) {
				return true;
			}

			return "version".equals(currentTag.getLocalName())
					&& ("dependency".equals(parentTag.getLocalName()) || "plugin".equals(parentTag.getLocalName()));
		}

		@Override
		public VersionUpgradeLookupSupport getLookup(PsiElement element) {
			return VersionUpgradeLookupService.create(element);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			Assert.state(anchor != null, "Cannot apply Maven updates without a build file");
			new UpdatePomFile().applyUpdates(psiFile, updates);
		}

		private DependencyCollector collect(PsiFile file, MavenProject currentProject) {

			Map<String, String> allProperties = new MavenProperties(project, manager).getAllProperties(currentProject);
			return new MavenDependencyCollector(service.getCache(), allProperties).collect(file);
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
