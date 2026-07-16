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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * The dependency-check rows after collapsing governed, version-agreeing upgrade
 * candidates into {@link GroupRow} rows.
 *
 * <p>Candidates group when they are governed by the same named
 * {@link DependencyRule}, belong to the same build ecosystem, and agree on one
 * effective current version. Per rule name only the largest agreeing cohort
 * forms the group; equal cohort sizes tie-break to the higher version. Each
 * group replaces its members at the position of the first member; all other
 * candidates remain individual rows in their original order.
 *
 * <p>Ungoverned candidates then collapse as a fallback by coordinate shape,
 * with the winning cohort additionally split so that only members sharing the
 * same release line (their releases at or above the agreed current version)
 * group together. See ADR 0017.
 *
 * <p>Iterate the result to obtain the rows in display order. Build it either
 * from a complete candidate list through {@link #of(List)} or incrementally
 * through {@link #builder()}.
 *
 * @author Mark Paluch
 * @see GroupRow
 */
class UpgradeRows implements Sequence<TableRow> {

	private final List<TableRow> groups;

	private UpgradeRows(List<TableRow> groups) {
		this.groups = groups;
	}

	/**
	 * Return a builder collecting candidates before collapsing them.
	 *
	 * @return a new builder.
	 */
	static Builder builder() {
		return new Builder();
	}

	/**
	 * Collapse groupable candidates into {@link GroupRow} rows, keeping all other
	 * candidates as individual rows.
	 *
	 * @param candidates the aggregated candidates in display order.
	 * @return the rows with each group replacing its members at the position of the
	 * first member.
	 */
	static UpgradeRows of(List<TableRow> candidates) {

		Map<GroupKey, List<TableRow>> governed = new LinkedHashMap<>();
		Map<String, List<TableRow>> governedByName = new LinkedHashMap<>();
		List<TableRow> ungoverned = new ArrayList<>();
		for (TableRow candidate : candidates) {
			GroupKey key = GroupKey.of(candidate);
			if (key != null) {
				governed.computeIfAbsent(key, it -> new ArrayList<>()).add(candidate);
				governedByName.computeIfAbsent(key.dependencyName(), it -> new ArrayList<>()).add(candidate);
			} else {
				ungoverned.add(candidate);
			}
		}

		governedByName.values().forEach(named -> {
			if (named.size() == 1) {
				named.getFirst().labelByDependencyName();
			}
		});

		Map<TableRow, GroupRow> firstMemberToGroup = new LinkedHashMap<>();
		Set<TableRow> grouped = new LinkedHashSet<>();

		governed.values().forEach(bucket -> {

			AgreeingCohort cohort = selectCohort(bucket);
			if (cohort == null || cohort.members().size() < 2) {
				return;
			}

			List<TableRow> members = withPropertySharingDrifters(bucket, cohort.members());
			firstMemberToGroup.put(members.getFirst(), GroupRow.governed(members));
			grouped.addAll(members);
		});

		collapseInferred(ungoverned, firstMemberToGroup, grouped);

		List<TableRow> rows = new ArrayList<>(candidates.size());
		for (TableRow candidate : candidates) {

			GroupRow group = firstMemberToGroup.get(candidate);
			if (group != null) {
				rows.add(group);
				continue;
			}

			if (!grouped.contains(candidate)) {
				rows.add(candidate);
			}
		}

		return new UpgradeRows(rows);
	}

	@Override
	public Iterator<TableRow> iterator() {
		return groups.iterator();
	}

	@Override
	public Stream<TableRow> stream() {
		return groups.stream();
	}

	/**
	 * Return the collapsed rows in display order.
	 *
	 * @return the rows.
	 */
	@Override
	public List<TableRow> toList() {
		return groups;
	}

	@Override
	public String toString() {

		String toString = "UpgradeRows: " + System.lineSeparator();
		toString += groups.stream()
				.map(it -> " * " + it.getDependencyName() + "@" + it.getCurrentVersion())
				.collect(Collectors.joining(System.lineSeparator()));
		return toString;
	}

	/**
	 * Select the largest version-agreeing cohort within the bucket, tie-breaking
	 * equal sizes to the higher version. A drifting candidate agrees with every
	 * version one of its occurrences is declared at.
	 *
	 * @return the cohort with its agreed version, or {@literal null} when the
	 * bucket declares no version.
	 */
	private static @Nullable AgreeingCohort selectCohort(List<TableRow> bucket) {

		Map<ArtifactVersion, List<TableRow>> cohorts = new LinkedHashMap<>();
		for (TableRow candidate : bucket) {
			for (ArtifactVersion version : candidate.getDeclaredVersions().versions()) {
				cohorts.computeIfAbsent(version, it -> new ArrayList<>()).add(candidate);
			}
		}

		AgreeingCohort selected = null;
		for (Map.Entry<ArtifactVersion, List<TableRow>> cohort : cohorts.entrySet()) {

			if (selected == null || cohort.getValue().size() > selected.members().size()
					|| (cohort.getValue().size() == selected.members().size()
							&& cohort.getKey().isNewer(selected.version()))) {
				selected = new AgreeingCohort(cohort.getKey(), cohort.getValue());
			}
		}

		return selected;
	}

	/**
	 * Extend the cohort with drifting bucket candidates that share a version
	 * property with a cohort member, preserving bucket order.
	 */
	private static List<TableRow> withPropertySharingDrifters(List<TableRow> bucket,
			List<TableRow> cohort) {

		Set<String> memberProperties = new LinkedHashSet<>();
		cohort.forEach(member -> memberProperties.addAll(member.getVersionPropertyNames()));

		List<TableRow> members = new ArrayList<>(bucket.size());
		for (TableRow candidate : bucket) {

			if (cohort.contains(candidate) || (candidate.getDeclaredVersions().hasVersionDrift()
					&& !Collections.disjoint(candidate.getVersionPropertyNames(), memberProperties))) {
				members.add(candidate);
			}
		}

		return members;
	}

	/**
	 * Collapse ungoverned candidates into {@link GroupRow} rows by coordinate
	 * shape. Candidates bucket by group id plus their leading word-boundary token;
	 * within a bucket the largest version-agreeing cohort is partitioned by
	 * <em>Release Line Agreement</em>, and each partition that has at least two
	 * members and a {@link CoordinateShape#deriveGroupName(String) derivable group
	 * name} forms an inferred group. Members whose release line is unique stay
	 * individual rows. Drifting members join by version match only; a shared
	 * version property never pulls a member in.
	 */
	private static void collapseInferred(List<TableRow> ungoverned,
			Map<TableRow, GroupRow> firstMemberToGroup, Set<TableRow> grouped) {

		Map<InferredKey, List<TableRow>> families = new LinkedHashMap<>();
		for (TableRow candidate : ungoverned) {
			families.computeIfAbsent(InferredKey.of(candidate), it -> new ArrayList<>()).add(candidate);
		}

		families.forEach((key, family) -> {

			AgreeingCohort cohort = selectCohort(family);
			if (cohort == null || cohort.members().size() < 2) {
				return;
			}

			Map<Set<ArtifactVersion>, List<TableRow>> releaseLines = new LinkedHashMap<>();
			for (TableRow member : cohort.members()) {
				releaseLines.computeIfAbsent(releaseLine(member, cohort.version()), it -> new ArrayList<>())
						.add(member);
			}

			releaseLines.values().forEach(line -> {

				if (line.size() < 2) {
					return;
				}

				List<String> artifactIds = line.stream().map(it -> it.getArtifactId().artifactId()).toList();
				String name = CoordinateShape.of(artifactIds).deriveGroupName(key.groupId());
				if (name == null) {
					return;
				}

				firstMemberToGroup.put(line.getFirst(), GroupRow.inferred(line, name));
				grouped.addAll(line);
			});
		});
	}

	/**
	 * The member's <em>Release Line</em>: its release versions at or above the
	 * agreed current version, release dates ignored. Two members satisfy Release
	 * Line Agreement precisely when these sets are equal.
	 */
	private static Set<ArtifactVersion> releaseLine(TableRow member, ArtifactVersion currentVersion) {

		List<ArtifactVersion> allVersions = new ArrayList<>();
		Set<ArtifactVersion> line = new HashSet<>();
		Releases releases = member.getUpgrade().getReleases();
		for (Release release : releases) {
			allVersions.add(release.version());
		}

		int recentVersions = Math.min(10, allVersions.size());
		for (ArtifactVersion version : allVersions.subList(0, recentVersions)) {
			if (version.equals(currentVersion) || version.isNewer(currentVersion)) {
				line.add(version);
			}
		}

		return line;
	}

	@Override
	public boolean isEmpty() {
		return groups.isEmpty();
	}

	/**
	 * The largest version-agreeing cohort within a bucket, paired with the version
	 * its members agree on. The agreed version is the lower bound of each member's
	 * {@link #releaseLine(TableRow, ArtifactVersion) Release Line}.
	 */
	private record AgreeingCohort(ArtifactVersion version, List<TableRow> members) {
	}

	/**
	 * Grouping identity: the rule's dependency name within one build ecosystem.
	 */
	private record GroupKey(String dependencyName, Class<?> ecosystem) {

		/**
		 * Return the group key for the candidate, or {@literal null} if the candidate
		 * is not governed by a named rule.
		 */
		static @Nullable GroupKey of(TableRow candidate) {

			DependencyRule rule = candidate.getRule();
			if (!rule.isPresent() || StringUtils.isEmpty(rule.getDependencyName())) {
				return null;
			}

			return new GroupKey(rule.getDependencyName(), candidate.getInterfaceAssistant().getClass());
		}

	}

	/**
	 * Grouping identity for an ungoverned candidate: its group id and leading
	 * word-boundary token within one build ecosystem.
	 */
	private record InferredKey(String groupId, String leadingToken, Class<?> ecosystem) {

		static InferredKey of(TableRow candidate) {

			ArtifactId id = candidate.getArtifactId();
			String artifactId = id.artifactId();
			int boundary = firstBoundary(artifactId);
			String token = boundary < 0 ? artifactId : artifactId.substring(0, boundary);

			return new InferredKey(id.groupId(), token, candidate.getInterfaceAssistant().getClass());
		}

		private static int firstBoundary(String artifactId) {

			for (int i = 0; i < artifactId.length(); i++) {
				char c = artifactId.charAt(i);
				if (c == '-' || c == '.') {
					return i;
				}
			}
			return -1;
		}

	}

	/**
	 * Collects candidates and collapses them into {@link UpgradeRows} on
	 * {@link #build()}.
	 */
	static class Builder {

		private final List<TableRow> candidates = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Add a candidate to be collapsed.
		 *
		 * @param candidate the candidate to add.
		 * @return this builder.
		 */
		public Builder add(TableRow candidate) {
			candidates.add(candidate);
			return this;
		}

		/**
		 * Collapse the added candidates into upgrade groups.
		 *
		 * @return the collapsed rows.
		 */
		public UpgradeRows build() {
			return of(candidates);
		}

	}

}
