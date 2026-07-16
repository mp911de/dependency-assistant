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

package biz.paluch.dap.assistant.completion;

import biz.paluch.dap.artifact.ArtifactVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VersionStem}.
 *
 * @author Mark Paluch
 */
class VersionStemUnitTests {

	@Test
	void stripsTrailingSeparators() {

		assertThat(VersionStem.from("3.")).hasToString("3");
		assertThat(VersionStem.from("2.0.")).hasToString("2.0");
		assertThat(VersionStem.from("3.0-")).hasToString("3.0");
	}

	@Test
	void trailingHyphenSignalsSuffixIntent() {

		assertThat(VersionStem.from("3.0-").isSuffixIntent()).isTrue();
		assertThat(VersionStem.from("3.0.").isSuffixIntent()).isFalse();
		assertThat(VersionStem.from("3.0").isSuffixIntent()).isFalse();
	}

	@Test
	void rejectsUnusablePrefixes() {

		assertThat(VersionStem.from(null)).isNull();
		assertThat(VersionStem.from("")).isNull();
		assertThat(VersionStem.from("-")).isNull();
		assertThat(VersionStem.from("...")).isNull();
	}

	@Test
	void numericStemsBindAtComponentBoundaries() {

		assertThat(matches("3", "3.5.12")).isTrue();
		assertThat(matches("3", "3.0-M1")).isTrue();
		assertThat(matches("3", "30.1")).isFalse();
		assertThat(matches("2.7.", "2.7.12")).isTrue();
		assertThat(matches("2.7.", "2.71.0")).isFalse();
		assertThat(matches("5.0.9", "5.0.9")).isTrue();
	}

	@Test
	void prefixedNumericStemsBindAtComponentBoundaries() {

		assertThat(matches("v4", "v4.2.1")).isTrue();
		assertThat(matches("v4", "v40.1")).isFalse();
	}

	@Test
	void numericStemsMatchTheUnwrappedForm() {

		assertThat(matches("4.1", "v4.1.0")).isTrue();
		assertThat(matches("4.1", "v4.10.0")).isFalse();
	}

	@Test
	void nameStemsMatchByCaseInsensitivePrefix() {

		assertThat(matches("Hox", "Hoxton.SR12")).isTrue();
		assertThat(matches("hox", "Hoxton.SR12")).isTrue();
		assertThat(matches("Hox", "Greenwich.SR6")).isFalse();
	}

	private static boolean matches(String prefix, String version) {

		VersionStem stem = VersionStem.from(prefix);
		assertThat(stem).isNotNull();
		return stem.matches(ArtifactVersion.of(version));
	}

}
