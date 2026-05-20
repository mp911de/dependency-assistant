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

package biz.paluch.dap.gradle.wrapper;

import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.gradle.GradleDistributionReleaseSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.MatchFunction;
import biz.paluch.dap.util.PropertyUtils;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import icons.GradleIcons;

/**
 * Gradle Wrapper implementation of {@link DependencyAssistant}.
 *
 * @author Mark Paluch
 */
public class GradleWrapperAssistant implements DependencyAssistant {

	@Override
	public String getId() {
		return "gradle-wrapper";
	}

	@Override
	public String getDisplayName() {
		return GradleWrapperInterface.INSTANCE.getDisplayName();
	}

	@Override
	public boolean supports(Project project) {
		return true;
	}

	@Override
	public boolean supports(PsiFile file) {
		return GradleWrapperUtils.isWrapperFile(file);
	}

	@Override
	public DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator) {
		return new UpdateGradleWrapperPropertiesProjectState(project).getAllDependencies(indicator);
	}

	@Override
	public void initializeState(Project project, ProgressIndicator indicator) {

		Cache cache = StateService.getInstance(project).getCache();

		UpdateGradleWrapperPropertiesProjectState init = new UpdateGradleWrapperPropertiesProjectState(project);
		init.readAndUpdateAll(indicator);

		if (StringUtils.isEmpty(System.getProperty("junit.jupiter.extensions.autodetection.enabled"))) {

			DumbService ds = DumbService.getInstance(project);
			ds.queueTask(new DumbModeTask() {

				@Override
				public void performInDumbMode(ProgressIndicator indicator) {
					RefreshGradleWrapperVersions refresh = new RefreshGradleWrapperVersions(cache,
							init.getReleaseSources());
					refresh.refreshWrapperVersions(indicator);

					ds.runWhenSmart(() -> DaemonCodeAnalyzer.getInstance(project)
							.restart(MessageBundle.message("action.refresh-releases.task.done.title")));
				}

			});
		}
	}

	@Override
	public ProjectDependencyContext createContext(Project project, PsiFile anchor) {

		if (!supports(anchor)) {
			throw new IllegalStateException("Gradle wrapper integration does not support " + anchor);
		}

		return CachedValuesManager.getProjectPsiDependentCache(anchor, GradleWrapperAssistant::createWrapperContext);
	}

	private static ProjectDependencyContext createWrapperContext(PsiFile anchor) {

		VirtualFile virtualFile = anchor.getVirtualFile();
		if (virtualFile == null) {
			return ProjectDependencyContext.absent();
		}

		Project project = anchor.getProject();
		ProjectId projectId = GradleWrapperUtils.createProjectId(virtualFile);
		return new GradleWrapperDependencyContext(project, virtualFile, projectId,
				List.of(GradleDistributionReleaseSource.INSTANCE));
	}

	public static class GradleWrapperDependencyContext implements ProjectDependencyContext {

		private final Project project;

		private final VirtualFile anchor;

		private final ProjectId projectId;

		private final List<ReleaseSource> releaseSources;

		GradleWrapperDependencyContext(Project project, VirtualFile anchor, ProjectId projectId,
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
			return GradleWrapperInterface.INSTANCE;
		}

		@Override
		public void invalidateState(PsiFile file) {

			if (GradleWrapperUtils.isWrapperFile(file)) {
				new UpdateGradleWrapperPropertiesProjectState(project).update(file);
			}
		}

		@Override
		public DependencyCollector scanDependencies(ProgressIndicator indicator) {

			DependencyCollector collector = new DependencyCollector();
			PsiFile psiFile = PsiManager.getInstance(project).findFile(anchor);
			if (psiFile instanceof PropertiesFile propertiesFile && GradleWrapperUtils.isWrapperFile(psiFile)) {
				new GradleWrapperParser(collector).collect(propertiesFile);
			}
			return collector;
		}

		@Override
		public boolean isVersionElement(PsiElement element) {

			if (!(element instanceof PropertyValueImpl value)) {
				return false;
			}
			if (!GradleWrapperUtils.isWrapperFile(value.getContainingFile())) {
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
			UpdateGradleWrapperProperties.applyUpdate(versionLiteral, update);
		}

		@Override
		public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {
			UpdateGradleWrapperProperties.applyUpdates(psiFile, updates);
		}

		@Override
		public String toString() {
			return "GradleWrapperDependencyContext[%s] projectId=%s".formatted(anchor, projectId);
		}

	}

	enum GradleWrapperInterface implements InterfaceAssistant {

		INSTANCE;

		@Override
		public String getDisplayName() {
			return MessageBundle.message("assistant.gradle-wrapper");
		}

		@Override
		public String getDisplayName(VirtualFile file) {
			return getDisplayName();
		}

		@Override
		public Icon getGutterIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.UPGRADE_GRADLE_ICON;
		}

		@Override
		public Icon getNavigateIcon(ArtifactDeclaration declaration) {
			return DependencyAssistantIcons.ICON;
		}

		@Override
		public Icon getTableIcon(Dependency dependency) {
			return GradleIcons.Gradle;
		}

		@Override
		public TextRange getHighlightRange(PsiElement element) {

			PropertyValueImpl literal = element instanceof PropertyValueImpl pv ? pv
					: PsiTreeUtil.getParentOfType(element, PropertyValueImpl.class, false);
			if (literal == null
					|| !(literal.getContainingFile() instanceof PropertiesFile propertiesFile)
					|| !GradleWrapperUtils.isWrapperFile(propertiesFile)) {
				return element.getTextRange();
			}

			if (!(literal.getParent() instanceof PropertyImpl property)) {
				return literal.getTextRange();
			}

			GradleWrapperEntry entry = GradleWrapperParser.parse(property);
			if (entry == null || entry.versionText().isEmpty()) {
				return literal.getTextRange();
			}

			return PropertyUtils.findTextRange(property, literal,
					MatchFunction.indexOf(entry.versionText()));
		}

	}

}
