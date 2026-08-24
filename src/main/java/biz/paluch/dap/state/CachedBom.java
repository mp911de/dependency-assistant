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

package biz.paluch.dap.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.util.StringUtils;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

/**
 * Persistent Bill of Materials membership for one BOM version.
 *
 * <p>Members are stored grouped by group identifier and managed version rather
 * than one element per member, because a BOM typically manages many artifacts
 * of the same group at a single version. A group omits its version when the
 * members are managed at the BOM version itself, which is the common case. The
 * grouped form is produced by {@link #snapshot()} and expanded again on read,
 * so callers never see it.
 *
 * <p>Documents written before grouping stored one member per element carrying
 * its own {@code artifactId}. Such elements still read correctly and are
 * rewritten in grouped form on the next snapshot.
 *
 * @author Mark Paluch
 */
@Tag("bom")
public class CachedBom {

	@Attribute(converter = ArtifactVersionConverter.class)
	private ArtifactVersion version;

	private final @XCollection(propertyElementName = "members", elementName = "member", style = XCollection.Style.v2) List<CachedBomMembers> members = new ArrayList<>();

	/**
	 * Create an empty membership entry for XML deserialization.
	 */
	public CachedBom() {
	}

	public CachedBom(ArtifactVersion version) {
		this.version = version;
	}

	/**
	 * Create a membership entry for the given BOM version and members.
	 *
	 * @param version the BOM version the membership is scoped to.
	 * @param members the managed members keyed by artifact coordinates.
	 * @return the membership entry.
	 */
	public static CachedBom from(ArtifactVersion version, Map<ArtifactId, ArtifactVersion> members) {

		CachedBom membership = new CachedBom(version);
		membership.group(members);
		return membership;
	}

	/**
	 * Return the BOM version this membership is scoped to.
	 *
	 * @return the BOM version.
	 */
	public ArtifactVersion getVersion() {
		return version;
	}

	/**
	 * Return whether the given artifact is listed as a member of this membership,
	 * regardless of version.
	 * @param artifactId the member coordinates to look up.
	 * @return {@code true} if a member entry matches the coordinates.
	 */
	public boolean isMember(ArtifactId artifactId) {

		for (CachedBomMembers group : members) {
			if (artifactId.groupId().equals(group.getGroupId())
					&& group.getArtifactIds().contains(artifactId.artifactId())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Expand the stored groups into a domain member map, skipping entries whose
	 * coordinates or version no longer parse.
	 *
	 * @return the managed members keyed by artifact coordinates.
	 */
	public Map<ArtifactId, ArtifactVersion> toMembers() {

		Map<ArtifactId, ArtifactVersion> membersMap = new LinkedHashMap<>();
		for (CachedBomMembers group : members) {

			String groupId = group.getGroupId();
			ArtifactVersion memberVersion = group.getVersion() != null ? group.getVersion() : version;
			if (!StringUtils.hasText(groupId) || memberVersion == null) {
				continue;
			}

			for (String artifactId : group.getArtifactIds()) {
				membersMap.put(ArtifactId.of(groupId, artifactId), memberVersion);
			}
		}
		return membersMap;
	}

	/**
	 * Return a copy in grouped form for persistence snapshots.
	 * <p>Grouping runs off the {@link #toMembers() expanded} view rather than the
	 * stored groups, so re-snapshotting an entry that was itself read from a
	 * snapshot preserves every member.
	 */
	CachedBom snapshot() {

		CachedBom copy = new CachedBom(version);
		copy.group(toMembers());
		return copy;
	}

	/**
	 * Replace the stored groups with the given members, grouped by group identifier
	 * and managed version. Groups, and the identifiers within them, are ordered so
	 * that equal memberships serialize to an identical document.
	 */
	private void group(Map<ArtifactId, ArtifactVersion> memberVersions) {

		Map<String, Map<ArtifactVersion, List<String>>> grouped = new TreeMap<>();
		memberVersions.forEach((artifactId, memberVersion) -> grouped
				.computeIfAbsent(artifactId.groupId(), groupId -> new TreeMap<>())
				.computeIfAbsent(memberVersion, key -> new ArrayList<>())
				.add(artifactId.artifactId()));

		members.clear();
		grouped.forEach((groupId, byVersion) -> byVersion.forEach((memberVersion, artifactIds) -> {
			artifactIds.sort(Comparator.naturalOrder());
			members.add(new CachedBomMembers(groupId, memberVersion.equals(version) ? null : memberVersion,
					artifactIds));
		}));
	}

	@Override
	public String toString() {
		return "CachedBomMembership[%s, %d members]".formatted(version, toMembers().size());
	}

	public void loadState() {
		for (CachedBomMembers member : members) {
			if (member.artifacts.isEmpty()) {

			}
		}
	}

	/**
	 * Persistent coordinates of the BOM members that share one group identifier and
	 * one managed version.
	 */
	@Tag("member")
	public static class CachedBomMembers {

		private @Attribute String groupId;

		/**
		 * Managed version of every artifact in this group, or {@literal null} when the
		 * members are managed at the BOM version itself.
		 */
		@Attribute(converter = ArtifactVersionConverter.class)
		private @Nullable ArtifactVersion version;

		/**
		 * Grouped artifact identifiers. Not {@code final} because the platform replaces
		 * the value through the converter on load.
		 */
		@Attribute(converter = ArtifactIdsConverter.class)
		private List<String> artifacts = new ArrayList<>();

		/**
		 * Single artifact identifier as written before members were grouped. Read for
		 * backward compatibility and never written.
		 */
		private @Nullable @Attribute String artifactId;

		/**
		 * Create an empty group for XML deserialization.
		 */
		public CachedBomMembers() {
		}

		CachedBomMembers(String groupId, @Nullable ArtifactVersion version, List<String> artifacts) {
			this.groupId = groupId;
			this.version = version;
			this.artifacts = new ArrayList<>(artifacts);
		}

		public @Nullable String getGroupId() {
			return groupId;
		}

		/**
		 * Return the managed version shared by this group, or {@literal null} when the
		 * group inherits the BOM version.
		 *
		 * @return the shared managed version, or {@literal null} when inherited.
		 */
		public @Nullable ArtifactVersion getVersion() {
			return version;
		}

		/**
		 * Return the artifact identifiers in this group, falling back to the
		 * single-member form used by documents written before grouping.
		 *
		 * @return the grouped artifact identifiers.
		 */
		public List<String> getArtifactIds() {
			return artifacts.isEmpty() && StringUtils.hasText(artifactId) ? List.of(artifactId) : artifacts;
		}

	}

}
