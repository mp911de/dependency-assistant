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

package biz.paluch.dap.state;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StateService}.
 *
 * @author Mark Paluch
 */
class StateServiceUnitTests {

	private static final ArtifactId SPRING_CORE = ArtifactId.of("org.springframework", "spring-core");

	private static final ArtifactId SPRING_TEST = ArtifactId.of("org.springframework", "spring-test");

	@Test
	void collectsDistinctDeclaredVersionsAcrossModules() {

		StateService service = new StateService();
		store(service, "com.acme", "app", SPRING_CORE, "6.0.0");
		store(service, "com.acme", "lib", SPRING_CORE, "6.1.0");

		assertThat(service.getDeclaredVersions(SPRING_CORE)).extracting(Object::toString)
				.containsExactlyInAnyOrder("6.0.0", "6.1.0");
	}

	@Test
	void collapsesAgreeingModulesToSingleVersion() {

		StateService service = new StateService();
		store(service, "com.acme", "app", SPRING_CORE, "6.1.0");
		store(service, "com.acme", "lib", SPRING_CORE, "6.1.0");

		assertThat(service.getDeclaredVersions(SPRING_CORE)).extracting(Object::toString)
				.containsExactly("6.1.0");
	}

	@Test
	void returnsEmptyForUnknownArtifact() {
		assertThat(new StateService().getDeclaredVersions(SPRING_CORE)).isEmpty();
	}

	@Test
	void visitsRuntimeDependencies() {

		StateService service = new StateService();
		store(service, "com.acme", "app", SPRING_CORE, "6.1.0");
		store(service, "com.acme", "lib", SPRING_TEST, "6.1.0");

		List<ArtifactId> visited = new ArrayList<>();
		service.doWithDependencies(dependency -> visited.add(dependency.getArtifactId()));
		assertThat(visited).containsExactlyInAnyOrder(SPRING_CORE, SPRING_TEST);
	}

	@Test
	void collectsVersionSourcesAcrossModules() {

		StateService service = new StateService();
		store(service, "com.acme", "app", SPRING_CORE, "6.1.0", VersionSource.property("spring.version"));
		store(service, "com.acme", "lib", SPRING_CORE, "6.1.0", VersionSource.declared("6.1.0"));

		assertThat(service.getVersionSources(SPRING_CORE)).hasSize(2)
				.anyMatch(VersionSource.VersionProperty.class::isInstance)
				.anyMatch(VersionSource.DeclaredVersion.class::isInstance);
	}

	@Test
	void excludesModuleFromVersionSourceLookup() {

		StateService service = new StateService();
		store(service, "com.acme", "app", SPRING_CORE, "6.1.0", VersionSource.property("spring.version"));
		store(service, "com.acme", "lib", SPRING_CORE, "6.1.0", VersionSource.declared("6.1.0"));

		assertThat(service.getVersionSources(it -> !it.equals(ProjectId.of("com.acme", "app")), SPRING_CORE))
				.singleElement()
				.isInstanceOf(VersionSource.DeclaredVersion.class);
	}

	private static void store(StateService service, String groupId, String artifactId, ArtifactId dependency,
			String version) {
		store(service, groupId, artifactId, dependency, version, VersionSource.declared(version));
	}

	private static void store(StateService service, String groupId, String artifactId, ArtifactId dependency,
			String version, VersionSource versionSource) {

		DependencyCollector collector = new DependencyCollector();
		collector.registerUsage(dependency, ArtifactVersion.of(version), DeclarationSource.dependency(),
				versionSource);
		service.getProjectState(ProjectId.of(groupId, artifactId))
				.setDependencies(collector);
	}

}
