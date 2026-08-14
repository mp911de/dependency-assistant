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

package biz.paluch.dap.assistant.review;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;

/**
 * A candidate's release line: its release versions at or above the agreed
 * current version, capped at the ten most recent releases, release dates
 * ignored.
 *
 * <p>Two candidates satisfy release-line agreement precisely when their lines
 * are equal; {@link InferredGrouping} partitions agreeing cohorts by this
 * equality.
 *
 * @author Mark Paluch
 */
record ReleaseLine(Set<ArtifactVersion> versions) {

	/**
	 * Compute the release line of the given member relative to the agreed current
	 * version.
	 *
	 * @param member the candidate whose releases form the line.
	 * @param currentVersion the agreed current version acting as the lower bound.
	 * @return the member's release line.
	 */
	static ReleaseLine of(TableRow member, ArtifactVersion currentVersion) {

		Set<ArtifactVersion> line = new HashSet<>();
		Releases releases = member.getUpgrade().getReleases();
		List<ArtifactVersion> allVersions = releases.stream().map(Release::version)
				.toList();

		int recentVersions = Math.min(10, allVersions.size());
		for (ArtifactVersion version : allVersions.subList(0, recentVersions)) {
			if (version.equals(currentVersion) || version.isNewer(currentVersion)) {
				line.add(version);
			}
		}

		return new ReleaseLine(line);
	}

}
