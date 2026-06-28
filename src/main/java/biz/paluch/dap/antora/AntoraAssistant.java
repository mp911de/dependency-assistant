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

package biz.paluch.dap.antora;

import java.util.Collection;
import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.GitRefIntrospectedDependencies;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.IntrospectedDependencies;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyFileDelegate;
import biz.paluch.dap.support.DependencyUpdate;
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
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Antora playbook implementation of {@link DependencyAssistant}.
 *
 * <p>Supports YAML files named {@code antora-playbook.yml} whose
 * {@code ui.bundle.url} declaration references a Git-hosted release asset. The
 * assistant is only active when the YAML plugin is available, which is checked
 * through class-availability guards. Always-loaded code keeps optional plugin
 * types out of eager class loading.
 *
 * <p>Each supported file forms its own lightweight project context keyed by the
 * file path.
 *
 * @author Mark Paluch
 */
public class AntoraAssistant implements DependencyAssistant {

	private static final PluginId YAML = PluginId.getId("org.jetbrains.plugins.yaml");

	private static final PluginId GITHUB = PluginId.getId("org.jetbrains.plugins.github");

	private static final boolean AVAILABLE = isYamlAvailable() && isGitHubAvailable();

	@Override
	public String getId() {
		return "antora";
	}

	@Override
	public String getDisplayName() {
		return AntoraInterface.INSTANCE.getDisplayName();
	}

	@Override
	public PackageSystem getPackageSystem() {
		return PackageSystem.OTHER;
	}

	@Override
	public boolean supports(Project project) {
		return AVAILABLE;
	}

	@Override
	public boolean supports(PsiFile file) {
		return AVAILABLE && AntoraUtils.isPlaybookFile(file);
	}

	@Override
	public List<PsiFile> enumerate(Project project) {

		if (!AVAILABLE) {
			return List.of();
		}

		BetterPsiManager psiManager = BetterPsiManager.getInstance(project);
		GlobalSearchScope scope = new DelegatingGlobalSearchScope(ProjectScope.getProjectScope(project)) {

			@Override
			public boolean contains(VirtualFile file) {
				return super.contains(file) && AntoraUtils.isPlaybookFile(file);
			}

		};

		Collection<VirtualFile> yamlFiles = FileTypeIndex.getFiles(YAMLFileType.YML, scope);
		return psiManager.stream(yamlFiles).filter(AntoraUtils::isPlaybookFile).toList();
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector) {
		new AntoraDependencyCollector().doCollect(anchor, collector);
	}

	@Override
	public IntrospectedDependencies introspect(Project project) {
		return new GitRefIntrospectedDependencies(StateService.getInstance(project).getCache());
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Antora playbook integration does not support " + anchor);
		}

		AntoraProjectContext injected = anchor.getUserData(AntoraProjectContext.KEY);
		if (injected != null) {
			return new AntoraDependencyContext(this, project, anchor.getVirtualFile(), injected);
		}

		return CachedValuesManager.getProjectPsiDependentCache(anchor,
				it -> createContext(this, project, it.getVirtualFile()));
	}

	private static ProjectDependencyContext createContext(AntoraAssistant assistant, Project project,
			VirtualFile anchor) {
		return new AntoraDependencyContext(assistant, project, anchor, AntoraProjectContext.of(project, anchor));
	}

	private static boolean isYamlAvailable() {
		return PluginManagerCore.isPluginInstalled(YAML) && !PluginManagerCore.isDisabled(YAML)
				&& FileTypeManager.getInstance().findFileTypeByName("YAML") != null;
	}

	private static boolean isGitHubAvailable() {
		return PluginManagerCore.isPluginInstalled(GITHUB) && !PluginManagerCore.isDisabled(GITHUB);
	}

	private static class AntoraDependencyContext extends ProjectBuildContextWrapper
			implements ProjectDependencyContext {

		private final DependencyFileDelegate delegate;

		private final AntoraProjectContext projectContext;

		AntoraDependencyContext(AntoraAssistant assistant, Project project, VirtualFile file,
				AntoraProjectContext projectContext) {
			super(projectContext);
			this.delegate = DependencyFileDelegate.of(project, file);
			this.projectContext = projectContext;
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return AntoraInterface.INSTANCE;
		}

		@Override
		public PackageSystem getPackageSystem() {
			return projectContext.getPackageSystem();
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			return delegate.collectDependencies(it -> {
				DependencyCollector collector = new AntoraDependencyCollector().collect(it);
				collector.addAllReleaseSources(getReleaseSources());
				return collector;
			});
		}

		@Override
		public @Nullable Dependency resolveDependency(DeclaredDependency declaredDependency, List<Release> releases) {
			return GitVersionResolver.resolveDependency(declaredDependency, releases);
		}

		@Override
		public boolean isVersionElement(PsiElement element) {

			if (!AntoraUtils.isPlaybookFile(element.getContainingFile())) {
				return false;
			}

			if (element instanceof YAMLQuotedText) {
				return false;
			}

			YAMLScalar scalar = AntoraArtifactReferenceResolver.findBundleUrlScalar(PsiElements.unleaf(element));
			return scalar != null;
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			LookupContext context = LookupContext.create(delegate, this);
			return new VersionUpgradeLookup(context, new AntoraArtifactReferenceResolver(context, projectContext));
		}

		@Override
		public void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {
			new UpdateAntoraPlaybookFile(delegate.getProject()).applyUpdate((YAMLScalar) versionLiteral, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdateAntoraPlaybookFile(delegate.getProject()).applyUpdates(psiFile, updates);
		}

		@Override
		public String toString() {
			return "AntoraDependencyContext[%s] %s".formatted(delegate, projectContext);
		}

	}

	/**
	 * Antora-specific user interface support.
	 */
	enum AntoraInterface implements InterfaceAssistant {

		INSTANCE;

		@Override
		public String getDisplayName() {
			return MessageBundle.message("assistant.antora");
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
			return AllIcons.Vcs.Vendors.Github;
		}

		@Override
		public String getDocumentationText(ArtifactVersion artifactVersion) {
			return artifactVersion instanceof GitVersion gitVersion ? gitVersion.toDocumentationString()
					: InterfaceAssistant.super.getDocumentationText(artifactVersion);
		}

		@Override
		public TextRange getHighlightRange(PsiElement element) {
			return AntoraUtils.getVersionRange(element);
		}

	}

}
