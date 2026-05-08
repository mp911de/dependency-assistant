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

package biz.paluch.dap.npm;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Fixtures for NPM {@code package.json} tests.
 *
 * @author Mark Paluch
 */
class NpmFixtures {

	/**
	 * Set up a cache with NPM releases for the given project.
	 */
	static void setup(Project project) {

		Cache cache = new Cache();
		cache.addArtifacts(NpmReleases.all());
		StateService service = StateService.getInstance(project);
		service.setCache(cache);
	}

	/**
	 * Analyze the given {@code package.json} file and register its dependency
	 * state.
	 *
	 * <p>The returned collector is also stored in the shared {@link StateService}
	 * for the corresponding project id, with a test-scoped
	 * {@link NpmProjectContext} attached to the file's user data so downstream IDE
	 * features can resolve the context without re-running file detection.
	 */
	static DependencyCollector analyze(PsiFile file) {

		StateService service = StateService.getInstance(file.getProject());
		DependencyCollector collector = new NpmDependencyCollector(service.getCache()).collect(file);

		ProjectId projectId = ProjectId.of("npm", file.getVirtualFile().getNameWithoutExtension(),
				file.getVirtualFile().getPath());
		NpmProjectContext context = new NpmProjectContext(file.getProject(), projectId);
		file.putUserData(NpmProjectContext.KEY, context);

		service.getProjectState(context.getProjectId()).setDependencies(collector);
		return collector;
	}

}
