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

import java.util.List;
import javax.swing.*;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.*;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * GitHub Actions implementation of {@link DependencyAssistant}.
 *
 * <p>Supports workflow files under {@code .github/workflows/} with {@code .yml}
 * or {@code .yaml} extensions. The assistant is only active when both the YAML
 * plugin and the GitHub plugin are available, which is checked via
 * class-availability guards so that the always-loaded portion of this class
 * never triggers loading of the optional plugin types eagerly.
 *
 * <p>Each workflow file forms its own lightweight project context keyed by the
 * file path.
 *
 * @author Mark Paluch
 */
public class GitHubAssistant implements DependencyAssistant {

	private static final boolean AVAILABLE = isYamlAvailable() && isGitHubPluginAvailable();

	@Override
	public String getId() {
		return "github";
	}

	@Override
	public String getDisplayName() {
		return GitHubInterface.INSTANCE.getDisplayName();
	}

	@Override
	public boolean supports(Project project) {
		return AVAILABLE;
	}

	@Override
	public boolean supports(PsiFile file) {
		return AVAILABLE && GitHubUtils.isWorkflowFile(file);
	}

	@Override
	public void initializeState(Project project, ProgressIndicator indicator) {
		new UpdateProjectState(project).readAndUpdateAll(indicator);
	}

	@Override
	public DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator) {
		return new UpdateProjectState(project).getAllDependencies(indicator);
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

	private ProjectDependencyContext createContext(Project project, VirtualFile anchor) {
		return new GitHubDependencyContext(project, anchor, GitHubProjectContext.of(project, anchor));
	}

	private static boolean isYamlAvailable() {
		return FileTypeManager.getInstance().findFileTypeByName("YAML") != null
				|| isClassAvailable("org.jetbrains.yaml.YAMLFileType");
	}

	private static boolean isGitHubPluginAvailable() {
		return isClassAvailable("org.jetbrains.plugins.github.authentication.accounts.GHAccountManager");
	}

	private static boolean isClassAvailable(String fqn) {
		try {
			Class.forName(fqn);
			return true;
		} catch (ClassNotFoundException ex) {
			return false;
		}
	}

	private static class GitHubDependencyContext implements ProjectDependencyContext {

		private final Project project;

		private final VirtualFile anchor;

		private final GitHubProjectContext context;

		private final DependencyAssistantService service;

		GitHubDependencyContext(Project project, VirtualFile anchor, GitHubProjectContext context) {
			this.project = project;
			this.anchor = anchor;
			this.context = context;
			this.service = DependencyAssistantService.getInstance(project);
		}

		@Override
		public boolean isAvailable() {
			return context.isAvailable();
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return GitHubInterface.INSTANCE;
		}

		@Override
		public ProjectId getProjectId() {
			return context.getProjectId();
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return context.getReleaseSources();
		}

		@Override
		public void invalidateState(PsiFile file) {

			if (!GitHubUtils.isWorkflowFile(file)) {
				return;
			}

			DependencyCollector collector = new GitHubDependencyCollector()
					.collect(file);
			ProjectState projectState = service.getProjectState(getProjectId());
			Cache cache = service.getCache();

			for (DeclaredDependency declaration : collector.getDeclarations()) {
				Dependency dependency = resolveDependency(declaration,
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

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			PsiFile psiFile = PsiManager.getInstance(project).findFile(anchor);
			if (psiFile == null) {
				return new DependencyCollector();
			}

			GitHubDependencyCollector collector = new GitHubDependencyCollector();
			DependencyCollector result = collector.collect(psiFile);
			result.addAllReleaseSources(getReleaseSources());

			return result;
		}

		@Override
		public @Nullable Dependency resolveDependency(DeclaredDependency declaredDependency, List<Release> releases) {

			if (declaredDependency.getVersionSources().isEmpty()) {
				return null;
			}

			VersionSource source = declaredDependency.getVersionSources().iterator().next();
			if (StringUtils.hasText(source.toString())) {
				GitVersion gitVersion = GitVersionResolver.resolveVersion(source.toString(), releases);
				return gitVersion != null ? Dependency.from(declaredDependency, gitVersion) : null;
			}
			return null;
		}

		@Override
		public boolean isVersionElement(PsiElement element) {

			if (GitHubUtils.isWorkflowFile(element.getContainingFile())) {

				if (element instanceof YAMLQuotedText) {
					return false;
				}

				YAMLScalar usesScalar = VersionUpgradeLookupService
						.getUsesScalar(element instanceof LeafPsiElement leaf ? leaf.getParent() : element);
				return usesScalar != null;
			}

			return false;
		}

		@Override
		public VersionUpgradeLookupSupport getLookup(PsiElement element) {
			return new VersionUpgradeLookupService(project, context);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdateGitHubWorkflowFile(project).applyUpdates(psiFile, updates);
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
		public Icon getGutterIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.UPGRADE_GITHUB_ICON;
		}

		@Override
		public Icon getNavigateIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.ICON;
		}

		@Override
		public Icon getTableIcon(Dependency dependency) {
			return DependencyAssistantIcons.ICON;
		}

		@Override
		public String getDocumentationText(ArtifactVersion artifactVersion) {
			return artifactVersion instanceof GitVersion gitVersion ? gitVersion.toDocumentationString()
					: InterfaceAssistant.super.getDocumentationText(artifactVersion);
		}

	}

}
