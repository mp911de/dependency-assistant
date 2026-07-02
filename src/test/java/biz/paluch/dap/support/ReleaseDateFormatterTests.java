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

package biz.paluch.dap.support;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ReleaseDateFormatter}.
 *
 * @author Mark Paluch
 */
class ReleaseDateFormatterTests {

	@Test
	void formatsAgeAsSingleCoarseUnit() {

		assertThat(age(3)).isEqualTo("3 days");
		assertThat(age(8)).isEqualTo("1 week");
		assertThat(age(40)).isEqualTo("1 month");
		assertThat(age(81)).isEqualTo("3 months");
	}

	@Test
	void roundsAgeToNearestUnit() {

		assertThat(age(45)).isEqualTo("2 months");
		assertThat(age(350)).isEqualTo("1 year");
		assertThat(age(700)).isEqualTo("2 years");
	}

	@Test
	void omitsAgeBelowOneDay() {
		assertThat(age(0)).isNull();
	}

	@Test
	void formatsDetailedDateWithTimeOfDay() {

		String detailed = ReleaseDateFormatter.create().formatDetailed(LocalDateTime.of(2026, 2, 15, 14, 30));

		// Medium date plus short time; the hour-minute separator marks the time
		// part, joined without the locale's date-time comma.
		assertThat(detailed).contains(":").doesNotContain("2026,");
	}

	@Test
	void formatsAgeDirectionAgnostic() {
		assertThat(age(-40)).isEqualTo("1 month");
	}

	private static @Nullable String age(int days) {

		LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
		return ReleaseDateFormatter.create().formatAge(from, from.plusDays(days));
	}

}
