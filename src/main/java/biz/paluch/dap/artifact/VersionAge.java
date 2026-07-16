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

package biz.paluch.dap.artifact;

import biz.paluch.dap.support.UpgradeStrategy;

/**
 * Coarse relationship category for a candidate artifact version.
 *
 * <p>{@code VersionAge} intentionally sits after version parsing and upgrade
 * selection: callers use {@link ArtifactVersion} to compare concrete versions
 * and {@link UpgradeStrategy} to select an update target, then use this type to
 * classify the candidate's relationship to the declared version.
 *
 * <p>The categories are not an ordering contract. They describe the
 * relationship useful to a user reviewing an update: older, neutral, patch,
 * minor, major, or preview.
 *
 * @author Mark Paluch
 */
public enum VersionAge {

	/**
	 * Candidate version that compares older than the current version.
	 */
	OLDER,

	/**
	 * Neutral category for equal versions or callers that cannot provide a more
	 * specific relationship.
	 */
	SAME_OR_UNKNOWN,

	/**
	 * Newer candidate in the same major/minor version line.
	 */
	NEWER_PATCH,

	/**
	 * Newer candidate in the same major version line, but outside the current
	 * major/minor line.
	 */
	NEWER_MINOR,

	/**
	 * Newer candidate outside the current major line, or an update target that may
	 * cross stable version boundaries.
	 */
	NEWER_MAJOR,

	/**
	 * Newer milestone or release candidate. Preview status takes precedence over
	 * patch/minor/major presentation because it communicates release stability.
	 */
	PREVIEW;

	/**
	 * Return how the candidate version relates to the current version.
	 * <p>
	 * The result is meant for presentation and should not be used to select an
	 * upgrade candidate. Selection belongs to {@link UpgradeStrategy}; the
	 * returned category classifies an already-known candidate according to the
	 * comparison and version boundary contract of {@link ArtifactVersion}.
	 * @param start the start version (or currently used version).
	 * @param end the end version (or upgrade candidate version)
	 * @return the end candidate version age category.
	 * @see VersionAware
	 */
	public static VersionAge between(VersionAware start, VersionAware end) {
		return between(start.getVersion(), end.getVersion());
	}

	/**
	 * Return how the candidate version relates to the current version.
	 * <p>
	 * The result is meant for presentation and should not be used to select an
	 * upgrade candidate. Selection belongs to {@link UpgradeStrategy}; the
	 * returned category classifies an already-known candidate according to the
	 * comparison and version boundary contract of {@link ArtifactVersion}.
	 * @param start the start version (or currently used version).
	 * @param end the end version (or upgrade candidate version)
	 * @return the end candidate version age category.
	 */
	public static VersionAge between(ArtifactVersion start, ArtifactVersion end) {

		int cmp = end.compareTo(start);
		if (cmp < 0) {
			return OLDER;
		}

		if (cmp == 0) {
			return SAME_OR_UNKNOWN;
		}

		if (end.isMilestoneVersion() || end.isReleaseCandidateVersion()) {
			return PREVIEW;
		}

		if (end.hasSameMajorMinor(start) && end.isNewer(start)) {
			return NEWER_PATCH;
		}

		if (end.hasSameMajor(start) && end.isNewer(start)) {
			return NEWER_MINOR;
		}

		return NEWER_MAJOR;
	}

}
