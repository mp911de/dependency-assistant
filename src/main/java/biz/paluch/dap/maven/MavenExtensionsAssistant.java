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

import java.util.Collection;
import java.util.List;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.VersionCaretRemap;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.DependencyFileDelegate;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.CachedValuesManager;

import org.springframework.util.Assert;

/**
 * Maven Extension implementation of {@link DependencyAssistant}.
 *
 * @author Mark Paluch
 */
class MavenExtensionsAssistant implements DependencyAssistant {

	@Override
	public String getId() {
		return "maven";
	}

	@Override
	public String getDisplayName() {
		return MavenAssistant.MavenInterface.INSTANCE.getDisplayName();
	}

	@Override
	public boolean supports(Project project) {
		return true;
	}

	@Override
	public boolean supports(PsiFile file) {
		return MavenUtils.isMavenExtensionsFile(file);
	}

	@Override
	public List<PsiFile> enumerate(Project project) {

		BetterPsiManager psiManager = BetterPsiManager.getInstance(project);
		GlobalSearchScope scope = new DelegatingGlobalSearchScope(ProjectScope.getProjectScope(project)) {

			@Override
			public boolean contains(VirtualFile file) {
				return super.contains(file) && MavenUtils.isMavenExtensionsFile(file);
			}

		};

		Collection<VirtualFile> xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope);
		return psiManager.stream(xmlFiles).filter(MavenUtils::isMavenExtensionsFile)
				.toList();
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector) {

		MavenDependencyCollector dependencyCollector = new MavenDependencyCollector(
				StateService.getInstance(anchor.getProject()).getCache());
		dependencyCollector.doCollect(anchor, PropertyResolver.empty(), collector);
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Maven integration does not support " + anchor);
		}

		if (anchor.getVirtualFile() == null) {
			return ProjectDependencyContext.absent();
		}

		return new MavenExtensionContext(project, anchor.getVirtualFile());
	}

	/**
	 * {@link ProjectDependencyContext} for Maven extension files.
	 * @author Mark Paluch
	 */
	static class MavenExtensionContext implements ProjectDependencyContext {

		private final DependencyFileDelegate delegate;

		MavenExtensionContext(Project project, VirtualFile file) {
			this.delegate = DependencyFileDelegate.of(project, file);
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public ProjectId getProjectId() {
			return ProjectId.of(delegate.getFile());
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return MavenRepositories.getReleaseSources(delegate.getProject());
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return MavenAssistant.MavenInterface.INSTANCE;
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {
			return delegate.collectDependencies(this::collect);
		}

		@Override
		public boolean isVersionElement(PsiElement element) {
			return MavenUtils.isVersionElement(element);
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			return CachedValuesManager.getProjectPsiDependentCache(element.getContainingFile(),
					MavenExtensionContext::createLookup);
		}

		@Override
		public VersionCaretRemap applyUpdate(PsiElement anchor, DependencyUpdate update) {
			return new UpdateExtensionsFile().applyUpdate(anchor, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			new UpdateExtensionsFile().applyUpdates(psiFile, updates);
		}

		private DependencyCollector collect(PsiFile file) {

			MavenDependencyCollector dependencyCollector = new MavenDependencyCollector(delegate.getCache());
			DependencyCollector collector = dependencyCollector.collect(file, PropertyResolver.empty());
			collector.addAllReleaseSources(getReleaseSources());
			return collector;
		}

		@Override
		public String toString() {
			return "MavenExtensionsDependencyContext[%s]".formatted(delegate);
		}

		private static VersionUpgradeLookup createLookup(PsiFile extensions) {

			Project project = extensions.getProject();
			MavenExtensionContext buildContext = new MavenExtensionContext(project, extensions.getVirtualFile());
			LookupContext context = LookupContext.create(project, buildContext);
			return new VersionUpgradeLookup(context, new MavenExtensionsReferenceResolver(extensions));
		}

	}

}
