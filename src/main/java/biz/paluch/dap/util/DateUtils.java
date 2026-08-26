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

package biz.paluch.dap.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.springframework.util.Assert;

/**
 * Utility methods for parsing dates.
 *
 * @author Mark Paluch
 */
public class DateUtils {

	/**
	 * Parse a date string into a {@link LocalDateTime}.
	 * <p>The string can be an ISO_OFFSET_DATE_TIME string such as
	 * {@code 2007-12-03T10:15:30+01:00},a local DateTime string such as
	 * {@code 2007-12-03T10:15:30} or simply a date string such as
	 * {@code 2007-12-03}.
	 * @param date the date string to parse.
	 * @return the parsed {@link LocalDateTime}.
	 */
	public static LocalDateTime parse(String date) {
		Assert.hasText(date, "Date must not be empty");
		try {
			return OffsetDateTime.parse(date).toLocalDateTime();
		} catch (DateTimeParseException ignored) {
			try {
				return LocalDateTime.parse(date);
			} catch (DateTimeParseException e) {
				return LocalDateTime.of(LocalDate.parse(date), LocalTime.MIDNIGHT);
			}
		}
	}

}
