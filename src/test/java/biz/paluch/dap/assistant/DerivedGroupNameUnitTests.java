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

package biz.paluch.dap.assistant;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DerivedGroupName}.
 *
 * @author Mark Paluch
 */
class DerivedGroupNameUnitTests {

	@ParameterizedTest(name = "{0}")
	@MethodSource("derivableNames")
	void derivesGroupName(String description, String groupId, List<String> artifactIds, String expected) {

		DerivedGroupName name = DerivedGroupName.of(groupId, artifactIds);

		assertThat(name).isNotNull();
		assertThat(name.getDisplayName()).isEqualTo(expected);
	}

	static Stream<Arguments> derivableNames() {
		return Stream.of(
				Arguments.of("common prefix as name", "org.springframework.data",
						List.of("spring-data-commons", "spring-data-jpa"), "spring-data"),
				Arguments.of("base member as prefix", "org.junit.jupiter",
						List.of("junit-jupiter", "junit-jupiter-api", "junit-jupiter-engine", "junit-jupiter-params"),
						"junit-jupiter"),
				Arguments.of("promote short acronym to groupId segment", "org.bouncycastle",
						List.of("bc-fips", "bc-rng-jent"), "bouncycastle"),
				Arguments.of("keep full-word prefix without promotion", "com.fasterxml.jackson.core",
						List.of("jackson-core", "jackson-databind"), "jackson"),
				Arguments.of("keep multi-token prefix without expansion", "de.kaiserpfalz-edv.commons",
						List.of("kp-commons-bom", "kp-commons-core"), "kp-commons"),
				Arguments.of("promote to non-last groupId segment", "de.kaiserpfalz-edv.commons",
						List.of("kp-checkstyle", "kp-commons-api", "kp-spring-boot-parent"), "kaiserpfalz-edv"),
				Arguments.of("do not promote to segment with digits", "com.ape9527", List.of("ape-auth", "ape-core"),
						"ape"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("ungroupableCoordinates")
	void returnsNullWhenUngroupable(String description, String groupId, List<String> artifactIds) {
		assertThat(DerivedGroupName.of(groupId, artifactIds)).isNull();
	}

	static Stream<Arguments> ungroupableCoordinates() {
		return Stream.of(
				Arguments.of("no common prefix", "activesoap", List.of("jaxb-api", "activesoap")),
				Arguments.of("prefix shorter than two characters", "com.example", List.of("a-foo", "a-bar")));
	}

}
