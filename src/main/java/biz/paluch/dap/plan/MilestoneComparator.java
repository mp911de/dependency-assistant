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

package biz.paluch.dap.plan;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.ticket.Milestone;
import org.jspecify.annotations.Nullable;

/**
 * Order milestones by release day, with unscheduled milestones last.
 * <p>Ties use the version in the title, then case-insensitive title order. Use
 * {@link #startsNewGroup} for the scheduled-to-unscheduled boundary.
 *
 * @author Mark Paluch
 */
enum MilestoneComparator implements Comparator<Milestone> {

	INSTANCE;

	@Override
	public int compare(Milestone left, Milestone right) {

		LocalDate leftDay = left.getReleaseDay();
		LocalDate rightDay = right.getReleaseDay();

		if (leftDay != null && rightDay != null) {

			int byDay = leftDay.compareTo(rightDay);
			return byDay != 0 ? byDay : compareByVersion(left, right);
		}

		if (leftDay != null || rightDay != null) {
			return leftDay != null ? -1 : 1;
		}

		return compareByVersion(left, right);
	}

	/**
	 * Return whether adjacent sorted milestones cross into the unscheduled group.
	 */
	static boolean startsNewGroup(Milestone previous, Milestone next) {
		return previous.getReleaseDay() != null && next.getReleaseDay() == null;
	}

	private static int compareByVersion(Milestone left, Milestone right) {

		int byVersion = MilestoneVersion.of(left.getTitle()).compareTo(MilestoneVersion.of(right.getTitle()));
		return byVersion != 0 ? byVersion : String.CASE_INSENSITIVE_ORDER.compare(left.getTitle(), right.getTitle());
	}

	static class MilestoneVersion {

		private static final Pattern VERSION_CANDIDATE = Pattern.compile("(?:^|\\s)(v?\\d\\S*)");

		private static final MilestoneVersion ABSENT = new MilestoneVersion(null);

		private final @Nullable ArtifactVersion version;

		private MilestoneVersion(@Nullable ArtifactVersion version) {
			this.version = version;
		}

		/**
		 * Extract a title version, or an absent value if unreadable.
		 */
		static MilestoneVersion of(String title) {

			Matcher matcher = VERSION_CANDIDATE.matcher(title);
			if (!matcher.find()) {
				return ABSENT;
			}

			return ArtifactVersion.from(matcher.group(1)).map(MilestoneVersion::new).orElse(ABSENT);
		}

		/**
		 * Compare older versions before newer ones.
		 * <p>Return zero for equal, absent or incomparable versions. Callers must
		 * supply a fallback order. This partial comparison does not implement
		 * {@link Comparable}.
		 */
		int compareTo(MilestoneVersion other) {

			if (version == null || other.version == null || !version.canCompare(other.version)) {
				return 0;
			}
			return version.compareTo(other.version);
		}

		@Override
		public String toString() {
			return version != null ? version.toString() : "<none>";
		}

	}
}
