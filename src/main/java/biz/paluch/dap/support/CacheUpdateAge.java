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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import biz.paluch.dap.MessageBundle;
import com.intellij.util.text.DateFormatUtil;

class CacheUpdateAge {

	public static String agoText(Instant pastInstant, Instant nowInstant, ZoneId zone) {

		LocalDate past = pastInstant.atZone(zone).toLocalDate();
		LocalDate now = nowInstant.atZone(zone).toLocalDate();

		long days = ChronoUnit.DAYS.between(past, now);

		if (days < 0) {
			return DateFormatUtil.formatBetweenDates(pastInstant.toEpochMilli(), nowInstant.toEpochMilli());
		}

		if (days == 0 || days == 1) {
			return DateFormatUtil.formatPrettyDateTime(pastInstant.toEpochMilli());
		}

		if (days < 7) {
			return MessageBundle.message("cache-age.days-ago", days);
		}

		if (days < 14) {
			return MessageBundle.message("cache-age.a-week-ago");
		}

		if (days < 30) {
			return MessageBundle.message("cache-age.days-ago", days);
		}

		long months = ChronoUnit.MONTHS.between(past.withDayOfMonth(1),
				now.withDayOfMonth(1));

		if (months <= 1) {
			return MessageBundle.message("cache-age.a-month-ago", days);
		}

		return MessageBundle.message("cache-age.months-ago", months);
	}

}
