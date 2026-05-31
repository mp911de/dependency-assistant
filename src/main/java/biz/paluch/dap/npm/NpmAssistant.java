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

package biz.paluch.dap.npm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.LookupContext;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.VersionUpgradeLookup;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.json.JsonFileType;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValuesManager;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * NPM implementation of {@link DependencyAssistant}.
 *
 * <p>Supports {@code package.json} files whose JSON root carries a
 * {@code dependencies} or {@code devDependencies} object. The assistant relies
 * on the bundled IntelliJ JSON support, declared via
 * {@code com.intellij.modules.json} in the plugin XML.
 *
 * @author Mark Paluch
 */
public class NpmAssistant implements DependencyAssistant {

	private static final PluginId JSON = PluginId.getId("com.intellij.modules.json");

	private static final boolean AVAILABLE = isJsonAvailable();

	@Override
	public String getId() {
		return "npm";
	}

	@Override
	public String getDisplayName() {
		return NpmInterface.INSTANCE.getDisplayName();
	}

	@Override
	public boolean supports(Project project) {
		return AVAILABLE;
	}

	@Override
	public boolean supports(PsiFile file) {
		return AVAILABLE && NpmUtils.isPackageJson(file);
	}

	@Override
	public DependencyCollector getAllDependencies(ProjectStateIndexer indexer) {

		DependencyCollector aggregate = indexer.aggregate(this);
		aggregate.addAllReleaseSources(NpmProjectContext.getReleaseSources(indexer.getProject()));
		return aggregate;
	}

	@Override
	public List<PsiFile> enumerate(Project project) {

		if (!AVAILABLE) {
			return List.of();
		}

		List<PsiFile> anchors = new ArrayList<>();
		GlobalSearchScope scope = new DelegatingGlobalSearchScope(ProjectScope.getProjectScope(project)) {

			@Override
			public boolean contains(VirtualFile file) {
				return !file.getPath().contains("node_modules") && super.contains(file) && NpmUtils.isPackageJson(file);
			}

		};
		PsiManager psiManager = PsiManager.getInstance(project);
		Collection<VirtualFile> jsonFiles = FileTypeIndex.getFiles(JsonFileType.INSTANCE,
				scope);

		for (VirtualFile file : jsonFiles) {

			if (!NpmUtils.isPackageJson(file) || file.getPath().contains("node_modules")) {
				continue;
			}

			PsiFile psiFile = psiManager.findFile(file);
			if (psiFile != null) {
				anchors.add(psiFile);
			}
		}

		return anchors;
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector) {

		StateService service = StateService.getInstance(anchor.getProject());
		new NpmDependencyCollector(service.getCache()).doCollect(anchor, collector);
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("NPM integration does not support " + anchor);
		}

		NpmProjectContext injected = anchor.getUserData(NpmProjectContext.KEY);
		if (injected != null) {
			return new NpmDependencyContext(this, project, anchor.getVirtualFile(), injected);
		}

		return CachedValuesManager.getProjectPsiDependentCache(anchor,
				it -> buildCachedContext(this, project, it));
	}

	private static ProjectDependencyContext buildCachedContext(NpmAssistant assistant, Project project,
			PsiFile anchor) {

		NpmProjectContext context = NpmProjectContext.of(project, anchor.getVirtualFile());
		return new NpmDependencyContext(assistant, project, anchor.getVirtualFile(), context);
	}

	private static boolean isJsonAvailable() {
		return PluginManagerCore.isPluginInstalled(JSON) && !PluginManagerCore.isDisabled(JSON)
				&& FileTypeManager.getInstance().findFileTypeByName("JSON") != null;
	}

	private static class NpmDependencyContext implements ProjectDependencyContext {

		private final NpmAssistant assistant;

		private final Project project;

		private final VirtualFile anchor;

		private final NpmProjectContext projectContext;

		private final StateService service;

		NpmDependencyContext(NpmAssistant assistant, Project project, VirtualFile anchor,
				NpmProjectContext projectContext) {
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
			return NpmInterface.INSTANCE;
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			PsiFile psiFile = PsiManager.getInstance(project).findFile(anchor);
			if (psiFile == null) {
				return new DependencyCollector();
			}

			DependencyCollector collector = new NpmDependencyCollector(service.getCache()).collect(psiFile);
			collector.addAllReleaseSources(getReleaseSources());
			return collector;
		}

		@Override
		public @Nullable Dependency resolveDependency(DeclaredDependency declaredDependency, List<Release> releases) {
			return GitVersionResolver.resolveDependency(declaredDependency, releases);
		}

		@Override
		public boolean isVersionElement(PsiElement element) {

			if (!NpmUtils.isPackageJson(element.getContainingFile())) {
				return false;
			}
			// Only the JsonStringLiteral itself qualifies; firing for its child tokens
			// would register a duplicate line marker on the same dependency value.
			return element instanceof com.intellij.json.psi.JsonStringLiteral
					&& NpmPsiUtils.findDependencyLiteral(element) != null;
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			LookupContext context = LookupContext.create(project, projectContext);
			return new VersionUpgradeLookup(context, new NpmArtifactReferenceResolver(context, projectContext));
		}

		@Override
		public void applyUpdate(PsiElement anchor, DependencyUpdate update) {
			new UpdatePackageJsonFile(project).applyUpdate(anchor, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdatePackageJsonFile(project).applyUpdates(psiFile, updates);
		}

		@Override
		public String toString() {
			return "NpmDependencyContext[%s] %s".formatted(anchor, projectContext);
		}

	}

	enum NpmInterface implements InterfaceAssistant {

		INSTANCE;

		@Override
		public String getDisplayName() {
			return MessageBundle.message("assistant.npm");
		}

		@Override
		public String getDisplayName(VirtualFile file) {
			return getDisplayName();
		}

		@Override
		public Icon getGutterIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.UPGRADE_NPM_ICON;
		}

		@Override
		public Icon getNavigateIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.ICON;
		}

		@Override
		public Icon getTableIcon(Dependency dependency) {
			return DependencyAssistantIcons.NPM;
		}

		@Override
		public String getDocumentationText(ArtifactVersion artifactVersion) {
			return artifactVersion instanceof GitVersion gitVersion ? gitVersion.toDocumentationString()
					: InterfaceAssistant.super.getDocumentationText(artifactVersion);
		}

		@Override
		public TextRange getHighlightRange(PsiElement element) {
			return NpmPsiUtils.getVersionRange(element);
		}

	}

}
