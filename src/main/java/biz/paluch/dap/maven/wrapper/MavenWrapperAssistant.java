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

package biz.paluch.dap.maven.wrapper;

import java.util.Collection;
import java.util.List;

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
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.AbstractProjectBuildContext;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyFileDelegate;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileIndexLookup;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MatchFunction;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.PropertyUtils;
import biz.paluch.dap.util.PsiFileCache;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.CachedValuesManager;
import icons.MavenIcons;

import org.springframework.util.Assert;

/**
 * {@link DependencyAssistant} for Maven Wrapper property files.
 *
 * <p>This integration treats {@code distributionUrl} and {@code wrapperUrl} as
 * Maven dependency declarations for the Maven distribution and Maven Wrapper
 * artifacts. For trusted projects, release repositories are derived from the
 * declared URLs. Untrusted projects use Maven Central. No imported Maven
 * project model is required.
 *
 * <p>Contexts are cached on the wrapper PSI file and become unavailable only
 * when the file has no backing {@link VirtualFile}.
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
	public InterfaceAssistant getInterfaceAssistant() {
		return MavenWrapperInterface.INSTANCE;
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
	public boolean isVersionElement(PsiElement element) {
		return MavenWrapperUtils.isVersionElement(element);
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

		return CachedValuesManager.getProjectPsiDependentCache(anchor, this::createWrapperContext);
	}

	private ProjectDependencyContext createWrapperContext(PsiFile anchor) {

		VirtualFile virtualFile = anchor.getVirtualFile();
		if (virtualFile == null) {
			return ProjectDependencyContext.absent();
		}

		Project project = anchor.getProject();
		ProjectId projectId = MavenWrapperUtils.createProjectId(virtualFile);
		List<ReleaseSource> releaseSources = collectReleaseSources(anchor);

		return new MavenWrapperDependencyContext(this, project, virtualFile, projectId, releaseSources);
	}

	/**
	 * Return the wrapper-derived release sources for the given wrapper file,
	 * deduplicated by repository URL.
	 *
	 * <p>The result is cached until the PSI file changes. It follows the
	 * {@link MavenWrapperParser} collection policy: non-properties files, files
	 * without parseable supported URL properties, and entries whose two version
	 * occurrences differ contribute no release source.
	 *
	 * @param wrapperFile the wrapper properties file.
	 * @return the release sources declared by collectable wrapper URL properties.
	 */
	public static List<ReleaseSource> collectReleaseSources(PsiFile wrapperFile) {

		return PsiFileCache.get(wrapperFile, it -> {
			if (!(it instanceof PropertiesFile propertiesFile)) {
				return List.of();
			}
			return MavenWrapperParser.parseRepositories(propertiesFile).stream().map(MavenRepository::new)
					.map(rs -> (ReleaseSource) rs)
					.toList();
		});
	}

	/**
	 * File-scoped Maven Wrapper context backed by one physical wrapper properties
	 * file.
	 *
	 * <p>The context retains the release-source snapshot derived when it is
	 * created. PSI-dependent context caching recreates it after the anchor file
	 * changes.
	 */
	public static class MavenWrapperDependencyContext extends AbstractProjectBuildContext
			implements ProjectDependencyContext {

		private final DependencyFileDelegate delegate;

		private final DependencyAssistant assistant;

		private final List<ReleaseSource> releaseSources;

		MavenWrapperDependencyContext(DependencyAssistant assistant, Project project, VirtualFile file,
				ProjectId projectId,
				List<ReleaseSource> releaseSources) {
			super(projectId);
			this.assistant = assistant;
			this.releaseSources = releaseSources;
			this.delegate = DependencyFileDelegate.of(project, file);
		}

		@Override
		public DependencyAssistant getAssistant() {
			return assistant;
		}

		@Override
		public PackageSystem getPackageSystem() {
			return PackageSystem.MAVEN;
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			return delegate.collectDependencies(getPackageSystem(), it -> {
				DependencyCollector collector = new DependencyCollector(getPackageSystem());
				if (it instanceof PropertiesFile propertiesFile && MavenWrapperUtils.isWrapperFile(it)) {
					new MavenWrapperParser(collector).collect(propertiesFile);
				}
				return collector;
			});
		}

		@Override
		public boolean isVersionElement(PsiElement element) {
			return MavenWrapperUtils.isVersionElement(element);
		}

		@Override
		public VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file) {
			Assert.state(isAvailable(), "Project context is not available");
			return VersionUpgradeLookup.of(delegate.getProject(), getProjectId(),
					new MavenWrapperArtifactReferenceResolver());
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return this.releaseSources;
		}

		@Override
		public void applyUpdate(PsiElement versionLiteral, DependencyUpdate update) {
			UpdateMavenWrapperProperties.applyUpdate(versionLiteral, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, DependencyUpdates updates) {
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

			Property property = PropertyUtils.findProperty(element);
			PsiElement literal = property != null ? PropertyUtils.findPropertyValue(property) : null;
			if (literal == null
					|| !(literal.getContainingFile() instanceof PropertiesFile propertiesFile)
					|| !MavenWrapperUtils.isWrapperFile(propertiesFile)) {
				return element.getTextRange();
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
