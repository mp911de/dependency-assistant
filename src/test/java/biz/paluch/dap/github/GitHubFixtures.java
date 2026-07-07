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

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.fixtures.TestProjects;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Fixtures for GitHub Actions workflow tests.
 *
 * @author Mark Paluch
 */
class GitHubFixtures {

	/**
	 * Set up a cache with GitHub Actions releases and register contexts for the
	 * given project.
	 */
	static void setup(Project project) {

		Cache cache = new Cache();
		cache.addArtifacts(TestGitHubReleases.actions());
		StateService service = StateService.getInstance(project);
		service.setCache(cache);
	}

	/**
	 * Analyze the given workflow file and register its dependency state.
	 */
	static DependencyCollector analyze(PsiFile file) {

		StateService service = StateService.getInstance(file.getProject());

		DependencyCollector collector = new GitHubDependencyCollector(file.getProject()).collect(file);

		GitHubProjectContext projectContext = new GitHubProjectContext(
				TestProjects.PROJECT, new ProjectId("github:actions", "checkout", file.getVirtualFile().getPath()));
		file.putUserData(GitHubProjectContext.KEY, projectContext);

		service.getProjectState(projectContext.getProjectId()).setDependencies(collector, PackageSystem.GITHUB);

		return collector;
	}

	static void registerSetupJavaShaDependency(Project project) {

		DependencyCollector collector = new DependencyCollector();
		collector.registerUsage(TestGitHubReleases.SETUP_JAVA,
				new GitRef("1bcf9fb12cf4aa7d266a90ae39939e61372fe520"), DeclarationSource.dependency(),
				VersionSource.declared("1bcf9fb12cf4aa7d266a90ae39939e61372fe520"));

		StateService.getInstance(project)
				.getProjectState(ProjectId.of("github:actions", "stale-workflow"))
				.setDependencies(collector, PackageSystem.GITHUB);
	}

}
