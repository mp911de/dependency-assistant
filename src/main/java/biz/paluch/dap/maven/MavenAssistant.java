/*
 * Copyright 2026-present the original author or authors.
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
import biz.paluch.dap.VersionPropertyIntrospectedDependencies;
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionedPackage;
import biz.paluch.dap.lookup.ArtifactReferenceResolver;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyFileDelegate;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.ProjectBuildContextWrapper;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.PsiFileCache;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import icons.MavenIcons;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * {@link DependencyAssistant} for imported Maven POMs.
 *
 * <p>The integration enumerates POMs from the Maven project model, collects
 * dependency and plugin declarations, and creates per-POM contexts for lookup
 * and updates.
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
	public InterfaceAssistant getInterfaceAssistant() {
		return MavenInterface.INSTANCE;
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
	public boolean isVersionElement(PsiElement element) {
		return XmlUtil.isVersionElement(element);
	}

	@Override
	public @Nullable BillOfMaterials resolveBillOfMaterials(Project project, VersionedPackage bom) {
		return BomUtil.resolveBillOfMaterials(StateService.getInstance(project).getCache(), project, bom);
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
	public IntrospectedDependencies introspect(Project project) {
		return new VersionPropertyIntrospectedDependencies(StateService.getInstance(project).getCollectors());
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector) {
		collect(anchor, collector, introspect(anchor.getProject()));
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector,
			IntrospectedDependencies introspected) {

		Project project = anchor.getProject();
		MavenProjectContext context = MavenProjectContext.of(project, anchor);
		MavenPomProperties propertyResolver = context.getPomProperties();

		new MavenDependencyCollector(project)
				.doCollect(anchor, propertyResolver, collector);
		collector.addPropertyValues(localPropertyValues(anchor, propertyResolver));

		if (introspected instanceof VersionPropertyIntrospectedDependencies properties) {
			properties.register(context.getProjectId(), collector);
		}
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Maven integration does not support " + anchor);
		}

		MavenProjectContext context = MavenProjectContext.of(project, anchor);
		return new MavenDependencyContext(this, project, anchor.getVirtualFile(), context);
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

		private final DependencyFileDelegate delegate;

		MavenDependencyContext(MavenAssistant assistant, Project project, VirtualFile anchor,
				MavenProjectContext projectContext) {
			super(projectContext);
			this.assistant = assistant;
			this.projectContext = projectContext;
			this.delegate = DependencyFileDelegate.of(project, anchor);
		}

		@Override
		public DependencyAssistant getAssistant() {
			return assistant;
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {
			return delegate.collectDependencies(getPackageSystem(), assistant::collectCompleted);
		}

		@Override
		public boolean isVersionElement(PsiElement element) {
			return XmlUtil.isVersionElement(element);
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			return PsiFileCache.withProjectRoot(element.getContainingFile(), MavenDependencyContext::createLookup);
		}

		private static VersionUpgradeLookup createLookup(PsiFile pom) {

			Project project = pom.getProject();
			MavenProjectContext projectContext = MavenProjectContext.of(project, pom);

			if (pom instanceof XmlFile xmlFile) {
				return VersionUpgradeLookup.of(project, projectContext.getProjectId(),
						new MavenArtifactReferenceResolver(project, xmlFile, projectContext));
			}

			return VersionUpgradeLookup.of(project, projectContext.getProjectId(),
					ArtifactReferenceResolver.unresolved());
		}

		@Override
		public void applyUpdate(PsiElement anchor, DependencyUpdate update) {
			new UpdatePomFile(projectContext.getPomProperties()).applyUpdate(anchor, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, DependencyUpdates updates) {
			new UpdatePomFile(projectContext.getPomProperties()).applyUpdates(psiFile, updates);
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
