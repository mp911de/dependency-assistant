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

import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RepositoryCredentials;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.ProjectBuildContext;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Maven project context. Implements {@link ProjectBuildContext} so that all
 * IDE-integration code (annotators, completion contributors, etc.) can work
 * against the build-tool-agnostic interface.
 *
 * @author Mark Paluch
 */
interface MavenProjectContext extends ProjectBuildContext {

	Key<MavenProjectContext> KEY = Key.create("MavenProjectContext");

	/**
	 * Lookup the {@link MavenProjectContext} for the given {@link Project} and
	 * {@link VirtualFile}.
	 */
	static MavenProjectContext of(Project project, @Nullable PsiFile file) {

		if (file == null) {
			return EmptyMavenContext.INSTANCE;
		}

		MavenProjectContext injected = file.getUserData(KEY);
		if (injected != null) {
			return injected;
		}

		return of(project, file.getVirtualFile());
	}

	/**
	 * Lookup the {@link MavenProjectContext} for the given {@link Project} and
	 * {@link VirtualFile}.
	 */
	static MavenProjectContext of(Project project, @Nullable VirtualFile file) {

		MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
		return of(project, projectsManager, file);
	}

	/**
	 * Lookup the {@link MavenProjectContext} for the given {@link Project} and
	 * {@link VirtualFile}.
	 */
	static MavenProjectContext of(Project project, MavenProjectsManager projectsManager, @Nullable VirtualFile file) {

		if (!projectsManager.isMavenizedProject() || file == null) {
			return EmptyMavenContext.INSTANCE;
		}
		MavenProject mavenProject = projectsManager.findProject(file);
		if (mavenProject == null || !isValid(mavenProject.getMavenId())) {
			return EmptyMavenContext.INSTANCE;
		}

		return new MavenContextImpl(project, mavenProject);
	}

	static boolean isValid(MavenId mavenId) {
		return StringUtils.hasText(mavenId.getGroupId()) && StringUtils.hasText(mavenId.getArtifactId());
	}

	static ProjectId createProjectId(MavenId mavenId) {
		Assert.hasText(mavenId.getGroupId(), "groupId must not be null or empty");
		Assert.hasText(mavenId.getArtifactId(), "groupId must not be null or empty");
		return new ProjectId(mavenId.getGroupId(), mavenId.getArtifactId(), null);
	}

	static ProjectId createWrapperProjectId(VirtualFile virtualFile) {
		return new ProjectId("org.apache.maven", "apache-maven", virtualFile.getPath());
	}

	/**
	 * Return the Maven project.
	 */
	MavenProject getMavenProject();

	/**
	 * Maven project context.
	 */
	class MavenContextImpl implements MavenProjectContext {

		private final Project project;

		private final MavenProject mavenProject;

		private final ProjectId projectId;

		/**
		 * Create a context for the given Maven project.
		 */
		public MavenContextImpl(Project project, MavenProject mavenProject) {
			this(project, mavenProject.getMavenId(), mavenProject);
		}

		/**
		 * Create a context for the given Maven project.
		 */
		public MavenContextImpl(Project project, MavenId id, MavenProject mavenProject) {
			this.project = project;
			this.mavenProject = mavenProject;
			this.projectId = createProjectId(id);
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public MavenProject getMavenProject() {
			return mavenProject;
		}

		@Override
		public ProjectId getProjectId() {
			return projectId;
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {

			Map<String, RepositoryCredentials> credentials = SettingsXmlCredentialsLoader.load(project);
			Set<RemoteRepository> remoteRepositories = MavenUtils.getRemoteRepositories(credentials, mavenProject);
			return MavenUtils.getReleaseSources(remoteRepositories);
		}

		@Override
		public boolean isAbsent() {
			return MavenProjectContext.super.isAbsent();
		}

		@Override
		public String toString() {
			return "%s, MavenProject: %s".formatted(projectId, mavenProject);
		}

	}

	/**
	 * Absent Maven context.
	 */
	enum EmptyMavenContext implements MavenProjectContext {

		INSTANCE;

		@Override
		public boolean isAvailable() {
			return false;
		}

		@Override
		public MavenProject getMavenProject() {
			throw new IllegalStateException("Maven Context not available");
		}

		@Override
		public ProjectId getProjectId() {
			throw new IllegalStateException("Maven Context not available");
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			throw new IllegalStateException("Maven Context not available");
		}

		@Override
		public String toString() {
			return "Absent Maven Context";
		}

	}

}
