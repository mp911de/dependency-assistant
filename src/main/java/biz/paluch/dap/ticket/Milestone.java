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

package biz.paluch.dap.ticket;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * Release descriptor attached to tickets in a repository.
 *
 * <p>For GitHub and GitLab this maps to a milestone. Systems may expose a
 * release or fix-version concept through the same interface. Instances are
 * implementation-owned and obtained through
 * {@link TicketRepository#getMilestones(com.intellij.openapi.progress.ProgressIndicator)}
 * or {@link Ticket#getMilestones()}. They may be displayed or passed back to
 * the same repository for filtering and assignment.
 *
 * @author Mark Paluch
 * @see TicketRepository#getMilestones(com.intellij.openapi.progress.ProgressIndicator)
 */
public interface Milestone {

	/**
	 * Return the milestone title shown by the ticket system.
	 *
	 * @return the milestone title.
	 */
	String getTitle();

	/**
	 * Return whether the milestone is open.
	 *
	 * <p>{@link TicketRepository#getMilestones(com.intellij.openapi.progress.ProgressIndicator)}
	 * lists open milestones only; milestones attached to found tickets can be
	 * closed.
	 *
	 * @return {@literal true} if the milestone is open; {@literal false} otherwise.
	 */
	boolean isOpen();

	/**
	 * Return the milestone description.
	 *
	 * @return the milestone description, or {@literal null} if none is available.
	 */
	@Nullable
	String getDescription();

	/**
	 * Return the due or release date of this milestone.
	 *
	 * @return the due or release date, or {@literal null} if none is set.
	 */
	@Nullable
	LocalDateTime getReleaseDate();

	/**
	 * Return the day of the {@link #getReleaseDate() release date}, the resolution
	 * milestone scheduling is presented and ordered at.
	 * @return the release day, or {@literal null} if the milestone is unscheduled.
	 */
	@Nullable
	default LocalDate getReleaseDay() {

		LocalDateTime releaseDate = getReleaseDate();
		return releaseDate != null ? releaseDate.toLocalDate() : null;
	}

	/**
	 * Return whether the milestone {@link #isOpen()} and its
	 * {@link #getReleaseDay() release day} lies in the past.
	 *
	 * @return {@literal true} if the milestone is open and overdue;
	 * {@literal false} otherwise, including for an unscheduled milestone.
	 */
	default boolean isOverdue() {

		LocalDate releaseDay = getReleaseDay();
		return isOpen() && releaseDay != null && releaseDay.isBefore(LocalDate.now());
	}

}
