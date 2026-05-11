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

package biz.paluch.dap.antora;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Fixtures for Antora playbook tests.
 *
 * @author Mark Paluch
 */
class AntoraFixtures {

	static final ArtifactId ANTORA_UI = GitArtifactId.of("github.com", "spring-io", "antora-ui-spring");

	/**
	 * Set up a cache pre-populated with Antora UI releases for the given project.
	 */
	static void setup(Project project) {

		Cache cache = buildCache();
		StateService service = StateService.getInstance(project);
		service.setCache(cache);
	}

	/**
	 * Analyze the given Antora playbook file and register its dependency state.
	 */
	static DependencyCollector analyze(PsiFile file) {

		StateService service = StateService.getInstance(file.getProject());

		DependencyCollector collector = new AntoraDependencyCollector().collect(file);

		AntoraProjectContext projectContext = new AntoraProjectContext(
				new ProjectId("antora", "antora-playbook", file.getVirtualFile().getPath()), List.<ReleaseSource>of());
		file.putUserData(AntoraProjectContext.KEY, projectContext);

		service.getProjectState(projectContext.getProjectId()).setDependencies(collector);

		return collector;
	}

	private static Cache buildCache() {

		Cache cache = new Cache();

		CachedArtifact ui = new CachedArtifact(ArtifactId.of("spring-io", "antora-ui-spring"));
		ui.getReleases().add(new CachedRelease("v0.4.26", "2025-01-01",
				"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
		ui.getReleases().add(new CachedRelease("v0.4.25", "2024-12-01",
				"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
		ui.getReleases().add(new CachedRelease("v0.3.0", "2024-06-01",
				"cccccccccccccccccccccccccccccccccccccccc"));
		cache.addArtifacts(List.of(ui));

		return cache;
	}

}
