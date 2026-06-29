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

package biz.paluch.dap.artifact;

import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ArtifactId}.
 *
 * @author Mark Paluch
 */
class ArtifactIdUnitTests {

	@Test
	void treatsArtifactIdAsCaseSensitiveIdentity() {

		ArtifactId upper = ArtifactId.of("group", "Foo");
		ArtifactId lower = ArtifactId.of("group", "foo");

		assertThat(upper).isNotEqualTo(lower);
		assertThat(upper.compareTo(lower)).isNotZero();
	}

	@Test
	void naturalOrderingAgreesWithEquals() {

		ArtifactId upper = ArtifactId.of("group", "Foo");
		ArtifactId lower = ArtifactId.of("group", "foo");

		TreeMap<ArtifactId, String> sorted = new TreeMap<>();
		sorted.put(upper, "upper");
		sorted.put(lower, "lower");

		assertThat(sorted).hasSize(2);
	}

	@Test
	void equalsMatchesAcrossArtifactIdImplementations() {

		ArtifactId declared = ArtifactId.of("group", "artifact");
		ArtifactId git = GitArtifactId.of("github.com", "group", "artifact");

		assertThat(declared).isEqualTo(git);
		assertThat(declared).hasSameHashCodeAs(git);
	}

}
