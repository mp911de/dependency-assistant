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

package biz.paluch.dap.plan;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import biz.paluch.dap.ticket.Milestone;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MilestoneComparator}.
 *
 * @author Mark Paluch
 */
class MilestoneComparatorUnitTests {

	@Test
	void ordersDatedMilestonesByDay() {
		assertOrder(milestone("4.6", "2026-03-15"), milestone("4.5", "2026-06-01"));
	}

	@Test
	void sameDayFallsBackToVersion() {
		assertOrder(milestone("4.5.1", "2026-06-01"), milestone("4.6 GA", "2026-06-01"),
				milestone("4.10 GA", "2026-06-01"));
	}

	@Test
	void datedSortsBeforeUndated() {
		assertOrder(milestone("Backlog", "2026-06-01"), milestone("4.5", null));
	}

	@Test
	void undatedSortByExtractedVersion() {
		assertOrder(milestone("v4.6", null), milestone("4.10 (Ockham)", null), milestone("Spring Data 2025.1.3", null));
	}

	@Test
	void unparsableTitlesFallBackToTitleOrder() {
		assertOrder(milestone("backlog", null), milestone("Icebox", null));
	}

	@Test
	void incomparableVersionSchemesFallBackToTitleOrder() {
		assertOrder(milestone("2025.1.0 train", null), milestone("Turing-SR1", null));
	}

	private static void assertOrder(Milestone... expected) {

		List<Milestone> sorted = new ArrayList<>(List.of(expected));
		Collections.reverse(sorted);
		sorted.sort(MilestoneComparator.INSTANCE);

		assertThat(sorted).containsExactly(expected);
	}

	private static Milestone milestone(String title, @Nullable String releaseDay) {

		return new Milestone() {

			@Override
			public String getTitle() {
				return title;
			}

			@Override
			public boolean isOpen() {
				return true;
			}

			@Override
			public @Nullable String getDescription() {
				return null;
			}

			@Override
			public @Nullable LocalDateTime getReleaseDate() {
				return releaseDay != null ? LocalDateTime.parse(releaseDay + "T10:00:00") : null;
			}

			@Override
			public String toString() {
				return title;
			}

		};
	}

}
