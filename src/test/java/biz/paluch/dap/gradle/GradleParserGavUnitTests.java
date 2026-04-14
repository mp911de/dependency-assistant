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

import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleDependency.PropertyManagedDependency;
import biz.paluch.dap.gradle.GradleDependency.SimpleDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GradleParser#parseGav(String)}.
 *
 * @author Mark Paluch
 */
class GradleParserGavUnitTests {

	@Test
	void parsesLiteralGav() {

		GradleParser parser = new GradleParser();
		SimpleDependency dependency = (SimpleDependency) parser
				.parseGav("org.springframework:spring-core:6.1.0");

		assertThat(dependency).isNotNull();
		assertThat(dependency.id().groupId()).isEqualTo("org.springframework");
		assertThat(dependency.id().artifactId()).isEqualTo("spring-core");
		assertThat(dependency.version()).isEqualTo("6.1.0");
		assertThat(dependency.versionSource()).isInstanceOf(VersionSource.DeclaredVersion.class);
	}

	@Test
	void parsesGavWithPropertyVersion() {

		Map<String, String> props = Map.of("springVersion", "6.1.0");
		GradleParser parser = new GradleParser();
		parser.getPropertyMap().putAll(props);
		GradleDependency dependency = parser.parseGav("org.springframework:spring-core:${springVersion}");

		assertThat(dependency).isNotNull();
		assertThat(dependency.getId().groupId()).isEqualTo("org.springframework");
		assertThat(dependency.getId().artifactId()).isEqualTo("spring-core");
		assertThat(dependency.getVersionSource()).isInstanceOf(VersionSource.VersionProperty.class);
		assertThat(dependency.getVersionSource()).isEqualTo(VersionSource.property("springVersion"));
	}

	@Test
	void returnsNullForGavWithUnresolvablePropertyVersion() {

		GradleParser parser = new GradleParser();
		GradleDependency dependency = parser.parseGav("org.springframework:spring-core:${springVersion}");

		assertThat(dependency).isNotNull().isInstanceOf(PropertyManagedDependency.class);
		assertThat(dependency.getId().groupId()).isEqualTo("org.springframework");
		assertThat(dependency.getId().artifactId()).isEqualTo("spring-core");
		assertThat(dependency.getVersionSource()).isInstanceOf(VersionSource.VersionProperty.class);
		assertThat(dependency.getVersionSource()).isEqualTo(VersionSource.property("springVersion"));
	}

	@Test
	void returnsNullForNullInput() {

		GradleParser parser = new GradleParser();

		assertThat(parser.parseGav(null)).isNull();
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"com.example:artifact:1.0.0, com.example, artifact, 1.0.0",
			"org.example.group:my-artifact:2.3.4, org.example.group, my-artifact, 2.3.4"})
	void parsesVariousCoordinates(String gav, String group, String artifact, String version) {

		GradleParser parser = new GradleParser();
		GradleDependency parsed = parser.parseGav(gav);

		assertThat(parsed).isNotNull();
		assertThat(parsed.getId().groupId()).isEqualTo(group);
		assertThat(parsed.getId().artifactId()).isEqualTo(artifact);
	}

	@Test
	void versionSourceIsDeclaredForLiteralVersion() {

		GradleParser parser = new GradleParser();
		GradleDependency dependency = parser.parseGav("com.example:artifact:1.0.0");

		assertThat(dependency).isNotNull();
		assertThat(dependency.getVersionSource()).isInstanceOf(VersionSource.DeclaredVersion.class);
	}

	@Test
	void versionSourceIsPropertyForTemplateVersion() {

		GradleParser parser = new GradleParser();
		parser.getPropertyMap().putAll(Map.of("myVersion", "3.2.1"));
		GradleDependency dependency = parser.parseGav("com.example:artifact:${myVersion}");

		assertThat(dependency).isNotNull();
		assertThat(dependency).isNotNull().isInstanceOf(PropertyManagedDependency.class);
		assertThat(dependency.getVersionSource()).isInstanceOf(VersionSource.VersionProperty.class);
		assertThat(dependency.getVersionSource()).isEqualTo(VersionSource.property("myVersion"));
	}

	@Test
	void isValidPluginId_acceptsNormalId() {
		assertThat(BuildFileParserSupport.isValidPluginId("org.springframework.boot")).isTrue();
	}

	@Test
	void isValidPluginId_rejectsPathTraversal() {
		assertThat(BuildFileParserSupport.isValidPluginId("../evil")).isFalse();
	}

	@Test
	void isValidPluginId_rejectsEmpty() {
		assertThat(BuildFileParserSupport.isValidPluginId("")).isFalse();
	}

	@Test
	void isValidPluginId_rejectsUrlSpecial() {
		assertThat(BuildFileParserSupport.isValidPluginId("org@attacker.com/x")).isFalse();
	}

}
