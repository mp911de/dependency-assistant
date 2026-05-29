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
import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.DependencyScanEntry;
import biz.paluch.dap.DependencySource;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.ProjectStateUpdater;
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
import biz.paluch.dap.util.StringUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValuesManager;
import icons.GradleIcons;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlElement;

import org.springframework.util.Assert;

/**
 * Gradle implementation of {@link DependencyAssistant}.
 *
 * @author Mark Paluch
 */
class GradleAssistant implements DependencyAssistant, DependencySource {

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
	public DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator) {
		return new ProjectStateUpdater(project).aggregate(this, indicator);
	}

	@Override
	public void initializeState(Project project, ProgressIndicator indicator) {
		new GradleInitService().execute(project, null);
		new ProjectStateUpdater(project).readAndUpdateAll(this, indicator);
	}

	@Override
	public List<DependencyScanEntry> enumerate(Project project) {

		if (!GradleProjectContext.isGradleProject(project)) {
			return List.of();
		}

		Collection<GradleProjectSettings> settings = GradleSettings.getInstance(project).getLinkedProjectsSettings();
		PsiManager psiManager = PsiManager.getInstance(project);
		LocalFileSystem lfs = LocalFileSystem.getInstance();

		List<VirtualFile> linkedDirectories = new ArrayList<>(settings.size());
		for (GradleProjectSettings setting : settings) {
			VirtualFile directory = resolveLinkedDirectory(setting, lfs);
			if (directory != null) {
				linkedDirectories.add(directory);
			}
		}

		List<DependencyScanEntry> entries = new ArrayList<>();

		for (VirtualFile directory : linkedDirectories) {
			for (String name : GradleUtils.GRADLE_SCRIPT_NAMES) {
				addEntry(project, psiManager, directory, name, entries);
			}
			addEntry(project, psiManager, directory, GradleUtils.LIBS_VERSIONS_TOML, entries);
		}

		for (VirtualFile directory : linkedDirectories) {
			addEntry(project, psiManager, directory, GradleUtils.GRADLE_PROPERTIES, entries);
		}

		return entries;
	}

	@Override
	public @Nullable DependencyScanEntry createEntry(Project project, PsiFile file) {

		if (!supports(file)) {
			return null;
		}

		return DependencyScanEntry.of(file, GradleProjectContext.of(project, file));
	}

	@Override
	public void collect(DependencyScanEntry entry, DependencyCollector collector) {
		PsiFile anchor = entry.anchor();
		new GradleDependencyCollector(anchor.getProject()).collect(anchor, collector);
	}

	private static @Nullable VirtualFile resolveLinkedDirectory(GradleProjectSettings setting, LocalFileSystem lfs) {

		String path = setting.getExternalProjectPath();
		if (StringUtils.isEmpty(path)) {
			return null;
		}

		VirtualFile directory = lfs.findFileByPath(path);
		if (directory == null || !directory.isDirectory()) {
			return null;
		}
		return directory;
	}

	private static void addEntry(Project project, PsiManager psiManager, VirtualFile directory, String relativePath,
			List<DependencyScanEntry> entries) {

		VirtualFile child = directory.findFileByRelativePath(relativePath);
		if (child == null || child.isDirectory()) {
			return;
		}

		PsiFile file = psiManager.findFile(child);
		if (file != null) {
			entries.add(DependencyScanEntry.of(file, GradleProjectContext.of(project, file)));
		}
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Gradle integration does not support " + anchor);
		}

		return CachedValuesManager.getProjectPsiDependentCache(anchor,
				it -> {
					GradleProjectContext context = GradleProjectContext.of(project, anchor);
					return new GradleDependencyContext(this, project, anchor.getVirtualFile(), context);
				});
	}

	static class GradleDependencyContext implements ProjectDependencyContext {

		private final GradleAssistant assistant;

		private final Project project;

		private final VirtualFile anchor;

		private final GradleProjectContext projectContext;

		private final StateService service;

		GradleDependencyContext(GradleAssistant assistant, Project project, VirtualFile anchor,
				GradleProjectContext projectContext) {

			this.assistant = assistant;
			this.project = project;
			this.anchor = anchor;
			this.projectContext = projectContext;
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
			return GradleInterface.INSTANCE;
		}

		@Override
		public void invalidateState(PsiFile file) {

			if (!GradleUtils.isGradleFile(file)) {
				return;
			}

			new ProjectStateUpdater(project).invalidateFile(assistant, file);
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			PsiFile psiFile = PsiManager.getInstance(project).findFile(anchor);
			if (psiFile == null) {
				return new DependencyCollector();
			}

			DependencyCollector collector = new GradleDependencyCollector(project).collect(psiFile);
			collector.addAllReleaseSources(projectContext.getReleaseSources());

			return collector;
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
				return KotlinVersionSiteLocator.isVersionElement(element);
			}

			return true;
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			PsiFile psiFile = element.getContainingFile();
			GradleProjectContext buildContext = GradleProjectContext.of(project, file);
			LookupContext context = LookupContext.create(project, buildContext);
			return new VersionUpgradeLookup(context, new GradleArtifactReferenceResolver(context, psiFile));
		}

		@Override
		public void applyUpdate(PsiElement anchor, DependencyUpdate update) {
			new UpdateGradleFile(project).applyUpdate(anchor, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdateGradleFile(project).applyUpdates(psiFile, updates);
		}

		@Override
		public String toString() {
			return "GradleDependencyContext[%s] %s".formatted(anchor, projectContext);
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
