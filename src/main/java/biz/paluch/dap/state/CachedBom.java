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

package biz.paluch.dap.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageSystem;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

/**
 * Persistent Bill of Materials membership for one BOM version
 *
 * @author Mark Paluch
 */
@Tag("bom")
public class CachedBom {

	private @Nullable @Attribute String version;

	private final @XCollection(propertyElementName = "members", elementName = "member", style = XCollection.Style.v2) List<CachedBomMember> members = new ArrayList<>();

	/**
	 * Create an empty membership entry for XML deserialization.
	 */
	public CachedBom() {
	}

	public CachedBom(@Nullable String version) {
		this.version = version;
	}

	/**
	 * Create a membership entry for the given BOM version and members.
	 *
	 * @param version the BOM version string the membership is scoped to.
	 * @param members the managed members keyed by artifact coordinates.
	 * @return the membership entry; guaranteed to be not {@literal null}.
	 */
	public static CachedBom from(String version, Map<ArtifactId, ArtifactVersion> members) {

		CachedBom membership = new CachedBom(version);
		members.forEach((artifactId, memberVersion) -> membership.members
				.add(new CachedBomMember(artifactId.groupId(), artifactId.artifactId(), memberVersion.toString())));
		return membership;
	}

	/**
	 * Return the BOM version string this membership is scoped to.
	 */
	public @Nullable String getVersion() {
		return version;
	}

	/**
	 * Return the backing member entries.
	 * <p>This is the live storage list used for persistence.
	 */
	public List<CachedBomMember> getMembers() {
		return members;
	}

	/**
	 * Convert the member entries into a domain member map, skipping entries whose
	 * version no longer parses.
	 *
	 * @return the managed members keyed by artifact coordinates; guaranteed to be
	 * not {@literal null}.
	 */
	public Map<ArtifactId, ArtifactVersion> toMembers() {

		Map<ArtifactId, ArtifactVersion> membersMap = new LinkedHashMap<>();
		for (CachedBomMember member : members) {

			Optional<ArtifactVersion> version = ArtifactVersion.from(member.getVersion());
			version.ifPresent(memberVersion -> membersMap
					.put(member.toArtifactId(), memberVersion));
		}

		return membersMap;
	}

	/**
	 * Return a deep copy for persistence snapshots.
	 */
	CachedBom snapshot() {

		CachedBom copy = new CachedBom(version);
		for (CachedBomMember member : members) {
			copy.members.add(new CachedBomMember(member.getGroupId(), member.getArtifactId(), member.getVersion()));
		}
		return copy;
	}

	@Override
	public String toString() {
		return "CachedBomMembership[%s, %d members]".formatted(version, members.size());
	}

	/**
	 * Persistent member coordinates with the managed version.
	 */
	@Tag("member")
	public static class CachedBomMember extends CachedArtifactSupport {

		private @Nullable @Attribute String groupId;

		private @Nullable @Attribute String artifactId;

		/**
		 * Package ecosystem this artifact belongs to, or {@literal null} for entries
		 * persisted before ecosystem tracking. Persisted so a cache-only scan can build
		 * the correct vulnerability query without re-reading the build files.
		 */
		private @Nullable @Attribute PackageSystem packageSystem;


		private @Nullable @Attribute String version;

		/**
		 * Create an empty member entry for XML deserialization.
		 */
		public CachedBomMember() {
		}

		CachedBomMember(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
			this.groupId = groupId;
			this.artifactId = artifactId;
			this.version = version;
		}

		@Override
		public @Nullable String getGroupId() {
			return groupId;
		}

		@Override
		public @Nullable String getArtifactId() {
			return artifactId;
		}

		@Override
		public @Nullable PackageSystem getPackageSystem() {
			return packageSystem;
		}

		public @Nullable String getVersion() {
			return version;
		}

	}

}
