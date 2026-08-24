/*
 * Copyright 2026-present the original author or authors.
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
import org.jspecify.annotations.Nullable;

/**
 * {@link ProjectBuildContext} for a single supported GitHub Actions YAML file.
 *
 * <p>The project identity contains the absolute file path. Production contexts
 * obtain a GitHub API executor each time release sources are requested and
 * expose no source when executor resolution fails. Injected contexts expose
 * their supplied release source directly.
 *
 * @author Mark Paluch
 */
class GitHubProjectContext extends AbstractProjectBuildContext {

	static final Key<GitHubProjectContext> KEY = Key.create("GitHubProjectContext");

	private final @Nullable GithubApiRequestExecutorFactory factory;

	private final List<ReleaseSource> releaseSources;

	/**
	 * Create a context that resolves GitHub release sources through the project
	 * service.
	 *
	 * @param project the IntelliJ project used for account resolution.
	 * @param projectId the file-scoped project identity.
	 */
	GitHubProjectContext(Project project, ProjectId projectId) {
		super(projectId);
		this.factory = GithubApiRequestExecutorFactory.getInstance(project);
		this.releaseSources = List.of();
	}

	GitHubProjectContext(ProjectId projectId, ReleaseSource releaseSource) {
		super(projectId);
		this.factory = null;
		this.releaseSources = List.of(releaseSource);
	}

	/**
	 * Create a context for the given project and anchor file.
	 *
	 * @param project the IntelliJ project.
	 * @param anchor the supported GitHub Actions file.
	 * @return a context scoped to the anchor file.
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

		if (factory == null) {
			return releaseSources;
		}

		GithubApiRequestExecutorFactory.ExecutorResult executor = factory.getExecutor();
		if (executor.hasExecutor()) {
			GitHubReleases gitHubReleases = new GitHubReleases(executor.getDecision()
					.getServer(), executor.getRequiredExecutor());
			return List.of(gitHubReleases);
		}

		return releaseSources;
	}
}
