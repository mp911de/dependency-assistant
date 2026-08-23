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

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.CoordinateShape;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.state.ApplicationSettings;
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
class InferredGrouping implements GroupingPolicy<SingleTableRow, GroupRow> {

	private final ApplicationSettings settings;

	InferredGrouping() {
		this.settings = ApplicationSettings.getInstance();
	}

	@Override
	public List<GroupRow> group(List<SingleTableRow> candidates) {

		MultiValueMap<Family, SingleTableRow> families = new LinkedMultiValueMap<>();
		for (SingleTableRow candidate : candidates) {

			if (!GroupByRule.isApplicable(candidate)) {
				families.add(Family.of(candidate), candidate);
			}
		}

		List<GroupRow> groups = new ArrayList<>();
		families.forEach((family, members) -> groups.addAll(groupFamily(family, members)));
		return groups;
	}

	private List<GroupRow> groupFamily(Family family, List<SingleTableRow> members) {

		VersionAgreement agreement = VersionAgreement.select(members);
		if (agreement == null || agreement.size() < 2) {
			return List.of();
		}

		MultiValueMap<ReleaseLine, SingleTableRow> releaseLines = new LinkedMultiValueMap<>();
		for (SingleTableRow member : agreement.members()) {
			releaseLines.add(ReleaseLine.of(member, agreement.version()), member);
		}

		List<GroupRow> groups = new ArrayList<>();
		releaseLines.values().forEach(line -> {

			if (line.size() < 2) {
				return;
			}
			GroupRow group = groupReleaseLine(family, line);
			if (group == null) {
				return;
			}

			groups.add(group);
		});

		return groups;
	}

	private @Nullable GroupRow groupReleaseLine(Family family, List<SingleTableRow> line) {

		List<PackageIdentity> packages = line.stream().map(SingleTableRow::getPackageIdentity).toList();
		String name = settings.findNameHint(packages);
		if (name == null) {
			name = CoordinateShape
					.of(packages.stream().map(PackageIdentity::getArtifactId).map(ArtifactId::artifactId).toList())
					.deriveGroupName(family.groupId());
		}
		return name == null ? null : GroupRow.inferred(name, line);
	}

	/**
	 * Grouping identity for an ungoverned candidate: its coordinate family, the
	 * artifact id up to the first {@code -} or {@code .} boundary, within its group
	 * id and build ecosystem.
	 */
	private record Family(String groupId, PackageSystem packageSystem, String token) {

		static Family of(SingleTableRow candidate) {
			ArtifactId artifactId = candidate.getArtifactId();
			return new Family(artifactId.groupId(),
					candidate.getPackageSystem(),
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
