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
 * Localized release and due-date presentation. The current instant is captured
 * at creation, so create a formatter for each rendering pass.
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

	/**
	 * Use the system clock and default time zone.
	 */
	public static ReleaseDateFormatter create() {
		return new ReleaseDateFormatter(Clock.systemDefaultZone());
	}

	/**
	 * Use the clock for the reference instant and time zone.
	 */
	public static ReleaseDateFormatter create(Clock clock) {
		return new ReleaseDateFormatter(clock);
	}

	/**
	 * Format a compact release date, adding relative text for recent or future
	 * dates.
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
	 * Format a release date with time of day and relative text for nearby dates.
	 */
	public String formatDetailed(LocalDateTime releaseDate) {

		ZonedDateTime zonedDateTime = releaseDate.atZone(clock.getZone());
		return combine(formatRelative(zonedDateTime.toInstant()),
				dateFormatter.format(zonedDateTime) + " " + timeFormatter.format(zonedDateTime));
	}

	/**
	 * Format a due date at day granularity, adding relative text for nearby dates.
	 */
	public String formatDue(LocalDate dueDate) {
		return combine(formatRelativeDay(dueDate), dateFormatter.format(dueDate));
	}

	/**
	 * Format the absolute age between releases in coarse units, such as "2 months".
	 *
	 * @return {@code null} if the dates are less than a day apart.
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

	private @Nullable String formatRelative(Instant released) {

		if (Math.abs(Duration.between(released, now).toDays()) >= RELATIVE_LIMIT_DAYS) {
			return null;
		}

		return DateFormatUtil.formatBetweenDates(released.toEpochMilli(), now.toEpochMilli());
	}

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
