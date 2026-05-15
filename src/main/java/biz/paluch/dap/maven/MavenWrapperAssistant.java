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
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
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
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import icons.MavenIcons;

/**
 * Maven Wrapper implementation of {@link DependencyAssistant}.
 *
 * @author Mark Paluch
 */
class MavenWrapperAssistant implements DependencyAssistant {

	/**
	 * Return the wrapper-derived release sources for the given wrapper file,
	 * deduplicated by repository URL.
	 * @param wrapperFile the wrapper properties file.
	 * @return the release sources declared by supported wrapper URL properties.
	 */
	public static List<ReleaseSource> collectReleaseSources(PsiFile wrapperFile) {

		List<WrapperEntry> entries = new MavenWrapperParser().parse(wrapperFile);
		Set<RemoteRepository> repositories = new LinkedHashSet<>();
		for (WrapperEntry entry : entries) {
			repositories.add(entry.repository());
		}
		return MavenUtils.getReleaseSources(repositories);
	}

	@Override
	public String getId() {
		return "maven-wrapper";
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
		return MavenUtils.isWrapperFile(file);
	}

	@Override
	public DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator) {
		return new UpdateWrapperPropertiesProjectState(project).getAllDependencies(indicator);
	}

	@Override
	public void initializeState(Project project, ProgressIndicator indicator) {
		new UpdateWrapperPropertiesProjectState(project).readAndUpdateAll(indicator);
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Maven integration does not support " + anchor);
		}

		return CachedValuesManager.getProjectPsiDependentCache(anchor, MavenWrapperAssistant::createWrapperContext);
	}

	/**
	 * Determine the highlighting range for a property value.
	 * @param propertyValue the property value to inspect.
	 * @return the resulting highlight range.
	 */
	public static TextRange getHighlightRange(PropertyValueImpl propertyValue) {
		return MavenWrapperInterface.INSTANCE.getHighlightRange(propertyValue);
	}

	private static MavenWrapperDependencyContext createWrapperContext(PsiFile anchor) {

		Project project = anchor.getProject();
		VirtualFile virtualFile = anchor.getVirtualFile();
		ProjectId projectId = MavenProjectContext.createWrapperProjectId(virtualFile);
		List<ReleaseSource> releaseSources = collectReleaseSources(anchor);
		return new MavenWrapperDependencyContext(project, virtualFile, projectId, releaseSources);
	}


	static class MavenWrapperDependencyContext implements ProjectDependencyContext {

		private final Project project;

		private final VirtualFile anchor;

		private final ProjectId projectId;

		private final List<ReleaseSource> releaseSources;

		MavenWrapperDependencyContext(Project project, VirtualFile anchor, ProjectId projectId,
				List<ReleaseSource> releaseSources) {

			this.project = project;
			this.anchor = anchor;
			this.projectId = projectId;
			this.releaseSources = releaseSources;
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public ProjectId getProjectId() {
			return projectId;
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return releaseSources;
		}

		@Override
		public InterfaceAssistant getInterfaceAssistant() {
			return MavenWrapperInterface.INSTANCE;
		}

		@Override
		public void invalidateState(PsiFile file) {

			if (!MavenUtils.isWrapperFile(file)) {
				return;
			}

			new UpdateWrapperPropertiesProjectState(project).update(file);
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			PsiFile psiFile = PsiManager.getInstance(project).findFile(anchor);
			if (psiFile == null) {
				return new DependencyCollector();
			}

			return new MavenWrapperDependencyCollector().collect(psiFile);
		}

		@Override
		public boolean isVersionElement(PsiElement element) {

			if (!(element instanceof PropertyValueImpl value)) {
				return false;
			}

			if (!MavenUtils.isWrapperFile(value.getContainingFile())) {
				return false;
			}

			if (!(value.getParent() instanceof IProperty property)) {
				return false;
			}

			return WrapperProperty.isWrapperProperty(property);
		}

		@Override
		public VersionUpgradeLookupSupport getLookup(PsiElement element, VirtualFile file) {
			return new WrapperVersionUpgradeLookupService(project, this);
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
			return "MavenWrapperDependencyContext[%s] projectId=%s".formatted(anchor, projectId);
		}

	}

	/**
	 * Maven-specific user interface support.
	 */
	enum MavenWrapperInterface implements InterfaceAssistant {

		INSTANCE;

		@Override
		public String getDisplayName() {
			return MessageBundle.message("assistant.maven-wrapper");
		}

		@Override
		public String getDisplayName(VirtualFile file) {
			return getDisplayName();
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
					|| !MavenUtils.isWrapperFile(propertiesFile)) {
				return element.getTextRange();
			}

			if (!(literal.getParent() instanceof PropertyImpl property)) {
				return literal.getTextRange();
			}

			WrapperEntry entry = MavenWrapperParser.parse(property);
			if (entry == null || entry.pathVersion().isEmpty()) {
				return literal.getTextRange();
			}

			return MavenWrapperUtils.findTextRange(property, literal,
					MatchFunction.indexOf(entry.pathVersion()));
		}

	}

}
