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

package biz.paluch.dap.assistant;

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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * The dependency-check rows after collapsing governed, version-agreeing upgrade
 * candidates into {@link UpgradeGroup} rows.
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
 * @see UpgradeGroup
 */
public class UpgradeGroups implements Iterable<UpgradeCandidate> {

	private final List<UpgradeCandidate> groups;

	private UpgradeGroups(List<UpgradeCandidate> groups) {
		this.groups = List.copyOf(groups);
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
	 * Collapse groupable candidates into {@link UpgradeGroup} rows, keeping all
	 * other candidates as individual rows.
	 *
	 * @param candidates the aggregated candidates in display order.
	 * @return the rows with each group replacing its members at the position of the
	 * first member.
	 */
	static UpgradeGroups of(List<UpgradeCandidate> candidates) {

		Map<GroupKey, List<UpgradeCandidate>> governed = new LinkedHashMap<>();
		Map<String, List<UpgradeCandidate>> governedByName = new LinkedHashMap<>();
		List<UpgradeCandidate> ungoverned = new ArrayList<>();
		for (UpgradeCandidate candidate : candidates) {
			GroupKey key = GroupKey.of(candidate);
			if (key != null) {
				governed.computeIfAbsent(key, it -> new ArrayList<>()).add(candidate);
				governedByName.computeIfAbsent(key.dependencyName(), it -> new ArrayList<>()).add(candidate);
			}
			else {
				ungoverned.add(candidate);
			}
		}

		governedByName.values().forEach(named -> {
			if (named.size() == 1) {
				named.getFirst().labelByDependencyName();
			}
		});

		Map<UpgradeCandidate, UpgradeGroup> firstMemberToGroup = new LinkedHashMap<>();
		Set<UpgradeCandidate> grouped = new LinkedHashSet<>();

		governed.values().forEach(bucket -> {

			AgreeingCohort cohort = selectCohort(bucket);
			if (cohort == null || cohort.members().size() < 2) {
				return;
			}

			List<UpgradeCandidate> members = withPropertySharingDrifters(bucket, cohort.members());
			firstMemberToGroup.put(members.getFirst(), UpgradeGroup.of(members));
			grouped.addAll(members);
		});

		collapseInferred(ungoverned, firstMemberToGroup, grouped);

		List<UpgradeCandidate> rows = new ArrayList<>(candidates.size());
		for (UpgradeCandidate candidate : candidates) {

			UpgradeGroup group = firstMemberToGroup.get(candidate);
			if (group != null) {
				rows.add(group);
				continue;
			}

			if (!grouped.contains(candidate)) {
				rows.add(candidate);
			}
		}

		return new UpgradeGroups(rows);
	}

	@Override
	public Iterator<UpgradeCandidate> iterator() {
		return groups.iterator();
	}

	/**
	 * Return the collapsed rows in display order.
	 *
	 * @return the rows.
	 */
	public List<UpgradeCandidate> toList() {
		return groups;
	}

	@Override
	public String toString() {

		String toString = "UpgradeGroups: " + System.lineSeparator();
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
	private static @Nullable AgreeingCohort selectCohort(List<UpgradeCandidate> bucket) {

		Map<ArtifactVersion, List<UpgradeCandidate>> cohorts = new LinkedHashMap<>();
		for (UpgradeCandidate candidate : bucket) {
			for (ArtifactVersion version : candidate.getDeclaredVersions().versions()) {
				cohorts.computeIfAbsent(version, it -> new ArrayList<>()).add(candidate);
			}
		}

		AgreeingCohort selected = null;
		for (Map.Entry<ArtifactVersion, List<UpgradeCandidate>> cohort : cohorts.entrySet()) {

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
	private static List<UpgradeCandidate> withPropertySharingDrifters(List<UpgradeCandidate> bucket,
			List<UpgradeCandidate> cohort) {

		Set<String> memberProperties = new LinkedHashSet<>();
		cohort.forEach(member -> memberProperties.addAll(member.getVersionPropertyNames()));

		List<UpgradeCandidate> members = new ArrayList<>(bucket.size());
		for (UpgradeCandidate candidate : bucket) {

			if (cohort.contains(candidate) || (candidate.getDeclaredVersions().hasVersionDrift()
					&& !Collections.disjoint(candidate.getVersionPropertyNames(), memberProperties))) {
				members.add(candidate);
			}
		}

		return members;
	}

	/**
	 * Collapse ungoverned candidates into {@link UpgradeGroup} rows by coordinate
	 * shape. Candidates bucket by group id plus their leading word-boundary token;
	 * within a bucket the largest version-agreeing cohort is partitioned by
	 * <em>Release Line Agreement</em>, and each partition that has at least two
	 * members and a derivable {@link DerivedGroupName} forms an inferred group.
	 * Members whose release line is unique stay individual rows. Drifting members
	 * join by version match only; a shared version property never pulls a member
	 * in.
	 */
	private static void collapseInferred(List<UpgradeCandidate> ungoverned,
			Map<UpgradeCandidate, UpgradeGroup> firstMemberToGroup, Set<UpgradeCandidate> grouped) {

		Map<InferredKey, List<UpgradeCandidate>> families = new LinkedHashMap<>();
		for (UpgradeCandidate candidate : ungoverned) {
			families.computeIfAbsent(InferredKey.of(candidate), it -> new ArrayList<>()).add(candidate);
		}

		families.forEach((key, family) -> {

			AgreeingCohort cohort = selectCohort(family);
			if (cohort == null || cohort.members().size() < 2) {
				return;
			}

			Map<Set<ArtifactVersion>, List<UpgradeCandidate>> releaseLines = new LinkedHashMap<>();
			for (UpgradeCandidate member : cohort.members()) {
				releaseLines.computeIfAbsent(releaseLine(member, cohort.version()), it -> new ArrayList<>())
						.add(member);
			}

			releaseLines.values().forEach(line -> {

				if (line.size() < 2) {
					return;
				}

				List<String> artifactIds = line.stream().map(it -> it.getArtifactId().artifactId()).toList();
				DerivedGroupName name = DerivedGroupName.of(key.groupId(), artifactIds);
				if (name == null) {
					return;
				}

				firstMemberToGroup.put(line.getFirst(), UpgradeGroup.inferred(line, name.displayName()));
				grouped.addAll(line);
			});
		});
	}

	/**
	 * The member's <em>Release Line</em>: its release versions at or above the
	 * agreed current version, release dates ignored. Two members satisfy Release
	 * Line Agreement precisely when these sets are equal.
	 */
	private static Set<ArtifactVersion> releaseLine(UpgradeCandidate member, ArtifactVersion currentVersion) {

		List<ArtifactVersion> allVersions = new ArrayList<>();
		Set<ArtifactVersion> line = new HashSet<>();
		Releases releases = member.getUpdateCandidate().getReleases();
		for (Release release : releases) {
			allVersions.add(release.version());
			ArtifactVersion version = release.version();
			if (version.equals(currentVersion) || version.isNewer(currentVersion)) {
				line.add(version);
			}
		}

		int lastVersions = Math.min(10, allVersions.size());
		line.addAll(allVersions.subList(allVersions.size() - lastVersions, allVersions.size() - 1));

		return line;
	}

	public boolean isEmpty() {
		return groups.isEmpty();
	}

	/**
	 * The largest version-agreeing cohort within a bucket, paired with the version
	 * its members agree on. The agreed version is the lower bound of each member's
	 * {@link #releaseLine(UpgradeCandidate, ArtifactVersion) Release Line}.
	 */
	private record AgreeingCohort(ArtifactVersion version, List<UpgradeCandidate> members) {
	}

	/**
	 * Grouping identity: the rule's dependency name within one build ecosystem.
	 */
	private record GroupKey(String dependencyName, Class<?> ecosystem) {

		/**
		 * Return the group key for the candidate, or {@literal null} if the candidate
		 * is not governed by a named rule.
		 */
		static @Nullable GroupKey of(UpgradeCandidate candidate) {

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

		static InferredKey of(UpgradeCandidate candidate) {

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
	 * Collects candidates and collapses them into {@link UpgradeGroups} on
	 * {@link #build()}.
	 */
	static class Builder {

		private final List<UpgradeCandidate> candidates = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Add a candidate to be collapsed.
		 *
		 * @param candidate the candidate to add.
		 * @return this builder.
		 */
		public Builder add(UpgradeCandidate candidate) {
			candidates.add(candidate);
			return this;
		}

		/**
		 * Collapse the added candidates into upgrade groups.
		 *
		 * @return the collapsed rows.
		 */
		public UpgradeGroups build() {
			return of(candidates);
		}

	}

}
