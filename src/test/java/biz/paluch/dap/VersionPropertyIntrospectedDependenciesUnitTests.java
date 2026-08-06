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

package biz.paluch.dap;

import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.ProjectId;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link VersionPropertyIntrospectedDependencies}.
 *
 * @author Mark Paluch
 */
class VersionPropertyIntrospectedDependenciesUnitTests {

	private static final ArtifactId JUNIT = ArtifactId.of("org.junit.jupiter", "junit-jupiter");

	private static final ProjectId PARENT = ProjectId.of("com.example", "parent");

	private static final ProjectId MODULE = ProjectId.of("com.example", "module");

	@Test
	void promotesDeclarationBackedByPropertyDeclaredInAnotherModule() {

		VersionPropertyIntrospectedDependencies introspected = new VersionPropertyIntrospectedDependencies(Map.of());
		introspected.register(MODULE, declaring(JUNIT, "junit.version"));

		DependencyCollector parent = resolving("junit.version", "5.11.0");
		introspected.register(PARENT, parent);
		introspected.complete(parent);

		assertThat(parent)
				.hasDependencyUsage("junit-jupiter")
				.hasVersion("5.11.0")
				.hasPropertyVersion("junit.version");
	}

	@Test
	void keepsExistingUsageInsteadOfPromoting() {

		DependencyCollector parent = resolving("junit.version", "5.11.0");
		parent.registerUsage(JUNIT, ArtifactVersion.of("5.10.0"), DeclarationSource.dependency(),
				VersionSource.declared("5.10.0"));

		VersionPropertyIntrospectedDependencies introspected = new VersionPropertyIntrospectedDependencies(Map.of());
		introspected.register(MODULE, declaring(JUNIT, "junit.version"));
		introspected.complete(parent);

		assertThat(parent).hasDependencyUsage("junit-jupiter").hasVersion("5.10.0");
	}

	@Test
	void seededCollectorContributesWhenOnlyOneFileIsRecollected() {

		Map<ProjectId, DependencyCollector> stored = Map.of(MODULE, declaring(JUNIT, "junit.version"));

		DependencyCollector parent = resolving("junit.version", "5.11.0");
		VersionPropertyIntrospectedDependencies introspected = new VersionPropertyIntrospectedDependencies(stored);
		introspected.register(PARENT, parent);
		introspected.complete(parent);

		assertThat(parent).hasDependencyUsage("junit-jupiter").hasVersion("5.11.0");
	}

	@Test
	void registeredCollectorReplacesSeededOneForSameProject() {

		Map<ProjectId, DependencyCollector> stored = Map.of(MODULE, declaring(JUNIT, "junit.version"));
		DependencyCollector recollected = new DependencyCollector(PackageSystem.MAVEN);

		DependencyCollector parent = resolving("junit.version", "5.11.0");
		VersionPropertyIntrospectedDependencies introspected = new VersionPropertyIntrospectedDependencies(stored);
		introspected.register(MODULE, recollected);
		introspected.register(PARENT, parent);
		introspected.complete(parent);

		assertThat(parent.getUsage(JUNIT)).isNull();
	}

	@Test
	void ignoresPropertyDeclaredWithoutValue() {

		DependencyCollector parent = resolving("junit.version", "");
		VersionPropertyIntrospectedDependencies introspected = new VersionPropertyIntrospectedDependencies(Map.of());
		introspected.register(MODULE, declaring(JUNIT, "junit.version"));
		introspected.complete(parent);

		assertThat(parent.getUsage(JUNIT)).isNull();
	}

	private static DependencyCollector declaring(ArtifactId artifactId, String propertyName) {

		DependencyCollector collector = new DependencyCollector(PackageSystem.MAVEN);
		collector.registerDeclaration(artifactId, DeclarationSource.dependency(), VersionSource.property(propertyName));
		return collector;
	}

	private static DependencyCollector resolving(String propertyName, String value) {

		DependencyCollector collector = new DependencyCollector(PackageSystem.MAVEN);
		collector.addPropertyValues(Map.of(propertyName, value));
		return collector;
	}

}
