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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.checker.Vulnerability;
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
public class CachedArtifact extends CachedArtifactSupport implements ArtifactId {

	private static final Logger LOG = Logger.getInstance(CachedArtifact.class);

	private @Nullable @Attribute String groupId;

	private @Nullable @Attribute String artifactId;

	/**
	 * Package ecosystem this artifact belongs to, or {@literal null} for entries
	 * persisted before ecosystem tracking. Persisted so a cache-only scan can build
	 * the correct vulnerability query without re-reading the build files.
	 */
	private @Nullable @Attribute PackageSystem packageSystem;

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

	private final @XCollection(propertyElementName = "boms", elementName = "bom", style = XCollection.Style.v2) List<CachedBom> boms = new ArrayList<>();

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
	public CachedArtifact(@Nullable String groupId, @Nullable String artifactId) {
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
	@Override
	public @Nullable String getGroupId() {
		return this.groupId;
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
	@Override
	public @Nullable String getArtifactId() {
		return this.artifactId;
	}

	@Override
	@Transient
	public String artifactId() {
		return getArtifactId();
	}

	@Override
	public @Nullable PackageSystem getPackageSystem() {
		return this.packageSystem;
	}

	public void setPackageSystem(@Nullable PackageSystem packageSystem) {
		this.packageSystem = packageSystem;
	}

	/**
	 * Return the backing release entries.
	 * <p>
	 * This is the live storage list used for persistence.
	 *
	 * @return the mutable backing release entries.
	 */
	public List<CachedRelease> getReleases() {
		synchronized (releases) {
			return List.copyOf(releases);
		}
	}

	/**
	 * Return whether this entry has any cached releases.
	 */
	public boolean hasReleases() {
		synchronized (releases) {
			return !releases.isEmpty();
		}
	}

	public long getLastSeen() {
		return lastSeen;
	}

	/**
	 * Return whether this entry has any cached Bill of Materials membership.
	 */
	public boolean hasBoms() {
		synchronized (boms) {
			return !boms.isEmpty();
		}
	}

	/**
	 * Return whether a Bill of Materials membership is cached for the given BOM
	 * version.
	 */
	public boolean hasBom(ArtifactVersion version) {
		return hasBom(version.toString());
	}

	/**
	 * Return whether a Bill of Materials membership is cached for the given BOM
	 * version.
	 */
	public boolean hasBom(String version) {
		return getBomMembership(version) != null;
	}

	/**
	 * Return the managed members cached for the given BOM version.
	 *
	 * @return the members keyed by artifact coordinates; empty when no membership
	 * is cached for the version.
	 */
	public Map<ArtifactId, ArtifactVersion> getBom(ArtifactVersion version) {
		return getBom(version.toString());
	}

	/**
	 * Return the managed members cached for the given BOM version string.
	 *
	 * @return the members keyed by artifact coordinates; empty when no membership
	 * is cached for the version.
	 */
	public Map<ArtifactId, ArtifactVersion> getBom(String version) {
		CachedBom membership = getBomMembership(version);
		return membership != null ? membership.toMembers() : Map.of();
	}

	/**
	 * Return the Bill of Materials membership for the given BOM version.
	 *
	 * @param version the BOM version string.
	 * @return the membership, or {@literal null} if no membership is cached for the
	 * version.
	 */
	public @Nullable CachedBom getBomMembership(String version) {

		synchronized (boms) {
			for (CachedBom membership : boms) {
				if (version.equals(membership.getVersion())) {
					return membership;
				}
			}
		}
		return null;
	}

	/**
	 * Cache the given Bill of Materials membership. Released BOM contents are
	 * immutable, so an already-cached version is left unchanged.
	 */
	public void setBillOfMaterials(BillOfMaterials bom) {

		String version = bom.getVersion().toString();
		synchronized (boms) {

			if (getBomMembership(version) == null) {
				boms.add(CachedBom.from(version, bom.getMembers()));
				boms.sort(Comparator.comparing(it -> ArtifactVersion.of(it.getVersion())));
			}
		}
	}

	/**
	 * Predict the managed members of a BOM version that has no cached membership,
	 * based on the release-train heuristic: a member of the nearest cached
	 * membership whose group id matches this BOM's group id (equal or a subgroup)
	 * and whose managed version equals that membership's BOM version is assumed to
	 * follow the BOM's release train, so its pin for {@code version} is predicted
	 * as {@code version} itself. Members with independent versioning are omitted
	 * rather than guessed.
	 *
	 * @param version the BOM version to predict members for.
	 * @return the predicted members keyed by artifact coordinates; empty when no
	 * membership is cached at all or no member follows the release train.
	 */
	public Map<ArtifactId, ArtifactVersion> predictBom(ArtifactVersion version) {

		String bomGroupId = getGroupId();
		if (bomGroupId == null) {
			return Map.of();
		}

		CachedBom reference = getNearestBomMembership(version);
		if (reference == null) {
			return Map.of();
		}

		Map<ArtifactId, ArtifactVersion> predicted = new LinkedHashMap<>();
		for (CachedBom.CachedBomMember member : reference.getMembers()) {

			String memberGroupId = member.getGroupId();
			if (memberGroupId == null || member.getArtifactId() == null) {
				continue;
			}

			boolean releaseTrainVersion = Objects.equals(member.getVersion(), reference.getVersion());
			boolean groupAffinity = memberGroupId.equals(bomGroupId)
					|| memberGroupId.startsWith(bomGroupId + ".");
			if (releaseTrainVersion && groupAffinity) {
				predicted.put(member.toArtifactId(), version);
			}
		}
		return predicted;
	}

	/**
	 * Return the cached membership closest to the given version: the highest cached
	 * version at or below it, or the lowest cached one when all cached memberships
	 * are newer.
	 */
	private @Nullable CachedBom getNearestBomMembership(ArtifactVersion version) {

		synchronized (boms) {

			CachedBom nearest = null;
			for (CachedBom membership : boms) {

				String membershipVersion = membership.getVersion();
				if (membershipVersion == null) {
					continue;
				}

				if (nearest == null || !ArtifactVersion.of(membershipVersion).isNewer(version)) {
					nearest = membership;
				}
			}
			return nearest;
		}
	}

	/**
	 * Return this entry's releases as domain {@link Release} objects.
	 *
	 * @return a newly created list of releases.
	 */
	@Transient
	public List<Release> getVersionOptions() {

		List<Release> options = new ArrayList<>();
		synchronized (releases) {
			for (CachedRelease release : releases) {
				try {
					options.add(release.toRelease());
				} catch (RuntimeException e) {
					if (LOG.isDebugEnabled()) {
						LOG.debug(
								"Failed to parse release '%s:%s': '%s'".formatted(getGroupId(), getArtifactId(),
										release),
								e);
					}
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

	public int getEmptyLookups() {
		return emptyLookups;
	}

	public long getSourcesCheckedSince() {
		return sourcesCheckedSince;
	}

	public @Nullable CachedRelease getCachedRelease(ArtifactVersion version) {
		String versionString = version.toString();
		synchronized (releases) {
			for (CachedRelease release : releases) {
				if (versionString.equals(release.version())) {
					return release;
				}
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

	public void addRelease(CachedRelease release) {
		addReleases(release);
	}

	public void addReleases(CachedRelease... releases) {
		addReleases(List.of(releases));
	}

	public void addReleases(Collection<CachedRelease> releases) {
		synchronized (this.releases) {
			this.releases.addAll(releases);
		}
	}

	/**
	 * Hard replace of cached releases.
	 *
	 * @param releases the collection of releases to store.
	 * @param timestamp current timestamp for expiry tracking.
	 */
	public void setCachedReleases(Collection<CachedRelease> releases, long timestamp) {
		synchronized (this.releases) {
			this.releases.clear();
			this.releases.addAll(releases);
			this.lastSeen = timestamp;
		}
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
	 * @param onNewRelease invoked once per newly added release .
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

		synchronized (releases) {
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
	}

	/**
	 * Record one completed scan attempt that returned no data for this release,
	 */
	public void recordAttempt(ArtifactVersion version) {
		CachedRelease cachedRelease = getCachedRelease(version);
		if (cachedRelease != null) {
			cachedRelease.recordAttempt();
		}
	}

	/**
	 * Store the vulnerabilities found by a completed scan, stamping the scan time.
	 *
	 * @param scannedAt the time the scan completed.
	 * @param vulnerabilities the vulnerabilities found, possibly empty for a clean
	 * scan.
	 */
	public void recordVulnerabilities(long scannedAt, ArtifactVersion version,
			Iterable<Vulnerability> vulnerabilities) {
		CachedRelease cachedRelease = getCachedRelease(version);
		if (cachedRelease != null) {
			cachedRelease.setVulnerabilities(scannedAt, vulnerabilities);
		}
	}

	public CachedArtifact snapshot() {
		CachedArtifact copy = new CachedArtifact(getGroupId(), getArtifactId());
		copy.setPackageSystem(getPackageSystem());
		copy.lastSeen = lastSeen;
		synchronized (releases) {
			for (CachedRelease release : releases) {
				copy.releases.add(release.snapshot());
			}
		}
		synchronized (boms) {
			for (CachedBom membership : boms) {
				copy.boms.add(membership.snapshot());
			}
		}
		copy.preferredSource = preferredSource;
		copy.emptyLookups = emptyLookups;
		copy.sourcesCheckedSince = sourcesCheckedSince;
		copy.emptyReleaseSources = emptyReleaseSources;

		return copy;
	}

	@Override
	public String toString() {
		return getGroupId() + ":" + getArtifactId() + ", Release count: " + releases.size();
	}

}
