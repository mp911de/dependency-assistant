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

package biz.paluch.dap.gradle.wrapper;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.fixtures.DependencyAssistantFixtures;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Fixtures for Gradle Wrapper.
 *
 * @author Mark Paluch
 */
class GradleWrapperFixtures {

	static void setup(Project project) {
		DependencyAssistantFixtures.setup(project);
	}

	static DependencyCollector analyze(PsiFile file) {
		GradleWrapperAssistant assistant = new GradleWrapperAssistant();
		if (!assistant.supports(file)) {
			return new DependencyCollector();
		}

		DependencyCollector collector = new DependencyCollector();
		assistant.collect(file, collector);
		ProjectState state = StateService.getInstance(file.getProject())
				.getProjectState(assistant.createContext(file.getProject(), file).getProjectId());
		state.invalidateDependencies();
		state.setDependencies(collector, assistant.getPackageSystem());
		return collector;
	}

}
