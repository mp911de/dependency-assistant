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

package biz.paluch.dap.maven.wrapper;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.MavenRepository;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.lookup.LookupContext;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.AbstractProjectBuildContext;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyFileDelegate;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileIndexLookup;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MatchFunction;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.PropertyUtils;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import icons.MavenIcons;

import org.springframework.util.Assert;

/**
 * Maven Wrapper implementation of {@link DependencyAssistant}.
 *
 * @author Mark Paluch
 */
public class MavenWrapperAssistant implements DependencyAssistant {

	@Override
	public String getId() {
		return "maven-wrapper";
	}

	@Override
	public PackageSystem getPackageSystem() {
		return PackageSystem.MAVEN;
	}

	@Override
	public String getDisplayName() {
		return MavenWrapperInterface.INSTANCE.getDisplayName();
	}

	@Override
	public boolean supports(Project project) {
		return true;
	}

	@Override
	public boolean supports(PsiFile file) {
		return MavenWrapperUtils.isWrapperFile(file);
	}

	@Override
	public List<PsiFile> enumerate(Project project) {

		BetterPsiManager psiManager = BetterPsiManager.getInstance(project);
		Collection<VirtualFile> files = FileIndexLookup.getInstance(project).find(MavenWrapperUtils.WRAPPER_FILENAME,
				MavenWrapperUtils::isWrapperFile);
		return psiManager.stream(files).filter(this::supports).toList();
	}

	@Override
	public void collect(PsiFile anchor, DependencyCollector collector) {

		if (anchor instanceof PropertiesFile propertiesFile) {
			new MavenWrapperParser(collector).collect(propertiesFile);
		}
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Maven integration does not support " + anchor);
		}

		return CachedValuesManager.getProjectPsiDependentCache(anchor, MavenWrapperAssistant::createWrapperContext);
	}

	private static ProjectDependencyContext createWrapperContext(PsiFile anchor) {

		VirtualFile virtualFile = anchor.getVirtualFile();
		if (virtualFile == null) {
			return ProjectDependencyContext.absent();
		}

		Project project = anchor.getProject();
		ProjectId projectId = MavenWrapperUtils.createProjectId(virtualFile);
		List<ReleaseSource> releaseSources = collectReleaseSources(anchor);

		return new MavenWrapperDependencyContext(project, virtualFile, projectId, releaseSources);
	}

	/**
	 * Return the wrapper-derived release sources for the given wrapper file,
	 * deduplicated by repository URL.
	 * @param wrapperFile the wrapper properties file.
	 * @return the release sources declared by supported wrapper URL properties.
	 */
	public static List<ReleaseSource> collectReleaseSources(PsiFile wrapperFile) {

		return CachedValuesManager.getProjectPsiDependentCache(wrapperFile, it -> {
			if (!(it instanceof PropertiesFile propertiesFile)) {
				return List.of();
			}
			Set<RemoteRepository> repositories = new LinkedHashSet<>();
			for (WrapperEntry entry : MavenWrapperParser.parse(propertiesFile)) {
				repositories.add(entry.repository());
			}
			return repositories.stream().map(MavenRepository::new)
					.map(rs -> (ReleaseSource) rs)
					.toList();
		});
	}

	public static class MavenWrapperDependencyContext extends AbstractProjectBuildContext
			implements ProjectDependencyContext {

		private final DependencyFileDelegate delegate;

		MavenWrapperDependencyContext(Project project, VirtualFile file, ProjectId projectId,
				List<ReleaseSource> releaseSources) {
			super(projectId, releaseSources);

			this.delegate = DependencyFileDelegate.of(project, file);
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return MavenWrapperInterface.INSTANCE;
		}

		@Override
		public PackageSystem getPackageSystem() {
			return PackageSystem.MAVEN;
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			return delegate.collectDependencies(it -> {
				DependencyCollector collector = new DependencyCollector();
				if (it instanceof PropertiesFile propertiesFile && MavenWrapperUtils.isWrapperFile(it)) {
					new MavenWrapperParser(collector).collect(propertiesFile);
				}
				return collector;
			});
		}

		@Override
		public boolean isVersionElement(PsiElement element) {

			if (!(element instanceof PropertyValueImpl value)) {
				return false;
			}

			if (!MavenWrapperUtils.isWrapperFile(value.getContainingFile())) {
				return false;
			}

			if (!(value.getParent() instanceof IProperty property)) {
				return false;
			}

			return WrapperProperty.isWrapperProperty(property);
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			LookupContext context = LookupContext.create(delegate, this);
			return new VersionUpgradeLookup(context, new MavenWrapperArtifactReferenceResolver());
		}

		@Override
		public void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {
			UpdateMavenWrapperProperties.applyUpdate(versionLiteral, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			UpdateMavenWrapperProperties.applyUpdates(psiFile, updates);
		}

		@Override
		public String toString() {
			return "MavenWrapperDependencyContext[%s] projectId=%s".formatted(delegate, getProjectId());
		}

	}

	/**
	 * Maven Wrapper-specific {@link InterfaceAssistant} supplying the display name
	 * and icons for Maven Wrapper version declarations.
	 */
	enum MavenWrapperInterface implements InterfaceAssistant {

		INSTANCE;

		@Override
		public String getDisplayName() {
			return MessageBundle.message("assistant.maven-wrapper");
		}

		@Override
		public Icon getGutterIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.UPGRADE_MAVEN_ICON;
		}

		@Override
		public Icon getNavigateIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.ICON;
		}

		@Override
		public Icon getTableIcon(Dependency dependency) {
			return MavenIcons.MavenProject;
		}

		@Override
		public TextRange getHighlightRange(PsiElement element) {

			PropertyValueImpl literal = element instanceof PropertyValueImpl pv ? pv
					: PsiTreeUtil.getParentOfType(element, PropertyValueImpl.class, false);
			if (literal == null
					|| !(literal.getContainingFile() instanceof PropertiesFile propertiesFile)
					|| !MavenWrapperUtils.isWrapperFile(propertiesFile)) {
				return element.getTextRange();
			}

			if (!(literal.getParent() instanceof PropertyImpl property)) {
				return literal.getTextRange();
			}

			WrapperEntry entry = MavenWrapperParser.parse(property);
			if (entry == null || entry.pathVersion().isEmpty()) {
				return literal.getTextRange();
			}

			return PropertyUtils.findTextRange(property, literal,
					MatchFunction.indexOf(entry.pathVersion()));
		}

	}

}
