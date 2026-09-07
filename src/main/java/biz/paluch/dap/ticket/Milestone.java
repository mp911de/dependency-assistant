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

package biz.paluch.dap.ticket;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * Repository-owned release milestone. Use instances from the repository when
 * filtering or assigning tickets.
 *
 * @author Mark Paluch
 */
public interface Milestone {

	String getTitle();

	/**
	 * Whether the milestone is open. Tickets may retain closed milestones even
	 * though repository listings contain only open ones.
	 */
	boolean isOpen();

	@Nullable
	String getDescription();

	/**
	 * Return the due or release date, or {@code null} if unscheduled.
	 */
	@Nullable
	LocalDateTime getReleaseDate();

	@Nullable
	default LocalDate getReleaseDay() {
		LocalDateTime releaseDate = getReleaseDate();
		return releaseDate != null ? releaseDate.toLocalDate() : null;
	}

	/**
	 * Whether an open milestone is due before today in the local time zone.
	 */
	default boolean isOverdue() {
		LocalDate releaseDay = getReleaseDay();
		return isOpen() && releaseDay != null && releaseDay.isBefore(LocalDate.now());
	}

}
