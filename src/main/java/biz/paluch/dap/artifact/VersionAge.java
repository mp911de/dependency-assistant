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

package biz.paluch.dap.artifact;

import biz.paluch.dap.support.UpgradeStrategy;

/**
 * Presentation category for a candidate version relative to the current
 * version.
 * <p>Use {@link UpgradeStrategy} to select an upgrade. These categories do not
 * establish version ordering.
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
	 * Newer candidate outside the current major line or in another versioning
	 * scheme.
	 */
	NEWER_MAJOR,

	/**
	 * Newer milestone or release candidate. Preview status takes precedence over
	 * patch/minor/major presentation because it communicates release stability.
	 */
	PREVIEW;

	/**
	 * Classify the candidate relative to the current version for presentation.
	 * @param start the current version.
	 * @param end the candidate version.
	 */
	public static VersionAge between(VersionAware start, VersionAware end) {
		return between(start.getVersion(), end.getVersion());
	}

	/**
	 * Classify the candidate relative to the current version for presentation.
	 * @param start the current version.
	 * @param end the candidate version.
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
