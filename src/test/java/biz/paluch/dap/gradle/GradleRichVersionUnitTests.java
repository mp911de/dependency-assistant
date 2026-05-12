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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link GradleRichVersion}.
 *
 * @author Mark Paluch
 */
class GradleRichVersionUnitTests {

	@Test
	void ignoresEmptyVersion() {

		assertThat(GradleRichVersion.parse(null)).isEmpty();
		assertThat(GradleRichVersion.parse("")).isEmpty();
		assertThat(GradleRichVersion.parse("  ")).isEmpty();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"[", "(", "]", "[1", "[1,", "[1,2", "[1.0]", "[]", "[,]", "(,)",
			"1.0,2.0", "1.0,2.0]", "[1.0,2.0", "[1.0,2.0,3.0]", "[alpha,2.0]",
			"[1.0,alpha]"})
	void rejectsIncompleteOrInvalidRanges(String raw) {

		assertThat(GradleRichVersion.parse(raw)).isEmpty();
	}

	@Test
	void leavesInvalidRangesUnchangedWhenUpdating() {

		assertThat(GradleRichVersion.update("[", "6.0.3")).isEqualTo("[");
		assertThat(GradleRichVersion.update("1.0,2.0]", "6.0.3")).isEqualTo("1.0,2.0]");
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"!!6.0.0", "[!!6.0.0", "6.0.0!!["})
	void rejectsInvalidEnforcedDeclarations(String raw) {

		assertThat(GradleRichVersion.parse(raw)).isEmpty();
	}

	@Test
	void derivesAnchorFromValidRange() {

		assertThat(GradleRichVersion.parse("[5.0, 7.0["))
				.hasValueSatisfying(version -> assertThat(version).hasToString("5.0"));
		assertThat(GradleRichVersion.parse("(5.0, 6.0.0]"))
				.hasValueSatisfying(version -> assertThat(version).hasToString("6.0.0"));
		assertThat(GradleRichVersion.parse("[6.0.0,)"))
				.hasValueSatisfying(version -> assertThat(version).hasToString("6.0.0"));
	}

	@Test
	void derivesPreferredVersionFromEnforcedRange() {

		assertThat(GradleRichVersion.parse("[5.0, 7.0[!!6.0.0"))
				.hasValueSatisfying(version -> assertThat(version).hasToString("6.0.0"));
		assertThat(GradleRichVersion.parse("6.0.0!!"))
				.hasValueSatisfying(version -> assertThat(version).hasToString("6.0.0"));
		assertThat(GradleRichVersion.parse("(5.0, 7.0)!!6.0.0"))
				.hasValueSatisfying(version -> assertThat(version).hasToString("6.0.0"));
	}

}
