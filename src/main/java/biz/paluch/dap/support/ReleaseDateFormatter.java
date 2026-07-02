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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import biz.paluch.dap.util.MessageBundle;
import com.intellij.util.text.DateFormatUtil;
import org.jspecify.annotations.Nullable;

/**
 * Formatter for release dates.
 *
 * @author Mark Paluch
 */
// TODO: review releative steps, like yesterday, 1 year and 4 months or so for better level of detail but do not clutter
public class ReleaseDateFormatter {

	public static final int DETAIL_LIMIT_DAYS = 14;

	private final Instant now = Instant.now();

	private final DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

	private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

	private ReleaseDateFormatter() {
	}

	public static ReleaseDateFormatter create() {
		return new ReleaseDateFormatter();
	}

	/**
	 * Format the given release date as a relative description (e.g. "3 days ago")
	 * when it falls within the last {@link #DETAIL_LIMIT_DAYS} days, and as an
	 * absolute medium-style date otherwise.
	 *
	 * @param releaseDate the release date to format.
	 * @return the formatted release date.
	 */
	public String format(LocalDateTime releaseDate) {

		ZonedDateTime zonedDateTime = releaseDate.atZone(ZoneId.systemDefault());
		Instant released = zonedDateTime.toInstant();

		Duration between = Duration.between(released, now);
		if (between.toDays() > DETAIL_LIMIT_DAYS) {
			return formatter.format(zonedDateTime);
		}

		return DateFormatUtil.formatBetweenDates(released.toEpochMilli(), now.toEpochMilli());
	}

	/**
	 * Format the given release date, combining the relative description with the
	 * absolute medium-style date (e.g. "3 days ago (Jun 25, 2026)") when it falls
	 * within the last {@link #DETAIL_LIMIT_DAYS} days, and using the absolute date
	 * alone otherwise.
	 *
	 * @param releaseDate the release date to format.
	 * @return the formatted release date.
	 */
	public String formatLong(LocalDateTime releaseDate) {

		ZonedDateTime zonedDateTime = releaseDate.atZone(ZoneId.systemDefault());
		Instant released = zonedDateTime.toInstant();

		Duration between = Duration.between(released, now);
		if (between.toDays() > DETAIL_LIMIT_DAYS) {
			return formatter.format(zonedDateTime);
		}

		return DateFormatUtil.formatBetweenDates(released.toEpochMilli(), now.toEpochMilli()) + " ("
				+ formatter.format(zonedDateTime) + ")";
	}

	/**
	 * Format the given release date including the time of day, combining the
	 * relative description with the absolute medium-style date and short-style time
	 * (e.g. "3 days ago (Jun 25, 2026 2:05 PM)") when it falls within the last
	 * {@link #DETAIL_LIMIT_DAYS} days, and using the absolute date-time alone
	 * otherwise. Date and time are composed without the locale's date-time
	 * separator. Intended for documentation surfaces with room for detail.
	 *
	 * @param releaseDate the release date to format.
	 * @return the formatted release date.
	 */
	public String formatDetailed(LocalDateTime releaseDate) {

		ZonedDateTime zonedDateTime = releaseDate.atZone(ZoneId.systemDefault());
		Instant released = zonedDateTime.toInstant();

		Duration between = Duration.between(released, now);
		if (between.toDays() > DETAIL_LIMIT_DAYS) {
			return formatDateTime(zonedDateTime);
		}

		return DateFormatUtil.formatBetweenDates(released.toEpochMilli(), now.toEpochMilli()) + " ("
				+ formatDateTime(zonedDateTime) + ")";
	}

	private String formatDateTime(ZonedDateTime zonedDateTime) {
		return formatter.format(zonedDateTime) + " " + timeFormatter.format(zonedDateTime);
	}

	/**
	 * Format the age between two release dates as a single coarse unit (days,
	 * weeks, months, or years), mirroring the unit ladder of
	 * {@link DateFormatUtil#formatBetweenDates} without the direction suffix.
	 * Values round to the nearest unit so long spans are not understated.
	 *
	 * @param from the first release date.
	 * @param to the second release date.
	 * @return the formatted age (e.g. "2 months"), or {@literal null} when the
	 * dates are less than a day apart.
	 */
	public @Nullable String formatAge(LocalDateTime from, LocalDateTime to) {

		long days = Math.abs(Duration.between(from, to).toDays());
		if (days < 1) {
			return null;
		}

		if (days >= 365) {
			return MessageBundle.message("date.age.years", Math.round(days / 365.0));
		}

		long months = Math.round(days / 30.0);
		if (months >= 12) {
			return MessageBundle.message("date.age.years", 1);
		}
		if (days >= 30) {
			return MessageBundle.message("date.age.months", months);
		}
		if (days >= 7) {
			return MessageBundle.message("date.age.weeks", Math.round(days / 7.0));
		}
		return MessageBundle.message("date.age.days", days);
	}

}
