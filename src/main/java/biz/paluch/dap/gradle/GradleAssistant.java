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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyFileDelegate;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.ProjectBuildContextWrapper;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.StringUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import icons.GradleIcons;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlElement;

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
		return GradleInterface.INSTANCE.getDisplayName();
	}

	@Override
	public boolean supports(Project project) {
		return GradleProjectContext.isGradleProject(project);
	}

	@Override
	public boolean supports(PsiFile file) {
		return GradleUtils.isGradleFile(file);
	}

	@Override
	public void prepare(Project project) {
		new GradleInitService().execute(project, null);
	}

	@Override
	public List<PsiFile> enumerate(Project project) {

		Collection<GradleProjectSettings> settings = GradleSettings.getInstance(project).getLinkedProjectsSettings();
		BetterPsiManager psiManager = BetterPsiManager.getInstance(project);
		ProjectDataManager pdm = ProjectDataManager.getInstance();
		LocalFileSystem lfs = LocalFileSystem.getInstance();
		List<PsiFile> anchors = new ArrayList<>();
		Set<String> seenDirectories = new HashSet<>();

		for (GradleProjectSettings setting : settings) {

			ExternalProjectInfo projectInfo = pdm
					.getExternalProjectData(project, GradleConstants.SYSTEM_ID, setting.getExternalProjectPath());

			if (projectInfo == null || projectInfo.getExternalProjectStructure() == null) {
				continue;
			}

			DataNode<ProjectData> structure = projectInfo.getExternalProjectStructure();
			for (DataNode<ModuleData> moduleNode : ExternalSystemApiUtil.findAll(structure, ProjectKeys.MODULE)) {
				ModuleData moduleData = moduleNode.getData();
				VirtualFile directory = resolveLinkedDirectory(moduleData.getLinkedExternalProjectPath(), lfs);

				if (!GradleUtils.isDirectory(directory) || seenDirectories.contains(directory.getPath())) {
					continue;
				}

				seenDirectories.add(directory.getPath());

				for (VirtualFile script : GradleUtils.findGradleScripts(directory)) {
					psiManager.doWithFile(script, anchors::add);
				}

				VirtualFile gradle = directory.findFileByRelativePath(GradleUtils.GRADLE_DIR);
				if (GradleUtils.isDirectory(gradle)) {
					VirtualFile[] children = gradle.getChildren();
					for (VirtualFile child : children) {
						if (GradleUtils.isVersionCatalog(child)) {
							psiManager.doWithFile(child, anchors::add);
						}
					}
				}

				addAnchor(psiManager, directory, GradleUtils.GRADLE_PROPERTIES, anchors);
			}
		}

		return anchors;
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector) {
		new GradleDependencyCollector(anchor.getProject()).collect(anchor, collector);
	}

	private static @Nullable VirtualFile resolveLinkedDirectory(String path, LocalFileSystem lfs) {
		if (StringUtils.isEmpty(path)) {
			return null;
		}
		VirtualFile directory = lfs.findFileByPath(path);
		return GradleUtils.isDirectory(directory) ? directory : null;
	}

	private static void addAnchor(BetterPsiManager psiManager, VirtualFile directory, String relativePath,
			List<PsiFile> anchors) {

		VirtualFile child = directory.findFileByRelativePath(relativePath);
		if (child == null || child.isDirectory()) {
			return;
		}

		psiManager.doWithFile(child, anchors::add);
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Gradle integration does not support " + anchor);
		}

		GradleProjectContext context = GradleProjectContext.of(project, anchor);
		return new GradleDependencyContext(project, anchor.getVirtualFile(), context);
	}

	static class GradleDependencyContext extends ProjectBuildContextWrapper implements ProjectDependencyContext {

		private final DependencyFileDelegate delegate;

		private final GradleProjectContext projectContext;

		GradleDependencyContext(Project project, VirtualFile file, GradleProjectContext projectContext) {
			super(projectContext);
			this.delegate = DependencyFileDelegate.of(project, file);
			this.projectContext = projectContext;
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return GradleInterface.INSTANCE;
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {
			return delegate.collectDependencies(it -> {
				DependencyCollector collector = new GradleDependencyCollector(delegate.getProject()).collect(it);
				collector.addAllReleaseSources(projectContext.getReleaseSources());
				return collector;
			});
		}

		@Override
		public boolean isVersionElement(PsiElement element) {

			PsiFile file = element.getContainingFile();

			if (!GradleUtils.isGradleFile(file)) {
				return false;
			}

			if (GradleUtils.isGradlePropertiesFile(file)) {
				return GradlePropertiesParser.isPropertyValueElement(element);
			}

			if (GradleUtils.KOTLIN_AVAILABLE && GradleUtils.isKotlinDsl(file)) {
				return KotlinArtifactReferenceLocator.isVersionElement(element);
			}

			return true;
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			PsiFile psiFile = element.getContainingFile();
			GradleProjectContext buildContext = GradleProjectContext.of(delegate.getProject(), file);
			LookupContext context = LookupContext.create(delegate, buildContext);
			return new VersionUpgradeLookup(context, new GradleArtifactReferenceResolver(context, psiFile));
		}

		@Override
		public void applyUpdate(PsiElement anchor, DependencyUpdate update) {
			new UpdateGradleFile(delegate.getProject()).applyUpdate(anchor, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdateGradleFile(delegate.getProject()).applyUpdates(psiFile, updates);
		}

		@Override
		public String toString() {
			return "GradleDependencyContext[%s] %s".formatted(delegate, projectContext);
		}

	}

	/**
	 * Gradle-specific user interface support.
	 */
	enum GradleInterface implements InterfaceAssistant {

		INSTANCE;

		@Override
		public String getDisplayName() {
			return MessageBundle.message("assistant.gradle");
		}

		@Override
		public String getDisplayName(VirtualFile file) {

			if (GradleUtils.isKotlinDsl(file)) {
				return MessageBundle.message("assistant.gradle.kts");
			}

			if (GradleUtils.isGroovyDsl(file)) {
				return MessageBundle.message("assistant.gradle.groovy");
			}

			if (GradleUtils.isVersionCatalog(file)) {
				return MessageBundle.message("assistant.gradle.toml");
			}

			return getDisplayName();
		}

		@Override
		public Icon getGutterIcon(ArtifactDeclaration declaration) {

			if (declaration.getVersionLiteral() instanceof TomlElement) {
				return DependencyAssistantIcons.UPGRADE_TOML_ICON;
			}

			return DependencyAssistantIcons.UPGRADE_GRADLE_ICON;
		}

		@Override
		public Icon getNavigateIcon(ArtifactDeclaration declaration) {

			PsiElement versionLiteral = declaration.getVersionLiteral();
			if ((declaration.getVersionSource() instanceof VersionSource.VersionCatalog
					|| declaration.getVersionSource() instanceof VersionSource.VersionCatalogProperty)
					&& versionLiteral instanceof TomlElement) {
				return DependencyAssistantIcons.TOML_NAVIGATE;
			}

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

			return GradleIcons.Gradle;
		}

		/**
		 * Strip the surrounding quote characters from quoted version-catalog literals
		 * ({@code .versions.toml}) so the highlight covers only the version text. All
		 * other Gradle build files highlight the full element range.
		 */
		@Override
		public TextRange getHighlightRange(PsiElement element) {

			TextRange range = element.getTextRange();

			if (GradleUtils.isVersionCatalog(element.getContainingFile()) && element.getText().startsWith("\"")) {
				return new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1);
			}
			return range;
		}

	}

}
