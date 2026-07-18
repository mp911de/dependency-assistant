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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;

import biz.paluch.dap.util.MessageBundle;
import com.intellij.util.text.DateFormatUtil;
import org.jspecify.annotations.Nullable;

/**
 * Formatter for release and due dates, combining relative descriptions with
 * absolute dates.
 *
 * <p>Release dates render as a relative description (e.g. "3 days ago", "in 3
 * days") within one year in either direction and as an absolute medium-style
 * date beyond. Due dates render relative at day granularity within two weeks.
 * Instances capture the current time at creation; create a formatter per
 * rendering pass.
 *
 * @author Mark Paluch
 */
public class ReleaseDateFormatter {

	private static final int RELATIVE_LIMIT_DAYS = 365;

	private static final int DUE_LIMIT_DAYS = 14;

	private final Clock clock;

	private final Instant now;

	private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

	private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

	private ReleaseDateFormatter(Clock clock) {
		this.clock = clock;
		this.now = clock.instant();
	}

	public static ReleaseDateFormatter create() {
		return new ReleaseDateFormatter(Clock.systemDefaultZone());
	}

	/**
	 * Create a formatter using the given clock as time and zone source, primarily
	 * for deterministic tests.
	 */
	public static ReleaseDateFormatter create(Clock clock) {
		return new ReleaseDateFormatter(clock);
	}

	/**
	 * Format the given release date as a relative description (e.g. "3 days ago",
	 * "in 3 days") when it falls within one year in either direction, and as an
	 * absolute medium-style date otherwise.
	 *
	 * @param releaseDate the release date to format.
	 * @return the formatted release date.
	 */
	public String format(LocalDateTime releaseDate) {

		ZonedDateTime zonedDateTime = releaseDate.atZone(clock.getZone());
		Instant released = zonedDateTime.toInstant();

		Duration between = Duration.between(released, now);
		if (between.toDays() > 60) {
			return dateFormatter.format(zonedDateTime);
		}

		return combine(DateFormatUtil.formatBetweenDates(released.toEpochMilli(), now.toEpochMilli()),
				dateFormatter.format(zonedDateTime));
	}

	/**
	 * Format the given release date including the time of day, combining the
	 * relative description with the absolute medium-style date and short-style time
	 * (e.g. "3 days ago (Jun 25, 2026 2:05 PM)") when it falls within one year in
	 * either direction, and using the absolute date-time alone otherwise. Date and
	 * time are composed without the locale's date-time separator. Intended for
	 * documentation surfaces with room for detail.
	 *
	 * @param releaseDate the release date to format.
	 * @return the formatted release date.
	 */
	public String formatDetailed(LocalDateTime releaseDate) {

		ZonedDateTime zonedDateTime = releaseDate.atZone(clock.getZone());
		return combine(formatRelative(zonedDateTime.toInstant()),
				dateFormatter.format(zonedDateTime) + " " + timeFormatter.format(zonedDateTime));
	}

	/**
	 * Format the given due date at day granularity, combining a relative
	 * description that extends the relative ladder into the future ("today",
	 * "tomorrow", "in 6 days", "next week") with the absolute medium-style date
	 * (e.g. "in 6 days (Jul 16, 2026)"); beyond two weeks in either direction the
	 * absolute date stands alone.
	 *
	 * @param dueDate the due date to format.
	 * @return the formatted due date.
	 */
	public String formatDue(LocalDate dueDate) {
		return combine(formatRelativeDay(dueDate), dateFormatter.format(dueDate));
	}

	/**
	 * Format the age between two release dates as a single coarse unit (days,
	 * weeks, or months) below one year, and as years with a month remainder beyond
	 * (e.g. "1 year and 4 months"). Values round to the nearest unit so long spans
	 * are not understated.
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
			return formatYears(days);
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

	private static String formatYears(long days) {

		long years = days / 365;
		long months = Math.round((days - years * 365) / 30.0);
		if (months >= 12) {
			years++;
			months = 0;
		}

		return months == 0 ? MessageBundle.message("date.age.years", years)
				: MessageBundle.message("date.age.years-and-months", years, months);
	}

	/**
	 * Relative description of the given instant, or {@literal null} when it falls
	 * outside the one-year relative window.
	 */
	private @Nullable String formatRelative(Instant released) {

		if (Math.abs(Duration.between(released, now).toDays()) >= RELATIVE_LIMIT_DAYS) {
			return null;
		}

		return DateFormatUtil.formatBetweenDates(released.toEpochMilli(), now.toEpochMilli());
	}

	/**
	 * Relative day description ("today", "tomorrow", "in 6 days", "next week"), or
	 * {@literal null} when the date falls outside the two-week window.
	 */
	private @Nullable String formatRelativeDay(LocalDate dueDate) {

		long days = ChronoUnit.DAYS.between(LocalDate.ofInstant(now, clock.getZone()), dueDate);
		if (Math.abs(days) > DUE_LIMIT_DAYS) {
			return null;
		}

		if (days == 0) {
			return MessageBundle.message("date.relative.today");
		}
		if (days == 1) {
			return MessageBundle.message("date.relative.tomorrow");
		}
		if (days == -1) {
			return MessageBundle.message("date.relative.yesterday");
		}
		if (days >= 7) {
			return MessageBundle.message("date.relative.next-week");
		}
		if (days <= -7) {
			return MessageBundle.message("date.relative.last-week");
		}

		return days > 0 ? MessageBundle.message("date.relative.in-days", days)
				: MessageBundle.message("date.relative.days-ago", -days);
	}

	private static String combine(@Nullable String relative, String absolute) {
		return relative != null ? relative + " (" + absolute + ")" : absolute;
	}

}
