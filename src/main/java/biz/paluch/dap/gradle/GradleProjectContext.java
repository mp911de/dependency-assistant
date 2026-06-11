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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.MavenRepository;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.ProjectBuildContext;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jspecify.annotations.Nullable;

/**
 * {@link ProjectBuildContext} for Gradle projects.
 *
 * @author Mark Paluch
 */
interface GradleProjectContext extends ProjectBuildContext {

	Key<GradleProjectContext> KEY = Key.create("GradleProjectContext");


	/**
	 * Looks up the {@link GradleProjectContext} for the given PSI file.
	 */
	static GradleProjectContext of(PsiFile file) {
		return of(file.getProject(), file);
	}

	/**
	 * Looks up the {@link GradleProjectContext} for the given PSI file.
	 */
	static GradleProjectContext of(Project project, @Nullable PsiFile file) {

		if (!GradleUtils.isGradleFile(file)) {
			return EmptyGradleBuildContext.INSTANCE;
		}

		GradleProjectContext context = file.getUserData(KEY);
		if (context != null) {
			return context;
		}
		VirtualFile virtualFile = file.getVirtualFile();
		if (virtualFile == null) {
			return EmptyGradleBuildContext.INSTANCE;
		}

		Module module = ModuleUtilCore.findModuleForFile(virtualFile, project);

		if (module == null) {
			return EmptyGradleBuildContext.INSTANCE;
		}

		String linkedPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
		// String linkedPath = GradleUtils.findLinkedProjectPath(project, virtualFile);
		if (linkedPath == null) {
			return EmptyGradleBuildContext.INSTANCE;
		}

		GradleProjectDescriptor descriptor = resolveDescriptor(project, file, linkedPath);

		ProjectId identity = resolveIdentity(project, file, linkedPath);
		if (identity == null) {
			return EmptyGradleBuildContext.INSTANCE;
		}

		return new GradleBuildContextImpl(project, descriptor);
	}

	private static GradleProjectDescriptor resolveDescriptor(
			Project project, PsiFile buildFile, String linkedPath) {

		VirtualFile virtualFile = buildFile.getVirtualFile();
		if (virtualFile == null) {
			return GradleProjectDescriptor.of(ProjectId.of(project.getName(), project.getName(), buildFile.getName()),
					"", "");
		}

		ExternalProjectInfo projectInfo = ProjectDataManager.getInstance()
				.getExternalProjectData(project, GradleConstants.SYSTEM_ID, linkedPath);
		ExternalProject root = ExternalProjectDataCache.getInstance(project)
				.getRootExternalProject(linkedPath);
		if (projectInfo == null || root == null) {
			return GradleProjectDescriptor.of(ProjectId.of(buildFile.getVirtualFile()), "", "");
		}

		DataNode<ProjectData> projectNode = projectInfo.getExternalProjectStructure();
		if (projectNode == null) {
			return GradleProjectDescriptor.of(ProjectId.of(buildFile.getVirtualFile()), "", "");
		}

		ProjectData data = projectNode.getData();
		String group = data.getGroup();
		String name = data.getExternalName();

		if (group == null || group.isBlank()) {
			group = name;
		}

		// TODO: Gradle project version? properties, build.gradle?
		String version = data.getVersion();// resolveVersion(project, linkedPath);
		ProjectId projectId = ProjectId.of(group, name, virtualFile.getPath());

		return GradleProjectDescriptor.of(projectId, version, linkedPath);
	}

	private static @Nullable String resolveVersion(Project project, String linkedPath) {
		ExternalProject rootExternalProject = ExternalProjectDataCache.getInstance(project)
				.getRootExternalProject(linkedPath);
		return rootExternalProject != null ? rootExternalProject.getVersion() : null;
	}

	/**
	 * Looks up the {@link GradleProjectContext} for the given virtual file, or
	 * returns {@link EmptyGradleBuildContext#INSTANCE} when no linked Gradle
	 * project contains it.
	 */
	static GradleProjectContext of(Project project, @Nullable VirtualFile file) {

		if (!GradleUtils.isGradleFile(file)) {
			return EmptyGradleBuildContext.INSTANCE;
		}

		BetterPsiManager psiManager = BetterPsiManager.getInstance(project);
		return ApplicationManager.getApplication().runReadAction((Computable<GradleProjectContext>) () -> {
			return psiManager.optional(file).map(GradleProjectContext::of).orElse(EmptyGradleBuildContext.INSTANCE);
		});
	}

	static boolean isGradleProject(Project project) {

		GradleSettings settings = GradleSettings.getInstance(project);
		Collection<GradleProjectSettings> linkedProjects = settings.getLinkedProjectsSettings();
		return !linkedProjects.isEmpty();
	}

	static List<ReleaseSource> getReleaseSources(Collection<RemoteRepository> repositories) {

		Set<ReleaseSource> sources = new LinkedHashSet<>();
		sources.add(GradlePluginPortalReleaseSource.INSTANCE);

		if (repositories.isEmpty()) {
			sources.add(ReleaseSource.mavenCentral());
		}

		for (RemoteRepository repository : repositories) {
			sources.add(new MavenRepository(repository));
		}

		return List.copyOf(sources);
	}

	/**
	 * Resolves the Gradle project's {@code group} and {@code name} from the
	 * external system data cache to build a {@link ProjectId}.
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

	class GradleBuildContextImpl implements GradleProjectContext {

		private final Project project;

		private final GradleProjectDescriptor descriptor;

		private final Versioned projectVersion;

		GradleBuildContextImpl(Project project, GradleProjectDescriptor descriptor) {

			this.project = project;
			this.descriptor = descriptor;
			this.projectVersion = descriptor.hasVersion()
					? ArtifactVersion.from(descriptor.version()).map(Versioned::of)
							.orElse(Versioned.unversioned())
					: Versioned.unversioned();
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public ProjectId getProjectId() {
			return descriptor.projectId();
		}

		@Override
		public Versioned getProjectVersion() {
			return projectVersion;
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {

			List<RemoteRepository> remoteRepositories = GradleUtils.getRepositoriesFromImportedProject(project,
					descriptor.linkedProjectPath());
			return GradleProjectContext.getReleaseSources(remoteRepositories);
		}

		@Override
		public String toString() {
			return "%s, Linked path: %s".formatted(getProjectId(), descriptor.linkedProjectPath());
		}

	}

	/**
	 * Absent Gradle project context.
	 */
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
		public List<ReleaseSource> getReleaseSources() {
			throw new IllegalStateException("Gradle BuildContext not available");
		}

		@Override
		public String toString() {
			return "Absent Gradle BuildContext";
		}

	}

}
