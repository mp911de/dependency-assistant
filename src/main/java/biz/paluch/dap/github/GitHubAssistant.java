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

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.GitRefIntrospectedDependencies;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.IntrospectedDependencies;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileIndexLookup;
import biz.paluch.dap.support.ProjectBuildContextWrapper;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.PsiElements;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * GitHub Actions implementation of {@link DependencyAssistant}.
 *
 * <p>Supports YAML files under {@code .github/workflows/} and GitHub Action
 * metadata files named {@code action.yml} or {@code action.yaml}. The assistant
 * is only active when both the YAML plugin and the GitHub plugin are available,
 * which is checked via class-availability guards so that the always-loaded
 * portion of this class never triggers loading of the optional plugin types
 * eagerly.
 *
 * <p>Each supported file forms its own lightweight project context keyed by the
 * file path.
 *
 * @author Mark Paluch
 */
public class GitHubAssistant implements DependencyAssistant {

	private static final PluginId YAML = PluginId.getId("org.jetbrains.plugins.yaml");

	private static final boolean AVAILABLE = isYamlAvailable();

	@Override
	public String getId() {
		return "github";
	}

	@Override
	public String getDisplayName() {
		return GitHubInterface.INSTANCE.getDisplayName();
	}

	@Override
	public PackageSystem getPackageSystem() {
		return PackageSystem.GITHUB;
	}

	@Override
	public boolean supports(Project project) {
		return AVAILABLE;
	}

	@Override
	public boolean supports(PsiFile file) {
		return AVAILABLE && (GitHubUtils.isWorkflowFile(file) || file.getUserData(GitHubProjectContext.KEY) != null);
	}

	@Override
	public List<PsiFile> enumerate(Project project) {

		if (!AVAILABLE) {
			return List.of();
		}

		List<PsiFile> actionFiles = new ArrayList<>();
		BetterPsiManager psiManager = BetterPsiManager.getInstance(project);
		Collection<VirtualFile> yamlFiles = FileIndexLookup.getInstance(project)
				.find(YAMLFileType.YML, GitHubUtils::isWorkflowFile);

		for (VirtualFile yaml : yamlFiles) {
			psiManager.optional(yaml).filter(GitHubUtils::isWorkflowFile).ifPresent(actionFiles::add);
		}

		return actionFiles;
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector) {
		new GitHubDependencyCollector(anchor.getProject()).doCollect(anchor, collector);
	}

	@Override
	public IntrospectedDependencies introspect(Project project) {
		return new GitRefIntrospectedDependencies(StateService.getInstance(project).getCache());
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("GitHub Actions integration does not support " + anchor);
		}

		GitHubProjectContext injected = anchor.getUserData(GitHubProjectContext.KEY);
		if (injected != null) {
			return new GitHubDependencyContext(project, anchor.getVirtualFile(), injected);
		}

		return CachedValuesManager.getProjectPsiDependentCache(anchor,
				it -> createContext(project, it.getVirtualFile()));
	}

	private static ProjectDependencyContext createContext(Project project, VirtualFile anchor) {
		return new GitHubDependencyContext(project, anchor, GitHubProjectContext.of(project, anchor));
	}

	private static boolean isYamlAvailable() {
		return PluginManagerCore.isPluginInstalled(YAML) && !PluginManagerCore.isDisabled(YAML)
				&& FileTypeManager.getInstance().findFileTypeByName("YAML") != null;
	}

	private static class GitHubDependencyContext extends ProjectBuildContextWrapper
			implements ProjectDependencyContext {

		private final Project project;

		private final VirtualFile anchor;

		private final GitHubProjectContext projectContext;

		private final BetterPsiManager psiManager;

		GitHubDependencyContext(Project project, VirtualFile anchor,
				GitHubProjectContext projectContext) {
			super(projectContext);
			this.project = project;
			this.anchor = anchor;
			this.projectContext = projectContext;
			this.psiManager = BetterPsiManager.getInstance(project);
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return GitHubInterface.INSTANCE;
		}

		@Override
		public PackageSystem getPackageSystem() {
			return projectContext.getPackageSystem();
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			return psiManager.optional(anchor).map(psiFile -> {
				GitHubDependencyCollector collector = new GitHubDependencyCollector(project);
				DependencyCollector result = collector.collect(psiFile);
				result.addAllReleaseSources(getReleaseSources());
				return result;
			}).orElseGet(DependencyCollector::new);
		}

		@Override
		public @Nullable Dependency resolveDependency(DeclaredDependency declaredDependency, List<Release> releases) {
			return GitVersionResolver.resolveDependency(declaredDependency, releases);
		}

		@Override
		public boolean isVersionElement(PsiElement element) {

			PsiFile file = element.getContainingFile();

			if (GitHubUtils.isWorkflowFile(file)
					|| file != null && file.getUserData(GitHubProjectContext.KEY) != null) {

				YAMLScalar usesScalar = GitHubUtils.findUsesScalar(PsiElements.unleaf(element));
				return usesScalar != null;
			}

			return false;
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			LookupContext context = LookupContext.create(project, this);
			return new VersionUpgradeLookup(context, new GitHubArtifactReferenceResolver(context, projectContext));
		}

		@Override
		public void applyUpdate(PsiElement anchor, DependencyUpdate update) {
			new UpdateGitHubWorkflowFile(project).applyUpdate((YAMLScalar) anchor, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdateGitHubWorkflowFile(project).applyUpdates(psiFile, updates);
		}

		@Override
		public String toString() {
			return "GitHubDependencyContext[%s] %s".formatted(anchor, getProjectId());
		}

	}

	/**
	 * GitHub Actions-specific user interface support.
	 */
	enum GitHubInterface implements InterfaceAssistant {

		INSTANCE;

		@Override
		public String getDisplayName() {
			return MessageBundle.message("assistant.github");
		}

		@Override
		public String getDisplayName(VirtualFile file) {
			return getDisplayName();
		}

		@Override
		public String getDisplayName(ArtifactId artifactId) {
			return GitHubUtils.toString(artifactId);
		}

		@Override
		public Icon getGutterIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.UPGRADE_GITHUB_ICON;
		}

		@Override
		public Icon getNavigateIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.ICON;
		}

		@Override
		public Icon getTableIcon(Dependency dependency) {
			return AllIcons.Vcs.Vendors.Github;
		}

		@Override
		public TextRange getHighlightRange(PsiElement element) {
			return GitHubUtils.getVersionRange(element);
		}

	}

}
