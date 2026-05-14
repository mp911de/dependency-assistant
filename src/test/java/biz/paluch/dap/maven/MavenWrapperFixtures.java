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

package biz.paluch.dap.maven;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.fixtures.DependencyAssistantFixtures;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Fixtures for Maven Wrapper.
 *
 * @author Mark Paluch
 */
class MavenWrapperFixtures {

	/**
	 * Set up Dependency Assistant for the given project.
	 */
	static void setup(Project project) {
		DependencyAssistantFixtures.setup(project);
	}

	/**
	 * Analyze the given file and return the dependency collector.
	 */
	public static DependencyCollector analyze(PsiFile file) {
		return new UpdateWrapperPropertiesProjectState(file.getProject()).update(file);
	}

}
