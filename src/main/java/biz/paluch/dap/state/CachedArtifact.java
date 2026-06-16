/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a snapshot of the License at
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.util.StringUtils;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

/**
 * Persistent cache entry for a single artifact and its known releases.
 * <p>
 * The entry is keyed by group and artifact identifiers and stores releases
 * in a serializer-friendly representation.
 *
 * @author Mark Paluch
 */
@Tag("artifact")
public class CachedArtifact {

	private @Attribute String groupId;

	private @Attribute String artifactId;

	private @Nullable @Attribute String preferredSource;

	/**
	 * Number of consecutive lookups that returned no releases at all, reset to
	 * {@code 0} once any source produces. Drives the empty-lookup warmup before
	 * back-off engages.
	 */
	private @Attribute int emptyLookups;

	/**
	 * Epoch-millisecond timestamp at which this artifact's source set was last
	 * fully re-checked (a fetch that queried every configured source, skipping
	 * none). Advanced only on such full queries and never reset on success, so it
	 * also serves as the periodic re-check clock for known-empty sources on an
	 * otherwise productive artifact.
	 */
	private @Attribute long sourcesCheckedSince;

	/**
	 * Comma-separated identifiers of release sources that yielded no result for
	 * this artifact, or {@literal null} if every queried source returned releases.
	 * Used to detect when a newly added source warrants a fetch despite
	 * empty-lookup back-off. This is the persisted form;
	 * {@link #getEmptyReleaseSources()} exposes the parsed view.
	 */
	private @Nullable @Attribute String emptyReleaseSources;

	/**
	 * Parsed, immutable view of {@link #emptyReleaseSources}.
	 */
	@Transient
	private volatile @Nullable Set<String> emptyReleaseSourceIds;

	/**
	 * Epoch-millisecond timestamp of the last write to this entry, or {@code 0} if
	 * the entry pre-dates expiry tracking and should never be expired.
	 */
	@Attribute
	private long lastSeen = 0L;

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
	 * <p>
	 * This is the live storage list used for persistence.
	 *
	 * @return the mutable backing release entries.
	 */
	public List<CachedRelease> getReleases() {
		return releases;
	}

	public long getLastSeen() {
		return lastSeen;
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

	public @Nullable String getPreferredSource() {
		return preferredSource;
	}

	public void setPreferredSource(@Nullable String preferredSource) {
		this.preferredSource = preferredSource;
	}

	public int getEmptyLookups() {
		return emptyLookups;
	}

	public long getSourcesCheckedSince() {
		return sourcesCheckedSince;
	}

	/**
	 * Return the identifiers of release sources known to yield no result for this
	 * artifact.
	 *
	 * @return an immutable set of the known-empty release source identifiers; never
	 * {@literal null} and empty when every queried source returned releases.
	 */
	@Transient
	public Set<String> getEmptyReleaseSources() {

		Set<String> ids = this.emptyReleaseSourceIds;
		if (ids == null) {
			ids = parseEmptyReleaseSources();
			this.emptyReleaseSourceIds = ids;
		}
		return ids;
	}

	private Set<String> parseEmptyReleaseSources() {

		if (!StringUtils.hasText(emptyReleaseSources)) {
			return Set.of();
		}

		Set<String> ids = new LinkedHashSet<>();
		for (String id : emptyReleaseSources.split(",")) {
			if (StringUtils.hasText(id)) {
				ids.add(id.trim());
			}
		}
		return Set.copyOf(ids);
	}

	/**
	 * Hard replace of cached releases.
	 *
	 * @param releases the collection of releases to store.
	 * @param timestamp current timestamp for expiry tracking.
	 */
	public void setCachedReleases(Collection<CachedRelease> releases, long timestamp) {
		this.releases.clear();
		this.releases.addAll(releases);
		this.lastSeen = timestamp;
	}

	/**
	 * Update the cached releases with the given fetched releases.
	 * @param fetchedReleases the fetched releases.
	 * @param timestamp current timestamp for expiry tracking.
	 */
	public void updateCachedReleases(FetchedReleases fetchedReleases, long timestamp) {

		setCachedReleases(fetchedReleases.getReleases(), timestamp);
		setPreferredSource(fetchedReleases.getPreferredSource());

		Collection<String> emptySources = fetchedReleases.getEmptySources();
		if (fetchedReleases.isFullFetch()) {
			this.emptyReleaseSources = emptySources.isEmpty() ? null : String.join(",", emptySources);
			this.emptyReleaseSourceIds = Set.copyOf(emptySources);
			this.sourcesCheckedSince = timestamp;
		} else {
			Set<String> merged = new LinkedHashSet<>(getEmptyReleaseSources());
			merged.addAll(emptySources);
			this.emptyReleaseSources = merged.isEmpty() ? null : String.join(",", merged);
			this.emptyReleaseSourceIds = Set.copyOf(merged);
		}

		this.emptyLookups = releases.isEmpty() ? Math.min(this.emptyLookups + 1, 999) : 0;
	}

	/**
	 * Return the artifact coordinates represented by this cache entry.
	 *
	 * @return the artifact identifier.
	 */
	public ArtifactId toArtifactId() {
		return ArtifactId.of(getGroupId(), getArtifactId());
	}

	public CachedArtifact snapshot() {
		CachedArtifact copy = new CachedArtifact(groupId, artifactId);
		copy.lastSeen = lastSeen;
		copy.releases.addAll(releases);
		copy.preferredSource = preferredSource;
		copy.emptyLookups = emptyLookups;
		copy.sourcesCheckedSince = sourcesCheckedSince;
		copy.emptyReleaseSources = emptyReleaseSources;
		return copy;
	}

	@Override
	public String toString() {
		return groupId + ":" + artifactId + ", Release count: " + releases.size();
	}
}
