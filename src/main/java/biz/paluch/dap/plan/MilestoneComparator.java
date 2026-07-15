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

import java.time.LocalDate;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.ticket.Milestone;
import org.jspecify.annotations.Nullable;

/**
 * Canonical milestone ordering, used by the milestone selector and the
 * default-milestone choice: scheduled milestones sort by
 * {@link Milestone#getReleaseDay() release day}, scheduled before unscheduled;
 * same-day and unscheduled milestones sort by their {@link MilestoneVersion};
 * titles the version step cannot tell apart fall back to case-insensitive title
 * order.
 *
 * <p>This enum owns the scheduled-before-unscheduled rule. Views that visualize
 * the resulting order ask {@link #startsNewGroup} rather than re-deriving it.
 *
 * @author Mark Paluch
 * @see MilestoneVersion
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
	 * Return whether this ordering crosses the scheduled-to-unscheduled boundary
	 * between two milestones adjacent in a sorted list, the one discontinuity a
	 * view can render as a group separator.
	 *
	 * @param previous the milestone preceding {@code next} in the sorted list.
	 * @param next the milestone following {@code previous} in the sorted list.
	 * @return {@literal true} if {@code next} opens the unscheduled group;
	 * {@literal false} otherwise.
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
		 * Read the version out of the given milestone's title.
		 *
		 * @param title the milestone title to read.
		 * @return the extracted version; one that compares equal to everything when the
		 * title carries none or carries one that does not parse.
		 */
		static MilestoneVersion of(String title) {

			Matcher matcher = VERSION_CANDIDATE.matcher(title);
			if (!matcher.find()) {
				return ABSENT;
			}

			return ArtifactVersion.from(matcher.group(1)).map(MilestoneVersion::new).orElse(ABSENT);
		}

		/**
		 * Compare this version to another, ordering older before newer.
		 *
		 * <p>The order is partial: {@code 0} means the two cannot be told apart by
		 * version, either because they are equal, because either title carries no
		 * readable version, or because they follow version schemes that do not compare
		 * (a calendar version against a semantic one). Callers must break a {@code 0}
		 * with a total fallback of their own; this type intentionally does not
		 * implement {@link Comparable}, because its order is not one.
		 *
		 * @param other the version to compare against.
		 * @return a negative value if this version is older, a positive value if it is
		 * newer, {@code 0} if the two are indistinguishable by version.
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
