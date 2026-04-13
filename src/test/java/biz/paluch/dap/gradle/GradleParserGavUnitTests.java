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

import java.util.LinkedHashMap;
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

		GradleParser parser = new GradleParser(Map.of());
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
		GradleParser parser = new GradleParser(props);
		GradleDependency dependency = parser.parseGav("org.springframework:spring-core:${springVersion}");

		assertThat(dependency).isNotNull();
		assertThat(dependency.getId().groupId()).isEqualTo("org.springframework");
		assertThat(dependency.getId().artifactId()).isEqualTo("spring-core");
		assertThat(dependency.getVersionSource()).isInstanceOf(VersionSource.VersionProperty.class);
		assertThat(dependency.getVersionSource()).isEqualTo(VersionSource.property("springVersion"));
	}

	@Test
	void returnsNullForGavWithUnresolvablePropertyVersion() {

		GradleParser parser = new GradleParser(Map.of());
		GradleDependency dependency = parser.parseGav("org.springframework:spring-core:${springVersion}");

		assertThat(dependency).isNotNull().isInstanceOf(PropertyManagedDependency.class);
		assertThat(dependency.getId().groupId()).isEqualTo("org.springframework");
		assertThat(dependency.getId().artifactId()).isEqualTo("spring-core");
		assertThat(dependency.getVersionSource()).isInstanceOf(VersionSource.VersionProperty.class);
		assertThat(dependency.getVersionSource()).isEqualTo(VersionSource.property("springVersion"));
	}

	@Test
	void returnsNullForNullInput() {

		GradleParser parser = new GradleParser(Map.of());

		assertThat(parser.parseGav(null)).isNull();
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({ "com.example:artifact:1.0.0, com.example, artifact, 1.0.0",
			"org.example.group:my-artifact:2.3.4, org.example.group, my-artifact, 2.3.4" })
	void parsesVariousCoordinates(String gav, String group, String artifact, String version) {

		GradleParser parser = new GradleParser(Map.of());
		GradleDependency parsed = parser.parseGav(gav);

		assertThat(parsed).isNotNull();
		assertThat(parsed.getId().groupId()).isEqualTo(group);
		assertThat(parsed.getId().artifactId()).isEqualTo(artifact);
	}

	@Test
	void versionSourceIsDeclaredForLiteralVersion() {

		GradleParser parser = new GradleParser(Map.of());
		GradleDependency dependency = parser.parseGav("com.example:artifact:1.0.0");

		assertThat(dependency).isNotNull();
		assertThat(dependency.getVersionSource()).isInstanceOf(VersionSource.DeclaredVersion.class);
	}

	@Test
	void versionSourceIsPropertyForTemplateVersion() {

		GradleParser parser = new GradleParser(Map.of("myVersion", "3.2.1"));
		GradleDependency dependency = parser.parseGav("com.example:artifact:${myVersion}");

		assertThat(dependency).isNotNull();
		assertThat(dependency).isNotNull().isInstanceOf(PropertyManagedDependency.class);
		assertThat(dependency.getVersionSource()).isInstanceOf(VersionSource.VersionProperty.class);
		assertThat(dependency.getVersionSource()).isEqualTo(VersionSource.property("myVersion"));
	}

	@Test
	void resolveInterpolated_singlePlaceholder() {
		assertThat(
				BuildFileParserSupport.resolveInterpolated("${a}", GradlePropertyResolver.wrap(Map.of("a", "org.foo"))))
						.isEqualTo("org.foo");
	}

	@Test
	void resolveInterpolated_mixedString() {
		assertThat(
				BuildFileParserSupport.resolveInterpolated("com.${a}", GradlePropertyResolver.wrap(Map.of("a", "foo"))))
						.isEqualTo("com.foo");
	}

	@Test
	void resolveInterpolated_unbracedPlaceholder() {
		assertThat(
				BuildFileParserSupport.resolveInterpolated("$a", GradlePropertyResolver.wrap(Map.of("a", "org.foo"))))
						.isEqualTo("org.foo");
	}

	@Test
	void resolveInterpolated_mixedBracedAndUnbraced() {
		assertThat(BuildFileParserSupport.resolveInterpolated("$a.${b}",
				GradlePropertyResolver.wrap(Map.of("a", "com", "b", "foo")))).isEqualTo("com.foo");
	}

	@Test
	void resolveInterpolated_unknownPlaceholderLeftInPlace() {
		assertThat(BuildFileParserSupport.resolveInterpolated("${missing}", GradlePropertyResolver.wrap(Map.of())))
				.isEqualTo("${missing}");
	}

	@Test
	void resolveInterpolated_unbracedUnknownLeftInPlace() {
		assertThat(BuildFileParserSupport.resolveInterpolated("$missing", GradlePropertyResolver.wrap(Map.of())))
				.isEqualTo("$missing");
	}

	@Test
	void resolveInterpolated_emptyValueLeftAsEmpty() {
		assertThat(BuildFileParserSupport.resolveInterpolated("${a}", GradlePropertyResolver.wrap(Map.of("a", ""))))
				.isEmpty();
	}

	@Test
	void resolveChained_twoHops() {
		Map<String, String> props = Map.of("a", "${b}", "b", "org.foo");
		assertThat(BuildFileParserSupport.resolveChained("${a}", GradlePropertyResolver.wrap(props)))
				.isEqualTo("org.foo");
	}

	@Test
	void resolveChained_threeHops() {
		Map<String, String> props = Map.of("a", "${b}", "b", "${c}", "c", "org.foo");
		assertThat(BuildFileParserSupport.resolveChained("${a}", GradlePropertyResolver.wrap(props)))
				.isEqualTo("org.foo");
	}

	@Test
	void resolveChained_cycle() {
		Map<String, String> props = Map.of("a", "${b}", "b", "${a}");
		String result = BuildFileParserSupport.resolveChained("${a}", GradlePropertyResolver.wrap(props));
		assertThat(BuildFileParserSupport.hasUnresolvedPlaceholder(result)).isTrue();
	}

	@Test
	void resolveChained_missingSecondHop() {
		Map<String, String> props = Map.of("a", "${b}");
		assertThat(BuildFileParserSupport.resolveChained("${a}", GradlePropertyResolver.wrap(props))).isEqualTo("${b}");
	}

	@Test
	void resolveChained_depthCapReached() {
		Map<String, String> props = new LinkedHashMap<>();
		props.put("p12", "org.foo");
		for (int i = 11; i >= 1; i--) {
			props.put("p" + i, "${p" + (i + 1) + "}");
		}
		String result = BuildFileParserSupport.resolveChained("${p1}", GradlePropertyResolver.wrap(props));
		assertThat(BuildFileParserSupport.hasUnresolvedPlaceholder(result)).isTrue();
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
