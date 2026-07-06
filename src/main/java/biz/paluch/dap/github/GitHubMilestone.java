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

package biz.paluch.dap.github;

import java.time.LocalDateTime;

import biz.paluch.dap.ticket.Milestone;
import org.jspecify.annotations.Nullable;

/**
 * GitHub {@link Milestone} carrying the repository-scoped milestone number used
 * for issue assignment and query filtering.
 *
 * @author Mark Paluch
 */
class GitHubMilestone implements Milestone {

	private final long number;

	private final String title;

	private final boolean open;

	private final @Nullable String description;

	private final @Nullable LocalDateTime releaseDate;

	GitHubMilestone(long number, String title, boolean open, @Nullable String description,
			@Nullable LocalDateTime releaseDate) {
		this.number = number;
		this.title = title;
		this.open = open;
		this.description = description;
		this.releaseDate = releaseDate;
	}

	/**
	 * Return the repository-scoped milestone number.
	 */
	long getNumber() {
		return number;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public @Nullable String getDescription() {
		return description;
	}

	@Override
	public @Nullable LocalDateTime getReleaseDate() {
		return releaseDate;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GitHubMilestone that)) {
			return false;
		}
		return number == that.number;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(number);
	}

	@Override
	public String toString() {
		return title;
	}

}
