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

package biz.paluch.dap.gradle;

import java.util.Map;

import biz.paluch.dap.DependencyAssistantFixtures;
import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.gradle.GradleProjectContext.GradleBuildContextImpl;
import biz.paluch.dap.state.DependencyAssistantService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Fixtures for Gradle.
 * @author Mark Paluch
 */
class GradleFixtures {

	/**
	 * Set up Dependency Assistant for the given project.
	 */
	public static void setup(Project project) {
		DependencyAssistantFixtures.setup(project);
	}


	/**
	 * Analyze the given file and return the dependency collector.
	 * @return
	 */
	public static DependencyCollector analyze(PsiFile file) {
		return analyze(file, Map.of());
	}

	/**
	 * Analyze the given file and return the dependency collector.
	 * @return
	 */
	public static DependencyCollector analyze(PsiFile file, Map<String, String> properties) {

		GradleDependencyCollector parser = new GradleDependencyCollector(file.getProject(), properties);
		DependencyCollector collector = parser.collect(file);
		GradleProjectContext projectContext = new GradleBuildContextImpl(file.getProject(), "",
				new ProjectId("demo", "demo", file.getVirtualFile().getPath()));
		file.putUserData(GradleProjectContext.KEY, projectContext);
		DependencyAssistantService.getInstance(file.getProject()).getProjectState(projectContext.getProjectId())
				.setDependencies(collector);

		return collector;
	}

	public static void analyze(PsiFile... files) {
		for (PsiFile file : files) {
			analyze(file);
		}
	}

}
