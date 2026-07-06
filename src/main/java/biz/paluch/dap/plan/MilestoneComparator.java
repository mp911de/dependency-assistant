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
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.ticket.Milestone;
import org.jspecify.annotations.Nullable;

/**
 * Canonical milestone ordering, used by the milestone selector and the
 * default-milestone choice: milestones with a release date sort by day, dated
 * before undated; same-day and undated milestones sort by the version extracted
 * from their title; titles without a comparable version fall back to
 * case-insensitive title order.
 *
 * <p>Milestone titles are free-form, so version extraction is a heuristic: the
 * first digit-or-{@code v}-led run up to whitespace, parsed through
 * {@link ArtifactVersion#from}. Parsing milestones is not an exact science; an
 * unparsable or incomparable candidate simply disqualifies the version step.
 *
 * @author Mark Paluch
 */
enum MilestoneComparator implements Comparator<Milestone> {

	INSTANCE;

	// first whitespace-delimited token led by a digit or v-prefix, mirroring the
	// shapes ArtifactVersion parses (1.2.3, 4.0.0-RC1, v2.1, 2025.0.0); anchoring
	// on the token start keeps digits inside words (Turing-SR1) from matching
	private static final Pattern VERSION_CANDIDATE = Pattern.compile("(?:^|\\s)(v?\\d\\S*)");

	@Override
	public int compare(Milestone left, Milestone right) {

		LocalDate leftDay = releaseDay(left);
		LocalDate rightDay = releaseDay(right);

		if (leftDay != null && rightDay != null) {

			int byDay = leftDay.compareTo(rightDay);
			return byDay != 0 ? byDay : compareByVersion(left, right);
		}

		if (leftDay != null || rightDay != null) {
			return leftDay != null ? -1 : 1;
		}

		return compareByVersion(left, right);
	}

	private static int compareByVersion(Milestone left, Milestone right) {

		ArtifactVersion leftVersion = versionOf(left);
		ArtifactVersion rightVersion = versionOf(right);

		if (leftVersion != null && rightVersion != null && leftVersion.canCompare(rightVersion)) {

			int byVersion = leftVersion.compareTo(rightVersion);
			if (byVersion != 0) {
				return byVersion;
			}
		}

		return String.CASE_INSENSITIVE_ORDER.compare(left.getTitle(), right.getTitle());
	}

	private static @Nullable LocalDate releaseDay(Milestone milestone) {

		LocalDateTime releaseDate = milestone.getReleaseDate();
		return releaseDate != null ? releaseDate.toLocalDate() : null;
	}

	private static @Nullable ArtifactVersion versionOf(Milestone milestone) {

		Matcher matcher = VERSION_CANDIDATE.matcher(milestone.getTitle());
		if (!matcher.find()) {
			return null;
		}

		return ArtifactVersion.from(matcher.group(1)).orElse(null);
	}

}
