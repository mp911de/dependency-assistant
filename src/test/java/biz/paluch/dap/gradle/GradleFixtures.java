/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.paluch.dap.gradle;

import biz.paluch.dap.DependencyAssistantFixtures;
import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.gradle.GradleProjectContext.GradleBuildContextImpl;
import biz.paluch.dap.state.DependencyAssistantService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

import org.springframework.util.Assert;

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

	public static void analyzeGroovyDsl(PsiFile file) {

		Assert.isTrue(GradleUtils.isGroovyDsl(file), "Expected a Groovy DSL file, got: " + file.getName());

		DependencyCollector collector = new DependencyCollector();
		GradleParser parser = new GradleParser(collector);
		parser.parseGroovyDsl(file);
		GradleProjectContext projectContext = new GradleBuildContextImpl(file.getProject(), file, "",
				new ProjectId("demo", "demo", file.getVirtualFile().getPath()));
		file.putUserData(GradleProjectContext.KEY, projectContext);
		DependencyAssistantService.getInstance(file.getProject()).getProjectState(projectContext.getProjectId())
				.setDependencies(collector);
	}

	public static void analyze(PsiFile... files) {

		for (PsiFile file : files) {


			GradleDependencyCollector perFile = new GradleDependencyCollector(file.getProject());
			GradleProjectContext projectContext = new GradleBuildContextImpl(file.getProject(), file, "",
					new ProjectId("demo", "demo", file.getVirtualFile().getPath()));
			file.putUserData(GradleProjectContext.KEY, projectContext);

			perFile.collect(file);

			DependencyAssistantService.getInstance(file.getProject()).getProjectState(projectContext.getProjectId())
					.setDependencies(perFile.collect(file));
		}


	}

}
