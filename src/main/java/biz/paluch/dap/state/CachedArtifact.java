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
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Release;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;

/**
 * Persistent cache entry for a single artifact and its known releases.
 * <p>The entry is keyed by group and artifact identifiers and stores releases
 * in a serializer-friendly representation.
 *
 * @author Mark Paluch
 */
@Tag("artifact")
public class CachedArtifact {

	private @Attribute String groupId;

	private @Attribute String artifactId;

	private final @XCollection(propertyElementName = "releases", elementName = "release", style = XCollection.Style.v2) List<CachedRelease> releases = new ArrayList<>();

	/**
	 * Create an empty cache entry for XML deserialization.
	 */
	public CachedArtifact() {
	}

	/**
	 * Create a cache entry for the given coordinates.
	 *
	 * @param groupId the artifact group identifier.
	 * @param artifactId the artifact identifier.
	 */
	public CachedArtifact(String groupId, String artifactId) {
		this.groupId = groupId;
		this.artifactId = artifactId;
	}

	/**
	 * Create a cache entry for the given artifact identifier.
	 *
	 * @param artifactId the artifact coordinates.
	 */
	public CachedArtifact(ArtifactId artifactId) {
		this(artifactId.groupId(), artifactId.artifactId());
	}

	/**
	 * Return the cached group identifier.
	 *
	 * @return the group identifier.
	 */
	public String getGroupId() {
		return groupId;
	}

	/**
	 * Return the cached artifact identifier.
	 *
	 * @return the artifact identifier.
	 */
	public String getArtifactId() {
		return artifactId;
	}

	/**
	 * Return the backing release entries.
	 * <p>This is the live storage list used for persistence.
	 *
	 * @return the mutable backing release entries.
	 */
	public List<CachedRelease> getReleases() {
		return releases;
	}

	/**
	 * Return whether this cache entry refers to the given artifact.
	 *
	 * @param artifactId the artifact to compare with.
	 * @return {@literal true} if both group and artifact identifiers match.
	 */
	public boolean matches(ArtifactId artifactId) {
		return getArtifactId().equals(artifactId.artifactId()) && getGroupId().equals(artifactId.groupId());
	}

	/**
	 * Return this entry's releases as domain {@link Release} objects.
	 *
	 * @return a newly created list of releases.
	 */
	@Transient
	public List<Release> getVersionOptions() {

		List<Release> options = new ArrayList<>();
		for (CachedRelease release : releases) {
			options.add(release.toRelease());
		}
		return options;
	}

	/**
	 * Replace the cached releases of this entry.
	 *
	 * @param releases the releases to store.
	 */
	public void setVersionOptions(List<Release> releases) {
		this.releases.clear();
		for (Release release : releases) {
			this.releases.add(CachedRelease.from(release));
		}
	}

	/**
	 * Replace the cached releases of this entry with already-converted entries,
	 * preserving any source-specific metadata such as the commit SHA stored by the
	 * GitHub release source.
	 *
	 * @param releases the cached release entries to store.
	 */
	public void replaceCachedReleases(List<CachedRelease> releases) {
		this.releases.clear();
		this.releases.addAll(releases);
	}

	/**
	 * Return the artifact coordinates represented by this cache entry.
	 *
	 * @return the artifact identifier.
	 */
	public ArtifactId toArtifactId() {
		return ArtifactId.of(getGroupId(), getArtifactId());
	}

}
