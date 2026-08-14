/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.checker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CvssSeverity}.
 *
 * @author Mark Paluch
 */
class CvssSeverityUnitTests {

	@ParameterizedTest
	@CsvSource(textBlock = """
			 9.0, CRITICAL
			10.0, CRITICAL
			 8.9, HIGH
			 7.0, HIGH
			 6.9, MEDIUM
			 4.0, MEDIUM
			 3.9, LOW
			 0.1, LOW
			 0.0, NONE
			-0.1, UNKNOWN
			""")
	void mapsScoreBoundaries(double score, CvssSeverity expected) {
		assertThat(CvssSeverity.fromScore(score)).isEqualTo(expected);
	}

	@Test
	void mapsNonPositiveScoreToUnknown() {
		assertThat(CvssSeverity.fromScore(-1.0)).isEqualTo(CvssSeverity.UNKNOWN);
	}

	@Test
	void mapsLabelCaseInsensitively() {

		assertThat(CvssSeverity.fromLabel("critical")).isEqualTo(CvssSeverity.CRITICAL);
		assertThat(CvssSeverity.fromLabel("HIGH")).isEqualTo(CvssSeverity.HIGH);
		assertThat(CvssSeverity.fromLabel("  Medium ")).isEqualTo(CvssSeverity.MEDIUM);
	}

	@Test
	void mapsUnrecognizedLabelToUnknown() {

		assertThat(CvssSeverity.fromLabel("severe")).isEqualTo(CvssSeverity.UNKNOWN);
		assertThat(CvssSeverity.fromLabel("")).isEqualTo(CvssSeverity.UNKNOWN);
		assertThat(CvssSeverity.fromLabel(null)).isEqualTo(CvssSeverity.UNKNOWN);
	}

	@ParameterizedTest
	@CsvSource(textBlock = """
			CRITICAL, 5
			HIGH, 4
			MEDIUM, 3
			LOW, 2
			NONE, 1
			UNKNOWN, 0
			""")
	void exposesSeverityRank(CvssSeverity severity, int rank) {
		assertThat(severity.rank()).isEqualTo(rank);
	}

}
