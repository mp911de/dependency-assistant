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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.diagnostic.Logger;
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
public class CachedArtifact implements ArtifactId {

	private static final Logger LOG = Logger.getInstance(CachedArtifact.class);

	private @Attribute String groupId;

	private @Attribute String artifactId;

	private @Nullable @Attribute String preferredSource;

	/**
	 * Package ecosystem this artifact belongs to, or {@literal null} for entries
	 * persisted before ecosystem tracking. Persisted so a cache-only scan can build
	 * the correct vulnerability query without re-reading the build files.
	 */
	private @Nullable @Attribute PackageSystem packageSystem;

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

	@Override
	@Transient
	public String groupId() {
		return getGroupId();
	}

	/**
	 * Return the cached artifact identifier.
	 *
	 * @return the artifact identifier.
	 */
	public String getArtifactId() {
		return artifactId;
	}

	@Override
	@Transient
	public String artifactId() {
		return getArtifactId();
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
	 * Return whether this entry matches the given coordinates and ecosystem. A
	 * {@literal null} ecosystem on either side is treated as a wildcard so entries
	 * persisted before ecosystem tracking still match.
	 *
	 * @param artifactId the artifact to compare with.
	 * @param packageSystem the ecosystem to compare; may be {@literal null}.
	 * @return {@literal true} if the entry matches; {@literal false} otherwise.
	 */
	public boolean matches(ArtifactId artifactId, @Nullable PackageSystem packageSystem) {
		PackageSystem ecosystem = getEcosystem();
		return matches(artifactId) && (ecosystem == null || packageSystem == null || ecosystem == packageSystem);
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
			try {
				options.add(release.toRelease());
			} catch (RuntimeException e) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Failed to parse release '%s:%s': '%s'".formatted(getGroupId(), getArtifactId(), release),
							e);
				}
			}
		}
		return options;
	}

	public @Nullable String getPreferredSource() {
		return preferredSource;
	}

	public void setPreferredSource(@Nullable String preferredSource) {
		this.preferredSource = preferredSource;
	}

	/**
	 * Return the package ecosystem this artifact belongs to.
	 *
	 * @return the ecosystem, or {@literal null} when not yet known.
	 */
	public @Nullable PackageSystem getEcosystem() {
		return packageSystem;
	}

	/**
	 * Record the package ecosystem this artifact belongs to.
	 *
	 * @param packageSystem the ecosystem to store; may be {@literal null}.
	 */
	public void setEcosystem(@Nullable PackageSystem packageSystem) {
		this.packageSystem = packageSystem;
	}

	public int getEmptyLookups() {
		return emptyLookups;
	}

	public long getSourcesCheckedSince() {
		return sourcesCheckedSince;
	}

	public @Nullable CachedRelease getCachedRelease(ArtifactVersion version) {
		String versionString = version.toString();
		for (CachedRelease release : getReleases()) {
			if (versionString.equals(release.version())) {
				return release;
			}
		}
		return null;
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
		updateCachedReleases(fetchedReleases, timestamp, (release, cached) -> {
		});
	}

	/**
	 * Merge the given fetched releases into the cached releases, preserving the
	 * stored vulnerabilities of releases already known so a metadata refresh never
	 * discards a scan, and notifying {@code onNewRelease} for each release that was
	 * not previously cached.
	 *
	 * @param fetchedReleases the fetched releases.
	 * @param timestamp current timestamp for expiry tracking.
	 * @param onNewRelease invoked once per newly added release; must not be
	 * {@literal null}.
	 */
	public void updateCachedReleases(FetchedReleases fetchedReleases, long timestamp,
			BiConsumer<Release, CachedRelease> onNewRelease) {

		updateReleases(fetchedReleases, onNewRelease);
		this.lastSeen = timestamp;
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

	private void updateReleases(FetchedReleases fetched, BiConsumer<Release, CachedRelease> onNewConsumer) {

		Set<String> known = new HashSet<>();
		for (CachedRelease existing : releases) {
			known.add(existing.version());
		}

		fetched.forEach((release, cached) -> {
			if (known.add(cached.version())) {
				releases.add(cached);
				onNewConsumer.accept(release, cached);
			}
		});
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
		for (CachedRelease release : releases) {
			copy.releases.add(release.snapshot());
		}
		copy.preferredSource = preferredSource;
		copy.emptyLookups = emptyLookups;
		copy.sourcesCheckedSince = sourcesCheckedSince;
		copy.emptyReleaseSources = emptyReleaseSources;
		copy.packageSystem = packageSystem;
		return copy;
	}

	@Override
	public String toString() {
		return groupId + ":" + artifactId + ", Release count: " + releases.size();
	}

}
