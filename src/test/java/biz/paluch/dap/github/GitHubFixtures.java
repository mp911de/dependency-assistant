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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubServerPath;

/**
 * Fixtures for GitHub Actions workflow tests.
 *
 * @author Mark Paluch
 */
class GitHubFixtures {

	static final String SHA_V3 = "7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1";

		static final String SHA_V4 = "d1185ce59f7757407fe6a5febb1e03e3dba2a530";

		static final ArtifactId CHECKOUT = ArtifactId.of("actions", "checkout");

	/**
	 * Set up a cache with GitHub Actions releases and register contexts for the
	 * given project.
	 */
	static void setup(Project project) {

		Cache cache = buildCache();
		StateService service = StateService.getInstance(project);
		service.setCache(cache);
	}

	/**
	 * Analyze the given workflow file and register its dependency state.
	 */
	static DependencyCollector analyze(PsiFile file) {

		StateService service = StateService.getInstance(file.getProject());

		GitHubReleases releaseSource = new GitHubReleases(
				GithubServerPath.DEFAULT_SERVER, new EmptyApiClient(), 100);
		DependencyCollector collector = new GitHubDependencyCollector(file.getProject()).collect(file);

		GitHubProjectContext projectContext = new GitHubProjectContext(
				new ProjectId("github:actions", "checkout", file.getVirtualFile().getPath()), releaseSource);
		file.putUserData(GitHubProjectContext.KEY, projectContext);

		service.getProjectState(projectContext.getProjectId()).setDependencies(collector, PackageSystem.GITHUB);

		return collector;
	}

	private static Cache buildCache() {

		Cache cache = new Cache();

		CachedArtifact checkout = new CachedArtifact(CHECKOUT);
		checkout.addRelease(new CachedRelease("v4.2.0", "2024-10-01", SHA_V4));
		checkout.addRelease(new CachedRelease("v4.1.0", "2024-05-01", null));
		checkout.addRelease(new CachedRelease("v3.6.0", "2024-01-01", SHA_V3));
		cache.addArtifacts(List.of(checkout));

		return cache;
	}

	/**
	 * Test component {@link GitHubReleases.GitHubApiClient} that never makes a
	 * network call: the fixture cache is pre-populated, so any release-source
	 * lookup at test time yields an empty list.
	 */
	static class EmptyApiClient implements GitHubReleases.GitHubApiClient {

		@Override
		public <T> List<T> loadAll(ProgressIndicator indicator, GithubApiPagesLoader.Request<T> request) {
			return List.of();
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T loadOne(ProgressIndicator indicator, GithubApiRequest<T> request) {
			return (T) new org.jetbrains.plugins.github.api.data.GithubResponsePage<>(List.of(), null, null, null,
					null);
		}

	}

}
