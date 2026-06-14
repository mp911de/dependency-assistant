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

import biz.paluch.dap.artifact.ArtifactId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link GradleArtifactId}.
 *
 * @author Mark Paluch
 */
class GradleArtifactIdUnitTests {

	@Test
	void parsesGroupAndArtifact() {

		GradleArtifactId artifactId = GradleArtifactId.from("com.example:lib");

		assertThat((ArtifactId) artifactId).isEqualTo(ArtifactId.of("com.example", "lib"));
		assertThat(artifactId.version()).isEmpty();
	}

	@Test
	void parsesGroupArtifactAndVersion() {

		GradleArtifactId artifactId = GradleArtifactId.from("com.example:lib:1.0.0");

		assertThat((ArtifactId) artifactId).isEqualTo(ArtifactId.of("com.example", "lib"));
		assertThat(artifactId.version()).isEqualTo("1.0.0");
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"com.example:", ":lib", "com.example", ":", "  "})
	void rejectsIncompleteCoordinate(String gav) {
		assertThatIllegalArgumentException().isThrownBy(() -> GradleArtifactId.from(gav));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"com.example:lib", "com.example:lib:1.0.0"})
	void recognizesCompleteCoordinatesAsValid(String gav) {
		assertThat(GradleArtifactId.isValid(gav)).isTrue();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"com.example:", ":lib", "com.example", ":", "  "})
	void recognizesIncompleteCoordinatesAsInvalid(String gav) {
		assertThat(GradleArtifactId.isValid(gav)).isFalse();
	}

	@Test
	void treatsBlankCoordinateAsInvalid() {

		assertThat(GradleArtifactId.isValid(null)).isFalse();
		assertThat(GradleArtifactId.isValid("")).isFalse();
	}

	@Test
	void guardingWithIsValidAvoidsParsingExceptions() {

		for (String gav : new String[] {"com.example:", ":lib", "com.example"}) {
			if (GradleArtifactId.isValid(gav)) {
				GradleArtifactId.from(gav);
			}
		}
	}

}
