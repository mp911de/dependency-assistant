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

package biz.paluch.dap.rule;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;

/**
 * Test fixtures for {@link DependencyfileCompletionContributor} tests.
 *
 * @author Mark Paluch
 */
class DependencyfileCompletionFixtures {

	static final ArtifactId SPRING_CORE = ArtifactId.of("org.springframework", "spring-core");

	static final ArtifactId SPRING_WEB = ArtifactId.of("org.springframework", "spring-web");

	static final ArtifactId JUNIT = ArtifactId.of("org.junit.jupiter", "junit-jupiter");

	static void setup(Project project) {

		DependencyCollector collector = new DependencyCollector();
		register(collector, SPRING_CORE);
		register(collector, SPRING_WEB);
		register(collector, JUNIT);

		StateService.getInstance(project)
				.getProjectState(ProjectId.of("test", "project"))
				.setDependencies(collector, PackageSystem.MAVEN);
	}

	private static void register(DependencyCollector collector, ArtifactId artifactId) {
		collector.registerUsage(artifactId, ArtifactVersion.of("1.0"), DeclarationSource.dependency(),
				VersionSource.none());
	}

}
