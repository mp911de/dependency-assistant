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

package biz.paluch.dap.assistant.review;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import org.jspecify.annotations.Nullable;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Groups ungoverned candidates by coordinate {@link Family}.
 *
 * <p>Within each family, the largest version-agreeing cohort is partitioned by
 * {@link ReleaseLine}, and each partition of at least two members whose group
 * name is derivable from the members' coordinate shape forms an inferred group.
 *
 * @author Mark Paluch
 * @see GroupByRule#isApplicable(TableRow)
 */
class InferredGrouping implements GroupingPolicy<TableRow, GroupRow> {

	@Override
	public List<GroupRow> group(List<TableRow> candidates) {

		MultiValueMap<Family, TableRow> families = new LinkedMultiValueMap<>();
		for (TableRow candidate : candidates) {

			if (!GroupByRule.isApplicable(candidate)) {
				families.add(Family.of(candidate), candidate);
			}
		}

		List<GroupRow> groups = new ArrayList<>();
		families.forEach((family, members) -> groups.addAll(groupFamily(family.groupId(), members)));
		return groups;
	}

	/**
	 * Group one family: the largest version-agreeing cohort is partitioned by
	 * {@link ReleaseLine}, and each partition with at least two members and a
	 * derivable group name forms an inferred group. Members whose release line is
	 * unique stay individual rows. Drifting members join by version match only; a
	 * shared version property never pulls a member in.
	 */
	private static List<GroupRow> groupFamily(String groupId, List<TableRow> family) {

		VersionAgreement agreement = VersionAgreement.select(family);
		if (agreement == null || agreement.size() < 2) {
			return List.of();
		}

		MultiValueMap<ReleaseLine, TableRow> releaseLines = new LinkedMultiValueMap<>();
		for (TableRow member : agreement.members()) {
			releaseLines.add(ReleaseLine.of(member, agreement.version()), member);
		}

		List<GroupRow> groups = new ArrayList<>();
		releaseLines.values().forEach(line -> {

			if (line.size() < 2) {
				return;
			}
			GroupRow group = groupReleaseLine(groupId, line);
			if (group == null) {
				return;
			}

			groups.add(group);
		});

		return groups;
	}

	private static @Nullable GroupRow groupReleaseLine(String groupId, List<TableRow> line) {

		List<String> artifactIds = line.stream().map(it -> it.getArtifactId().artifactId()).toList();
		String name = CoordinateShape.of(artifactIds).deriveGroupName(groupId);
		return name == null ? null : GroupRow.inferred(line, name);
	}

	/**
	 * Grouping identity for an ungoverned candidate: its coordinate family, the
	 * artifact id up to the first {@code -} or {@code .} boundary, within its group
	 * id and build ecosystem.
	 */
	private record Family(String groupId, String ecosystem, String token) {

		static Family of(TableRow candidate) {
			ArtifactId artifactId = candidate.getArtifactId();
			return new Family(artifactId.groupId(),
					candidate.getUpgrade().getPresentation().getEcosystem(),
					token(artifactId.artifactId()));
		}

		private static String token(String artifactId) {

			for (int i = 0; i < artifactId.length(); i++) {
				char c = artifactId.charAt(i);
				if (c == '-' || c == '.') {
					return artifactId.substring(0, i);
				}
			}
			return artifactId;
		}

	}

}
