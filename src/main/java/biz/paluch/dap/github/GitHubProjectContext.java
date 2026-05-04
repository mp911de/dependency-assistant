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

package biz.paluch.dap.github;

import java.util.List;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.ProjectBuildContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * {@link ProjectBuildContext} for a single supported GitHub Actions YAML file.
 *
 * <p>The project identity uses the owning repository coordinates and the
 * absolute file path. There is one context per file so independent declarations
 * in the same repository can maintain independent dependency state.
 *
 * @author Mark Paluch
 */
class GitHubProjectContext implements ProjectBuildContext {

	/**
	 * Key used to inject a test-scoped context into a PSI file's user data.
	 */
	static final Key<GitHubProjectContext> KEY = Key.create("GitHubProjectContext");

	private final ProjectId projectId;

	private final GitHubReleaseSource releaseSource;

	/**
	 * Create a context for the given project identity and release source.
	 * @param projectId the project identity.
	 * @param releaseSource the release source for this GitHub Actions file.
	 */
	GitHubProjectContext(ProjectId projectId, GitHubReleaseSource releaseSource) {
		this.projectId = projectId;
		this.releaseSource = releaseSource;
	}

	/**
	 * Create a context for the given project and anchor file.
	 * @param project the IntelliJ project.
	 * @param anchor the supported GitHub Actions file.
	 * @return the context to be used.
	 */
	public static GitHubProjectContext of(Project project, VirtualFile anchor) {

		GitRepositoryResolver repositoryResolver = new GitRepositoryResolver(project);
		GitRepositoryMetadata gitRepository = repositoryResolver.resolveOwnerAndRepository(anchor);
		GitHubReleaseSource releaseSource = gitRepository != null
				? GitHubReleaseSource.from(project, gitRepository.host())
				: GitHubReleaseSource.from(project);

		ProjectId projectId = gitRepository != null
				? ProjectId.of(gitRepository.owner(), gitRepository.repository(), anchor.getPath())
				: ProjectId.of("", "", anchor.getPath());
		return new GitHubProjectContext(projectId, releaseSource);
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public ProjectId getProjectId() {
		return projectId;
	}

	/**
	 * Return the typed release source for this GitHub Actions file.
	 * @return the release source; guaranteed to be not {@literal null}.
	 */
	GitHubReleaseSource getReleaseSource() {
		return releaseSource;
	}

	@Override
	public List<ReleaseSource> getReleaseSources() {
		return List.of(releaseSource);
	}

}
