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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ArtifactPattern}.
 *
 * @author Mark Paluch
 */
class ArtifactPatternUnitTests {

	@Test
	void matchesArtifactIdOnly() {

		ArtifactPattern pattern = ArtifactPattern.of("spring-security");

		assertThat(pattern.test(ArtifactId.of("org.springframework.security", "spring-security"))).isTrue();
		assertThat(pattern.test(ArtifactId.of("org.springframework", "spring-core"))).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {"org.springframework:spring-core", "org.springframework/spring-core"})
	void matchesGroupAndArtifact(String source) {

		ArtifactPattern pattern = ArtifactPattern.of(source);

		assertThat(pattern.test(ArtifactId.of("org.springframework", "spring-core"))).isTrue();
		assertThat(pattern.test(ArtifactId.of("org.springframework.boot", "spring-core"))).isFalse();
		assertThat(pattern.test(ArtifactId.of("org.springframework", "spring-context"))).isFalse();
	}

	@Test
	void matchesWildcards() {

		ArtifactPattern groupPattern = ArtifactPattern.of("org.springframework:*");
		ArtifactPattern artifactPattern = ArtifactPattern.of("spring-*");
		ArtifactId springCore = ArtifactId.of("org.springframework", "spring-core");

		assertThat(groupPattern.test(springCore)).isTrue();
		assertThat(groupPattern.test(ArtifactId.of("org.springframework.boot", "spring-boot"))).isFalse();
		assertThat(artifactPattern.test(springCore)).isTrue();
		assertThat(artifactPattern.test(ArtifactId.of("org.junit", "junit-bom"))).isFalse();
	}

	@Test
	void ordersPatternsBySpecificity() {

		assertThat(ArtifactPattern.of("org.springframework:spring-core")
				.compareTo(ArtifactPattern.of("spring-core"))).isPositive();
		assertThat(ArtifactPattern.of("spring-core")
				.compareTo(ArtifactPattern.of("org.springframework:*"))).isPositive();
		assertThat(ArtifactPattern.of("org.springframework:*").compareTo(ArtifactPattern.of("*"))).isPositive();
	}

}
