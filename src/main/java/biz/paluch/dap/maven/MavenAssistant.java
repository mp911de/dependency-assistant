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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.IntrospectedDependencies;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyFileDelegate;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.ProjectBuildContextWrapper;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import icons.MavenIcons;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

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
	public PackageSystem getPackageSystem() {
		return PackageSystem.MAVEN;
	}

	@Override
	public boolean supports(Project project) {
		return !MavenProjectsManager.getInstance(project).getProjects().isEmpty();
	}

	@Override
	public boolean supports(PsiFile file) {
		return MavenUtils.isMavenPomFile(file);
	}

	@Override
	public List<PsiFile> enumerate(Project project) {

		MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
		if (!manager.isMavenizedProject()) {
			return List.of();
		}

		BetterPsiManager psiManager = BetterPsiManager.getInstance(project);
		List<PsiFile> anchors = new ArrayList<>();

		for (MavenProject mavenProject : manager.getProjects()) {
			VirtualFile file = mavenProject.getFile();
			psiManager.doWithFile(file, anchors::add);
		}

		return anchors;
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector) {

		Project project = anchor.getProject();
		MavenProjectContext context = MavenProjectContext.of(project, anchor);
		PropertyResolver propertyResolver = MavenPropertyResolver.create(context, anchor);

		new MavenDependencyCollector(project)
				.doCollect(anchor, propertyResolver, collector);
		collector.addPropertyValues(localPropertyValues(anchor, propertyResolver));
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector,
			IntrospectedDependencies introspected) {

		collect(anchor, collector);

		if (introspected instanceof MavenIntrospectedDependencies maven) {
			maven.register(collector);
		}
	}

	@Override
	public IntrospectedDependencies introspect(Project project) {
		return new MavenIntrospectedDependencies();
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Maven integration does not support " + anchor);
		}

		MavenProjectContext context = anchor.getUserData(MavenProjectContext.KEY);
		if (context == null) {
			context = MavenProjectContext.of(project, anchor.getVirtualFile());
		}

		return new MavenDependencyContext(this, project, anchor, anchor.getVirtualFile(), context);
	}

	private static Map<String, String> localPropertyValues(PsiFile anchor, PropertyResolver propertyResolver) {

		if (!(anchor instanceof XmlFile xmlFile) || !MavenUtils.isMavenPomFile(anchor)) {
			return Map.of();
		}

		Map<String, String> values = new LinkedHashMap<>();
		for (String propertyName : MavenParser.getProperties(xmlFile).keySet()) {

			String value = propertyResolver.getProperty(propertyName);
			if (value != null) {
				values.put(propertyName, value);
			}
		}
		return values;
	}

	static class MavenDependencyContext extends ProjectBuildContextWrapper implements ProjectDependencyContext {

		private final MavenAssistant assistant;

		private final MavenProjectContext projectContext;

		private final MavenPropertyResolver propertyResolver;

		private final DependencyFileDelegate delegate;

		MavenDependencyContext(MavenAssistant assistant, Project project, PsiFile pomFile, VirtualFile anchor,
				MavenProjectContext projectContext) {
			super(projectContext);
			this.assistant = assistant;
			this.propertyResolver = MavenPropertyResolver.create(projectContext, pomFile);
			this.projectContext = projectContext;
			this.delegate = DependencyFileDelegate.of(project, anchor);
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return MavenInterface.INSTANCE;
		}

		@Override
		public PackageSystem getPackageSystem() {
			return PackageSystem.MAVEN;
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {
			return delegate.collectDependencies(file -> assistant.collectCompleted(file, getReleaseSources()));
		}

		@Override
		public boolean isVersionElement(PsiElement element) {
			return MavenUtils.isVersionElement(element);
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			return CachedValuesManager.getProjectPsiDependentCache(element.getContainingFile(),
					this::createLookup);
		}

		private VersionUpgradeLookup createLookup(PsiFile pom) {

			Project project = pom.getProject();
			LookupContext context = LookupContext.create(project, this);
			return new VersionUpgradeLookup(context, new MavenArtifactReferenceResolver(context, pom, projectContext));
		}

		@Override
		public void applyUpdate(PsiElement anchor, DependencyUpdate update) {
			new UpdatePomFile(propertyResolver).applyUpdate(anchor, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdatePomFile(propertyResolver).applyUpdates(psiFile, updates);
		}

		@Override
		public String toString() {
			return "MavenDependencyContext[%s] %s".formatted(delegate, projectContext);
		}

	}

	/**
	 * Maven-specific {@link InterfaceAssistant} supplying the display name and
	 * gutter, navigation, and table icons for Maven dependency declarations.
	 */
	enum MavenInterface implements InterfaceAssistant {

		INSTANCE;

		@Override
		public String getDisplayName() {
			return MessageBundle.message("assistant.maven");
		}

		@Override
		public Icon getGutterIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.UPGRADE_MAVEN_ICON;
		}

		@Override
		public Icon getNavigateIcon(ArtifactDeclaration declaration) {

			if (declaration.getVersionSource().isProperty()) {
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
