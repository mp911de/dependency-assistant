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
package biz.paluch.dap.gradle;

import biz.paluch.dap.ProjectBuildContext;
import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RemoteRepositoryReleaseSource;
import biz.paluch.dap.maven.MavenProjectContext;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.Property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * {@link ProjectBuildContext} for Gradle projects. Mirrors the design of {@link MavenProjectContext}: a static
 * {@code of()} factory returns either a populated {@link GradleBuildContextImpl} or
 * {@link EmptyGradleBuildContext#INSTANCE} when the file is not in a linked Gradle project.
 * <p>
 * Repository sources always include Maven Central; Google Maven is also included as it is commonly used by Android
 * projects.
 *
 * @author Mark Paluch
 */
public interface GradleProjectContext extends ProjectBuildContext {

	/**
	 * Check whether the property is locally defined.
	 */
	boolean isLocalProperty(String name);

	/**
	 * Looks up the {@link GradleProjectContext} for the given PSI file.
	 */
	static GradleProjectContext of(Project project, @Nullable PsiFile file) {

		if (file == null) {
			return EmptyGradleBuildContext.INSTANCE;
		}

		String linkedPath = GradleUtils.findLinkedProjectPath(project, file.getVirtualFile());
		if (linkedPath == null) {
			return EmptyGradleBuildContext.INSTANCE;
		}

		ProjectId identity = resolveIdentity(project, file, linkedPath);
		if (identity == null) {
			return EmptyGradleBuildContext.INSTANCE;
		}

		return new GradleBuildContextImpl(project, file, linkedPath, identity);
	}

	/**
	 * Looks up the {@link GradleProjectContext} for the given virtual file, or returns
	 * {@link EmptyGradleBuildContext#INSTANCE} when no linked Gradle project contains it.
	 */
	static GradleProjectContext of(Project project, @Nullable VirtualFile file) {

		if (file == null) {
			return EmptyGradleBuildContext.INSTANCE;
		}

		PsiManager psiManager = PsiManager.getInstance(project);
		return ApplicationManager.getApplication().runReadAction((Computable<GradleProjectContext>) () -> {
			PsiFile psiFile = psiManager.findFile(file);
			if (psiFile != null) {
				return of(project, psiFile);
			}
			return EmptyGradleBuildContext.INSTANCE;
		});
	}

	static boolean isGradleProject(Project project) {

		GradleSettings settings = GradleSettings.getInstance(project);
		Collection<GradleProjectSettings> linkedProjects = settings.getLinkedProjectsSettings();
		return !linkedProjects.isEmpty();
	}

	static List<ReleaseSource> getReleaseSources(Collection<RemoteRepository> repositories) {

		List<ReleaseSource> sources = new ArrayList<>();
		sources.add(ReleaseSource.gradlePluginPortal());

		if (repositories.isEmpty()) {
			sources.add(ReleaseSource.mavenCentral());
		}

		for (RemoteRepository repository : repositories) {
			sources.add(new RemoteRepositoryReleaseSource(repository));
		}

		return sources;
	}

	/**
	 * Resolves the Gradle project's {@code group} and {@code name} from the external system data cache to build a
	 * {@link ProjectId}.
	 */
	private static @Nullable ProjectId resolveIdentity(Project project, PsiFile buildFile, String linkedPath) {

		ExternalProjectInfo projectInfo = ProjectDataManager.getInstance().getExternalProjectData(project,
				GradleConstants.SYSTEM_ID, linkedPath);

		if (projectInfo == null) {
			return null;
		}

		DataNode<ProjectData> projectNode = projectInfo.getExternalProjectStructure();
		if (projectNode == null) {
			return null;
		}
		ProjectData data = projectNode.getData();
		String group = data.getGroup();
		String name = data.getExternalName();
		if (group == null || group.isBlank()) {
			group = name;
		}
		return ProjectId.of(group, name, buildFile.getVirtualFile().getPath());
	}

	// -------------------------------------------------------------------------
	// Concrete implementation
	// -------------------------------------------------------------------------

	class GradleBuildContextImpl implements GradleProjectContext {

		private final DependencyAssistantService service;
		private final PsiFile buildFile;
		private final String linkedProjectPath;
		private final ProjectId identity;
		private final PsiManager psiManager;
		private final Map<String, String> properties = new HashMap<>();

		GradleBuildContextImpl(Project project, PsiFile buildFile, String linkedProjectPath, ProjectId identity) {

			this.service = DependencyAssistantService.getInstance(project);
			this.buildFile = buildFile;
			this.linkedProjectPath = linkedProjectPath;
			this.identity = identity;
			this.psiManager = PsiManager.getInstance(project);
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public ProjectId getProjectId() {
			return identity;
		}

		@Override
		public List<ReleaseSource> getReleaseSources(Project project) {

			List<RemoteRepository> remoteRepositories = GradleUtils.getRepositoriesFromImportedProject(project,
					linkedProjectPath);

			return GradleProjectContext.getReleaseSources(remoteRepositories);
		}

		@Override
		public boolean isLocalProperty(String name) {

			Map<String, String> properties = new HashMap<>();
			if (GradleUtils.isGroovyDsl(buildFile)) {
				properties.putAll(GradleParser.parseExtProperties(buildFile));
			}
			if (GradleUtils.isKotlinDsl(buildFile) && GradleUtils.KOTLIN_AVAILABLE) {
				properties.putAll(KotlinDslParser.parseExtraProperties(buildFile));
			}

			if (GradleUtils.isGradlePropertiesFile(buildFile)) {
				properties.putAll(GradleParser.parseGradleProperties(buildFile));
			}

			return properties.containsKey(name);
		}

		@Override
		public @Nullable String getPropertyValue(String name) {

			if (properties.containsKey(name)) {
				return properties.get(name);
			}

			ProjectProperty property = this.service.getCache().findProperty(name, Property::isDeclared);
			if (property == null || !StringUtils.hasText(property.id().buildFile())) {
				return null;
			}

			LocalFileSystem lfs = LocalFileSystem.getInstance();
			VirtualFile file = lfs.findFileByPath(property.id().buildFile());

			if (file == null || file.isDirectory()) {
				return null;
			}

			ApplicationManager.getApplication().runReadAction(() -> {

				PsiFile psiFile = psiManager.findFile(file);

				if (psiFile == null) {
					return;
				}

				if (GradleUtils.isGroovyDsl(file)) {
					properties.putAll(GradleParser.parseExtProperties(psiFile));
				}
				if (GradleUtils.isKotlinDsl(file) && GradleUtils.KOTLIN_AVAILABLE) {
					properties.putAll(KotlinDslParser.parseExtraProperties(psiFile));
				}

				if (GradleUtils.isGradlePropertiesFile(file)) {
					properties.putAll(GradleParser.parseGradleProperties(psiFile));
				}
			});

			return properties.get(name);
		}

	}

	// -------------------------------------------------------------------------
	// Empty / absent context
	// -------------------------------------------------------------------------

	enum EmptyGradleBuildContext implements GradleProjectContext {

		INSTANCE;

		@Override
		public boolean isAvailable() {
			return false;
		}

		@Override
		public ProjectId getProjectId() {
			throw new IllegalStateException("Gradle BuildContext not available");
		}

		@Override
		public List<ReleaseSource> getReleaseSources(Project project) {
			throw new IllegalStateException("Gradle BuildContext not available");
		}

		@Override
		public boolean isLocalProperty(String name) {
			return false;
		}

		@Override
		public @Nullable String getPropertyValue(String name) {
			throw new IllegalStateException("Gradle BuildContext not available");
		}
	}

}
