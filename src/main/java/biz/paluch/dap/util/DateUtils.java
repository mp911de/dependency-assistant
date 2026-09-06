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
	 * Parse an ISO date or date-time. Date-only values use midnight. A supplied
	 * offset is discarded without adjusting the local time.
	 *
	 * @throws IllegalArgumentException if the value is absent or blank.
	 * @throws DateTimeParseException if the date cannot be parsed.
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
