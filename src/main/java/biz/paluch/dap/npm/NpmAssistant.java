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

import java.util.List;
import javax.swing.*;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.*;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.StringUtils;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValuesManager;
import org.jspecify.annotations.Nullable;

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
	public void initializeState(Project project, ProgressIndicator indicator) {
		new NpmIndexingTask(project).readAndUpdateAll(indicator);
	}

	@Override
	public DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator) {
		return new NpmIndexingTask(project).getAllDependencies(indicator);
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("NPM integration does not support " + anchor);
		}

		NpmProjectContext injected = anchor.getUserData(NpmProjectContext.KEY);
		if (injected != null) {
			return new NpmDependencyContext(project, anchor.getVirtualFile(), injected);
		}

		return CachedValuesManager.getProjectPsiDependentCache(anchor,
				it -> buildCachedContext(project, it));
	}

	private static ProjectDependencyContext buildCachedContext(Project project, PsiFile anchor) {

		NpmProjectContext context = NpmProjectContext.of(project, anchor.getVirtualFile());
		return new NpmDependencyContext(project, anchor.getVirtualFile(), context);
	}

	private static boolean isJsonAvailable() {
		return PluginManagerCore.isPluginInstalled(JSON) && !PluginManagerCore.isDisabled(JSON)
				&& FileTypeManager.getInstance().findFileTypeByName("JSON") != null;
	}

	private static class NpmDependencyContext implements ProjectDependencyContext {

		private final Project project;

		private final VirtualFile anchor;

		private final NpmProjectContext context;

		private final DependencyAssistantService service;

		NpmDependencyContext(Project project, VirtualFile anchor, NpmProjectContext context) {
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
			return NpmInterface.INSTANCE;
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

			if (!NpmUtils.isPackageJson(file)) {
				return;
			}

			DependencyCollector collector = new NpmDependencyCollector().collect(file);
			ProjectState projectState = service.getProjectState(getProjectId());
			projectState.invalidateDependencies();
			projectState.setDependencies(collector);
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			PsiFile psiFile = PsiManager.getInstance(project).findFile(anchor);
			if (psiFile == null) {
				return new DependencyCollector();
			}

			DependencyCollector collector = new NpmDependencyCollector().collect(psiFile);
			collector.addAllReleaseSources(getReleaseSources());
			return collector;
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

			if (!NpmUtils.isPackageJson(element.getContainingFile())) {
				return false;
			}
			// Only the JsonStringLiteral itself qualifies; firing for its child tokens
			// would register a duplicate line marker on the same dependency value.
			return element instanceof com.intellij.json.psi.JsonStringLiteral
					&& NpmPsiUtils.findDependencyLiteral(element) != null;
		}

		@Override
		public VersionUpgradeLookupSupport getLookup(PsiElement element) {
			return new VersionUpgradeLookupService(project, context);
		}

		@Override
		public void applyUpdate(PsiElement anchor, DependencyUpdate update) {
			new UpdatePackageJsonFile(project).applyUpdate(anchor, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdatePackageJsonFile(project).applyUpdates(psiFile, updates);
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
