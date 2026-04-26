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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import biz.paluch.dap.DependencyAssistantFixtures;
import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.UpdatedBuildFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;

/**
 * Fixtures for Maven.
 *
 * @author Mark Paluch
 */
class MavenFixtures {

	private static final ProjectId TEST_PROJECT_ID = ProjectId.of("com.example", "demo");

	/**
	 * Set up Dependency Assistant for the given project.
	 */
	static void setup(Project project) {
		DependencyAssistantFixtures.setup(project);
	}

	/**
	 * Analyze the given POM file, store the dependency state for annotators and
	 * completion contributors, and return the dependency collector.
	 */
	static DependencyCollector analyze(PsiFile file) {

		DependencyCollector collector = new DependencyCollector();
		new MavenParser(collector, new HashMap<>()).parsePomFile(new Cache(), (XmlFile) file);

		file.putUserData(MavenProjectContext.KEY, new TestMavenContext(TEST_PROJECT_ID));
		DependencyAssistantService.getInstance(file.getProject()).getProjectState(TEST_PROJECT_ID)
				.setDependencies(collector);

		return collector;
	}

	/**
	 * Create an {@link UpdatedBuildFile} backed by fresh dependency analysis and
	 * property resolution for the given POM file.
	 */
	static UpdatedBuildFile updatedBuildFile(PsiFile file) {

		DependencyCollector collector = analyze(file);
		Map<String, String> properties = MavenParser.getProperties((XmlFile) file);
		PropertyResolver propertyResolver = properties::get;
		return UpdatedBuildFile.of(collector, propertyResolver, file.getName());
	}

	private static class TestMavenContext implements MavenProjectContext {

		private final ProjectId projectId;

		TestMavenContext(ProjectId projectId) {
			this.projectId = projectId;
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public MavenId getMavenId() {
			throw new UnsupportedOperationException();
		}

		@Override
		public MavenProject getMavenProject() {
			throw new UnsupportedOperationException();
		}

		@Override
		public ProjectId getProjectId() {
			return projectId;
		}

		@Override
		public List<ReleaseSource> getReleaseSources() {
			return List.of();
		}

		@Override
		public <T> T doWithMaven(Function<MavenProject, T> action) {
			throw new UnsupportedOperationException();
		}

	}

}
