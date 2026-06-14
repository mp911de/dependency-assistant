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
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.rule.DependencyRule;

import org.springframework.util.Assert;

/**
 * Upgrade Group row collapsing several governed upgrade candidates that agree
 * on one effective current version into a single dependency-check row.
 *
 * <p>The group row offers the intersection of its members' releases, reports
 * the lowest declared version across all member occurrences as its current
 * version, and labels itself with the governing rule's dependency name. One
 * decision on the group fans out to every member occurrence on apply.
 *
 * @author Mark Paluch
 * @see UpgradeGroups
 */
class UpgradeGroup extends UpgradeCandidate {

	private static final int MEMBER_LABEL_LIMIT = 25;

	private final List<UpgradeCandidate> members;

	private UpgradeGroup(DependencyUpdateCandidate candidate, InterfaceAssistant assistant,
			DeclaredVersions declaredVersions, DependencyRule rule, List<UpgradeCandidate> members) {

		super(candidate, assistant, declaredVersions, rule);
		this.members = List.copyOf(members);
		labelByDependencyName();
	}

	/**
	 * Create an upgrade group from the given members.
	 *
	 * @param members the agreeing candidates governed by the same named rule; must
	 * contain at least two members.
	 * @return the group row aggregating the members.
	 */
	static UpgradeGroup of(List<UpgradeCandidate> members) {

		Assert.isTrue(members.size() >= 2, "Upgrade group requires at least two members");

		UpgradeCandidate first = members.getFirst();
		DeclaredVersions declaredVersions = mergeDeclaredVersions(members);

		Dependency merged = new Dependency(first.getArtifactId(), declaredVersions.getLowestDeclaredVersion());
		for (UpgradeCandidate member : members) {
			Dependency dependency = member.getUpdateCandidate().getDependency();
			merged.addAllDeclarationSources(dependency.getDeclarationSources());
			merged.addAllVersionSources(dependency.getVersionSources());
		}

		DependencyUpdateCandidate candidate = new DependencyUpdateCandidate(merged, intersectReleases(members));
		return new UpgradeGroup(candidate, first.getInterfaceAssistant(), declaredVersions, first.getRule(), members);
	}

	private static DeclaredVersions mergeDeclaredVersions(List<UpgradeCandidate> members) {

		Set<ArtifactVersion> versions = new TreeSet<>(Comparator.reverseOrder());
		Set<DeclaredVersions.VersionDrift> entries = new TreeSet<>();
		Set<DeclaredVersions.DeclarationDrift> declarationEntries = new TreeSet<>();
		for (UpgradeCandidate member : members) {
			versions.addAll(member.getDeclaredVersions().versions());
			entries.addAll(member.getDeclaredVersions().entries());
			declarationEntries.addAll(member.getDeclaredVersions().declarationEntries());
		}

		return new DeclaredVersions(versions, entries, declarationEntries);
	}

	private static Releases intersectReleases(List<UpgradeCandidate> members) {

		List<Release> common = members.getFirst().getUpdateCandidate().getReleases().stream()
				.filter(release -> members.stream().allMatch(
						member -> member.getUpdateCandidate().getReleases().getRelease(release.version()) != null))
				.toList();

		return Releases.of(common);
	}

	/**
	 * Return the member candidates collapsed into this group.
	 *
	 * @return the members in row order.
	 */
	List<UpgradeCandidate> getMembers() {
		return members;
	}

	/**
	 * Return the compact member label used beside the group row label.
	 *
	 * @return the member label or the member count if the artifact ids do not share
	 * a concise display prefix.
	 */
	String getMemberLabel() {

		List<String> artifactIds = members.stream().map(m -> m.getArtifactId().artifactId()).toList();
		List<String> parts = basePrefixLabelParts(artifactIds);
		if (parts.isEmpty()) {
			parts = commonSeparatorPrefixLabelParts(artifactIds);
		}

		String label = String.join(", ", parts);
		return !label.isEmpty() && label.length() <= MEMBER_LABEL_LIMIT ? label : String.valueOf(members.size());
	}

	private static List<String> basePrefixLabelParts(List<String> artifactIds) {

		List<String> bases = new ArrayList<>(artifactIds);
		bases.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));

		for (String base : bases) {
			List<String> parts = basePrefixLabelParts(artifactIds, base);
			if (!parts.isEmpty()) {
				return parts;
			}
		}

		return List.of();
	}

	private static List<String> basePrefixLabelParts(List<String> artifactIds, String base) {

		List<String> suffixes = new ArrayList<>(artifactIds.size() - 1);
		boolean hasBase = false;
		for (String artifactId : artifactIds) {
			if (artifactId.equals(base)) {
				hasBase = true;
				continue;
			}

			String suffix = suffixAfterBase(artifactId, base);
			if (suffix.isEmpty()) {
				return List.of();
			}
			suffixes.add(suffix);
		}

		if (!hasBase) {
			return List.of();
		}

		Collections.sort(suffixes);

		List<String> parts = new ArrayList<>(artifactIds.size());
		parts.add(base);
		parts.addAll(suffixes);
		return parts;
	}

	private static String suffixAfterBase(String artifactId, String base) {

		if (!artifactId.startsWith(base) || artifactId.length() <= base.length()) {
			return "";
		}

		char separator = artifactId.charAt(base.length());
		if (separator != '-' && separator != '.') {
			return "";
		}

		return artifactId.substring(base.length() + 1);
	}

	private static List<String> commonSeparatorPrefixLabelParts(List<String> artifactIds) {

		String lcp = longestCommonPrefix(artifactIds);
		int separator = Math.max(lcp.lastIndexOf('-'), lcp.lastIndexOf('.'));
		if (separator < 0) {
			return List.of();
		}

		String prefix = lcp.substring(0, separator + 1);
		List<String> suffixes = artifactIds.stream()
				.map(id -> id.substring(prefix.length()))
				.filter(s -> !s.isEmpty())
				.sorted()
				.toList();

		return suffixes.size() == artifactIds.size() ? suffixes : List.of();
	}

	private static String longestCommonPrefix(List<String> strings) {

		String first = strings.getFirst();
		int len = first.length();
		for (String s : strings) {
			len = Math.min(len, s.length());
			for (int i = 0; i < len; i++) {
				if (first.charAt(i) != s.charAt(i)) {
					len = i;
					break;
				}
			}
		}
		return first.substring(0, len);
	}

	@Override
	DependencySiteQuery toQuery() {
		return DependencySiteQuery.union(members.stream().map(UpgradeCandidate::toQuery).toList());
	}

	/**
	 * Fan the confirmed target out to one update per member coordinate. Member
	 * updates carry the group's dependency name through their shared rule.
	 */
	@Override
	public List<DependencyUpdate> createUpdates(ArtifactVersion target) {

		List<DependencyUpdate> updates = new ArrayList<>(members.size());
		for (UpgradeCandidate member : members) {
			updates.addAll(member.createUpdates(target));
		}

		return updates;
	}

}
