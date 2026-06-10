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

import java.util.Map;

import biz.paluch.dap.IntrospectedDependencies;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.fixtures.DependencyAssistantFixtures;
import biz.paluch.dap.maven.MavenProjectContext.MavenContextImpl;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

/**
 * Fixtures for Maven.
 *
 * @author Mark Paluch
 */
class MavenFixtures {

	static final MavenId MAVEN_ID = new MavenId("com.example", "demo", "");

	static final ProjectId PROJECT_ID = ProjectId.of("com.example", "demo");

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
		return analyze(file, Map.of());
	}

	/**
	 * Analyze the given POM file, store the dependency state for annotators and
	 * completion contributors, and return the dependency collector.
	 */
	public static DependencyCollector analyze(PsiFile file, Map<String, String> properties) {

		if (MavenUtils.isMavenExtensionsFile(file)) {
			MavenExtensionsAssistant assistant = new MavenExtensionsAssistant();
			if (!assistant.supports(file)) {
				return new DependencyCollector();
			}
			DependencyCollector collector = new DependencyCollector();
			assistant.collect(file, collector);
			StateService service = StateService.getInstance(file.getProject());
			ProjectState projectState = service
					.getProjectState(assistant.createContext(file.getProject(), file).getProjectId());
			projectState.invalidateDependencies();
			projectState.setDependencies(collector);
			return collector;
		}

		MavenProject mavenProject = new MavenProject(file.getVirtualFile());
		mavenProject.updateMavenId(MAVEN_ID);

		MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(file.getProject());
		BetterPsiManager psiManager = BetterPsiManager.getInstance(file.getProject());
		MavenProjectContext projectContext = new MavenContextImpl(file.getProject(), projectsManager, psiManager,
				mavenProject);
		file.putUserData(MavenProjectContext.KEY, projectContext);

		MavenAssistant assistant = new MavenAssistant();
		DependencyCollector collector = new DependencyCollector();
		collector.addPropertyValues(properties);
		IntrospectedDependencies introspected = assistant.introspect(file.getProject());
		assistant.collect(file, collector, introspected);
		introspected.complete(collector);

		StateService service = StateService.getInstance(file.getProject());
		ProjectState projectState = service.getProjectState(projectContext.getProjectId());
		projectState.invalidateDependencies();
		projectState.setDependencies(collector);
		return collector;
	}

}
