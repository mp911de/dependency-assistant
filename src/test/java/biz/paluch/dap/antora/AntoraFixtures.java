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

package biz.paluch.dap.antora;

import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.github.TestGitHubReleases;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Fixtures for Antora playbook tests.
 *
 * @author Mark Paluch
 */
class AntoraFixtures {

	/**
	 * Set up a cache pre-populated with Antora UI releases for the given project.
	 */
	static void setup(Project project) {

		Cache cache = new Cache();
		cache.addArtifacts(TestGitHubReleases.ANTORA_UI);
		StateService service = StateService.getInstance(project);
		service.setCache(cache);
	}

	/**
	 * Analyze the given Antora playbook file and register its dependency state.
	 */
	static void analyze(PsiFile file) {
		new ProjectStateIndexer(file.getProject(), new EmptyProgressIndicator()).invalidate(AntoraAssistant.INSTANCE,
				file);
	}

}
