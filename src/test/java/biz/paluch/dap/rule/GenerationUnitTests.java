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

import biz.paluch.dap.artifact.ArtifactVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Generation}.
 *
 * @author Mark Paluch
 */
class GenerationUnitTests {

	@Test
	void matchesMajorLine() {

		Generation generation = Generation.of("6");

		assertThat(generation).accepts("6.1.0");
		assertThat(generation).rejects("7.0.0");
	}

	@ParameterizedTest
	@ValueSource(strings = {"6.0", "6.0.x"})
	void matchesMinorLine(String source) {

		Generation generation = Generation.of(source);

		assertThat(generation).accepts("6.0.13");
		assertThat(generation).rejects("6.1.0");
	}

	@Test
	void matchesExactBaseVersion() {

		Generation generation = Generation.of("6.0.1");

		assertThat(generation).accepts("6.0.1");
		assertThat(generation).rejects("6.0.2");
	}

	@Test
	void testsInnerMostVersionByPrefix() {

		Generation generation = Generation.of("1.2");

		assertThat(generation.asVersionPredicate()).accepts(ArtifactVersion.of("v1.2.3"));
		assertThat(generation.asVersionPredicate()).rejects(ArtifactVersion.of("v1.20.0"));
	}

	@Test
	void rejectsAdjacentPrefixLine() {

		Generation generation = Generation.of("7.1");

		assertThat(generation).rejects("7.11.0");
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "x", "*", "6.x.0", "6.0.RELEASE", "6-0", "v6.0", "6.0.beta"})
	void rejectsNonNumericGeneration(String source) {

		assertThatIllegalArgumentException().isThrownBy(() -> Generation.of(source))
				.withMessageContaining(source);
	}

}
