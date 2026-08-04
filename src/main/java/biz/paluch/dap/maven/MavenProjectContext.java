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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.MavenRepository;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.ProjectBuildContext;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
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
	 * {@link PsiFile}.
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

		return new MavenContextImpl(project, projectsManager, BetterPsiManager.getInstance(project), mavenProject);
	}

	static boolean isValid(MavenId mavenId) {
		return StringUtils.hasText(mavenId.getGroupId()) && StringUtils.hasText(mavenId.getArtifactId());
	}

	static ProjectId createProjectId(MavenId mavenId) {
		Assert.hasText(mavenId.getGroupId(), "groupId must not be null or empty");
		Assert.hasText(mavenId.getArtifactId(), "groupId must not be null or empty");
		return new ProjectId(mavenId.getGroupId(), mavenId.getArtifactId(), null);
	}

	MavenPomProperties getPomProperties();

	/**
	 * Maven project context.
	 */
	class MavenContextImpl implements MavenProjectContext {

		private final Project project;

		private final MavenProjectsManager projectsManager;

		private final BetterPsiManager psiManager;

		private final MavenProject mavenProject;

		private final MavenPomProperties propertyResolver;

		private final ProjectId projectId;

		private final Versioned projectVersion;

		/**
		 * Create a context for the given Maven project.
		 */
		public MavenContextImpl(Project project, MavenProjectsManager projectsManager, BetterPsiManager psiManager,
				MavenProject mavenProject) {
			this.project = project;
			this.projectsManager = projectsManager;
			this.psiManager = psiManager;
			this.mavenProject = mavenProject;
			PsiFile file = psiManager.findFile(mavenProject.getFile());
			this.propertyResolver = file instanceof XmlFile pomFile ? createPomProperties(pomFile)
					: MavenPomProperties.from(List.of());
			this.projectId = createProjectId(mavenProject.getMavenId());
			this.projectVersion = resolveProjectVersion();
		}

		private Versioned resolveProjectVersion() {

			String version = propertyResolver.getProperty("project.version");
			if (!StringUtils.hasText(version)) {
				return Versioned.unversioned();
			}
			return ArtifactVersion.from(version.trim()).map(Versioned::of)
					.orElseGet(Versioned::unversioned);
		}

		private MavenPomProperties createPomProperties(XmlFile pomFile) {

			List<MavenProject> projectAndParents = getProjectHierarchy();
			List<XmlFile> pomFiles = new ArrayList<>();

			pomFiles.add(pomFile);
			for (int i = 1; i < projectAndParents.size(); i++) {
				PsiFile parentPom = psiManager.findFile(projectAndParents.get(i).getFile());
				if (parentPom instanceof XmlFile parentXml && MavenUtils.isMavenPomFile(parentXml)) {
					pomFiles.add(parentXml);
				}
			}

			MavenPomProperties projectProperties = MavenPomProperties.from(pomFiles);
			if (projectsManager.findProject(mavenProject.getFile()) == null) {
				return projectProperties;
			}
			Properties modelProperties = mavenProject.getProperties();
			return projectProperties.withFallback(modelProperties::getProperty);
		}

		private List<MavenProject> getProjectHierarchy() {

			List<MavenProject> hierarchy = new ArrayList<>();
			Set<MavenId> visited = new HashSet<>();
			MavenProject current = this.mavenProject;

			while (current != null && visited.add(current.getMavenId())) {
				hierarchy.add(current);
				MavenId parentId = current.getParentId();
				current = parentId != null ? projectsManager.findProject(parentId) : null;
			}
			return hierarchy;
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
		public PackageSystem getPackageSystem() {
			return PackageSystem.MAVEN;
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {

			MavenSettings settings = SettingsXmlLoader.load(project);
			PsiFile file = psiManager.findFile(mavenProject.getFile());

			Set<RemoteRepository> remoteRepositories = MavenRepositories.getRemoteRepositories(settings,
					mavenProject, file, this.propertyResolver);
			return remoteRepositories.stream().map(MavenRepository::new)
					.map(it -> (ReleaseSource) it)
					.toList();
		}

		@Override
		public Versioned getProjectVersion() {
			return projectVersion;
		}

		@Override
		public MavenPomProperties getPomProperties() {
			return propertyResolver;
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
		public ProjectId getProjectId() {
			throw new IllegalStateException("Maven Context not available");
		}

		@Override
		public PackageSystem getPackageSystem() {
			throw new IllegalStateException("Maven Context not available");
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			throw new IllegalStateException("Maven Context not available");
		}

		@Override
		public MavenPomProperties getPomProperties() {
			throw new IllegalStateException("Maven Context not available");
		}

		@Override
		public String toString() {
			return "Absent Maven Context";
		}

	}

}
