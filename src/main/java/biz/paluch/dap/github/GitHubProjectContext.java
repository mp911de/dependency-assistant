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
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.AbstractProjectBuildContext;
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
class GitHubProjectContext extends AbstractProjectBuildContext {

	/**
	 * User-data key under which {@link #of(Project, VirtualFile)} caches the
	 * resolved context on the anchor {@link VirtualFile} so it is computed once per
	 * file.
	 */
	static final Key<GitHubProjectContext> KEY = Key.create("GitHubProjectContext");

	/**
	 * Create a context for the given project identity and release source.
	 * @param projectId the project identity.
	 * @param releaseSource the release source for this GitHub Actions file.
	 */
	GitHubProjectContext(ProjectId projectId, GitHubReleases releaseSource) {
		super(projectId, List.of(releaseSource));
	}

	/**
	 * Create a context for the given project and anchor file.
	 * @param project the IntelliJ project.
	 * @param anchor the supported GitHub Actions file.
	 * @return the context to be used.
	 */
	public static GitHubProjectContext of(Project project, VirtualFile anchor) {

		GitHubProjectContext cached = anchor.getUserData(KEY);

		if (cached != null) {
			return cached;
		}
		cached = create(project, anchor);

		return cached;
	}

	private static GitHubProjectContext create(Project project, VirtualFile anchor) {

		GitRepositoryResolver repositoryResolver = new GitRepositoryResolver(project);
		GitRepositoryMetadata gitRepository = repositoryResolver.resolveOwnerAndRepository(anchor);
		GitHubReleases releaseSource = gitRepository != null
				? GitHubReleases.from(project, gitRepository.host())
				: GitHubReleases.from(project);

		ProjectId projectId = gitRepository != null
				? ProjectId.of(gitRepository.owner(), gitRepository.repository(), anchor.getPath())
				: ProjectId.of(anchor);
		return new GitHubProjectContext(projectId, releaseSource);
	}

	@Override
	public PackageSystem getPackageSystem() {
		return PackageSystem.GITHUB;
	}

}
