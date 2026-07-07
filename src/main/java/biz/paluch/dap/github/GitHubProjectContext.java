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

import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.ReleaseSource;
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

	private final GithubApiRequestExecutorFactory factory;

	/**
	 * Create a context for the given project identity and release source.
	 * @param project the project identity.
	 * @param projectId the project identity.
	 */
	GitHubProjectContext(Project project, ProjectId projectId) {
		super(projectId);
		this.factory = GithubApiRequestExecutorFactory.getInstance(project);
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
		return new GitHubProjectContext(project, ProjectId.of(anchor));
	}

	@Override
	public PackageSystem getPackageSystem() {
		return PackageSystem.GITHUB;
	}

	@Override
	public List<ReleaseSource> getReleaseSources() {

		GithubApiRequestExecutorFactory.ExecutorResult executor = factory.getExecutor();
		if (executor.hasExecutor()) {
			GitHubReleases gitHubReleases = new GitHubReleases(executor.getDecision()
					.getServer(), executor.getRequiredExecutor());
			return List.of(gitHubReleases);
		}

		return List.of();
	}
}
