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

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.fixtures.DependencyAssistantFixtures;
import biz.paluch.dap.maven.MavenProjectContext.MavenContextImpl;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.idea.maven.model.MavenId;

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

		DependencyCollector collector = new DependencyCollector();
		new MavenParser(collector, properties).parsePomFile(new Cache(), (XmlFile) file);
		MavenProjectContext projectContext = new MavenContextImpl(file.getProject(), MAVEN_ID, null);
		file.putUserData(MavenProjectContext.KEY, projectContext);
		StateService.getInstance(file.getProject()).getProjectState(PROJECT_ID)
				.setDependencies(collector);

		return collector;
	}

}
