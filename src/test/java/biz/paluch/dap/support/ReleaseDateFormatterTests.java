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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ReleaseDateFormatter}.
 *
 * @author Mark Paluch
 */
class ReleaseDateFormatterTests {

	Clock CLOCK = Clock.fixed(Instant.parse("2026-07-16T12:00:00Z"), ZoneOffset.UTC);

	ReleaseDateFormatter formatter = ReleaseDateFormatter.create(CLOCK);

	LocalDateTime NOW = LocalDateTime.now(CLOCK);

	LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);

	@Test
	void formatsRelativeWithinOneYearInBothDirections() {

		LocalDateTime releaseDate1 = NOW.minusDays(3);
		assertThat(formatter.format(releaseDate1)).isEqualTo("3 days ago (13.07.2026)");
		LocalDateTime releaseDate = NOW.plusDays(3);
		assertThat(formatter.format(releaseDate)).isEqualTo("in 3 days (19.07.2026)");
	}

	@Test
	void formatsDetailedDateWithTimeOfDay() {

		String detailed = formatter.formatDetailed(LocalDateTime.of(2025, 2, 15, 14, 30));

		// Medium date plus short time; the hour-minute separator marks the time
		// part, joined without the locale's date-time comma.
		assertThat(detailed).contains(":").doesNotContain("2025,");
	}

	@Test
	void formatsAgeAsSingleCoarseUnit() {

		assertThat(formatter.formatAge(from, from.plusDays(3))).isEqualTo("3 days");
		assertThat(formatter.formatAge(from, from.plusDays(8))).isEqualTo("1 week");
		assertThat(formatter.formatAge(from, from.plusDays(40))).isEqualTo("1 month");
		assertThat(formatter.formatAge(from, from.plusDays(81))).isEqualTo("3 months");
	}

	@Test
	void roundsAgeToNearestUnit() {

		assertThat(formatter.formatAge(from, from.plusDays(45))).isEqualTo("2 months");
		assertThat(formatter.formatAge(from, from.plusDays(350))).isEqualTo("1 year");
	}

	@Test
	void addsMonthDetailInYearRange() {

		assertThat(formatter.formatAge(from, from.plusDays(365))).isEqualTo("1 year");
		assertThat(formatter.formatAge(from, from.plusDays(400))).isEqualTo("1 year and 1 month");
		assertThat(formatter.formatAge(from, from.plusDays(500))).isEqualTo("1 year and 5 months");
		assertThat(formatter.formatAge(from, from.plusDays(700))).isEqualTo("1 year and 11 months");
		assertThat(formatter.formatAge(from, from.plusDays(720))).isEqualTo("2 years");
		assertThat(formatter.formatAge(from, from.plusDays(730))).isEqualTo("2 years");
	}

	@Test
	void omitsAgeBelowOneDay() {
		assertThat(formatter.formatAge(from, from.plusDays(0))).isNull();
	}

	@Test
	void formatsAgeDirectionAgnostic() {
		assertThat(formatter.formatAge(from, from.plusDays(-40))).isEqualTo("1 month");
	}

	@Test
	void formatsDueDatesRelativeInBothDirections() {

		assertThat(due(0)).startsWith("today (");
		assertThat(due(1)).startsWith("tomorrow (");
		assertThat(due(-1)).startsWith("yesterday (");
		assertThat(due(6)).startsWith("in 6 days (");
		assertThat(due(-3)).startsWith("3 days ago (");
		assertThat(due(8)).startsWith("next week (");
		assertThat(due(-10)).startsWith("last week (");
	}

	@Test
	void formatsFarDueDatesAbsoluteOnly() {

		assertThat(due(30)).doesNotContain("(");
		assertThat(due(-30)).doesNotContain("(");
	}

	private String due(int daysFromToday) {
		return formatter.formatDue(LocalDate.now(CLOCK).plusDays(daysFromToday));
	}

}
