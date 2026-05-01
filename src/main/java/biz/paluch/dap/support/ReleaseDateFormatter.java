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

import com.intellij.util.text.DateFormatUtil;

/**
 * Formatter for release dates.
 * 
 * @author Mark Paluch
 */
public class ReleaseDateFormatter {

	private final Instant now = Instant.now();

	private final DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

	private ReleaseDateFormatter() {
	}

	public static ReleaseDateFormatter create() {
		return new ReleaseDateFormatter();
	}

	/**
	 * Format the given release date.
	 */
	public String format(LocalDateTime releaseDate) {

		ZonedDateTime zonedDateTime = releaseDate.atZone(ZoneId.systemDefault());
		Instant released = zonedDateTime.toInstant();

		Duration between = Duration.between(released, now);
		if (between.toDays() > 8) {
			return formatter.format(zonedDateTime);
		}

		return DateFormatUtil.formatBetweenDates(released.toEpochMilli(), now.toEpochMilli());
	}

	/**
	 * Format the given release date.
	 */
	public String formatLong(LocalDateTime releaseDate) {

		ZonedDateTime zonedDateTime = releaseDate.atZone(ZoneId.systemDefault());
		Instant released = zonedDateTime.toInstant();

		Duration between = Duration.between(released, now);
		if (between.toDays() > 8) {
			return formatter.format(zonedDateTime);
		}

		return DateFormatUtil.formatBetweenDates(released.toEpochMilli(), now.toEpochMilli()) + " ("
				+ formatter.format(zonedDateTime) + ")";
	}

}
