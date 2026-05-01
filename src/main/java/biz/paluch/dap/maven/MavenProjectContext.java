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
import java.util.function.Function;

import biz.paluch.dap.ProjectBuildContext;
import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RepositoryCredentials;
import biz.paluch.dap.artifact.SettingsXmlCredentialsLoader;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jspecify.annotations.Nullable;

/**
 * Maven project context. Implements {@link ProjectBuildContext} so that all
 * IDE-integration code (annotators, completion contributors, etc.) can work
 * against the build-tool-agnostic interface while Maven-specific callers still
 * have access to {@link #getMavenId()} and {@link #doWithMaven(Function)}.
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

		if (!projectsManager.isMavenizedProject() || file == null) {
			return EmptyMavenContext.INSTANCE;
		}
		MavenProject mavenProject = projectsManager.findProject(file);
		if (mavenProject == null) {
			return EmptyMavenContext.INSTANCE;
		}

		return new MavenContextImpl(project, mavenProject, mavenProject.getMavenId());
	}

	/**
	 * Returns the associated Maven id.
	 */
	MavenId getMavenId();

	MavenProject getMavenProject();

	/**
	 * Maven project context.
	 */
	class MavenContextImpl implements MavenProjectContext {

		private final Project project;

		private final MavenProject mavenProject;

		private final MavenId id;

		private final ProjectId projectId;

		/**
		 * Create a context for the given Maven project.
		 */
		public MavenContextImpl(Project project, MavenProject mavenProject, MavenId id) {
			this.project = project;
			this.mavenProject = mavenProject;
			this.id = id;
			this.projectId = ProjectId.of(id.getGroupId(), id.getArtifactId(), null);
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public MavenId getMavenId() {
			return id;
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
		public MavenId getMavenId() {
			throw new IllegalStateException("Maven Context not available");
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

	}

}
