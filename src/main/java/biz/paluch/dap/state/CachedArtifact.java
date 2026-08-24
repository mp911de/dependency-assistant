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

import java.util.*;
import java.util.function.BiConsumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionedPackage;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

/**
 * Persistent cache entry for one package and its resolved metadata.
 *
 * <p>The entry stores releases and their vulnerability scans, BOM
 * classification and memberships, project metadata, and release-source back-off
 * state. Package-system absence is retained for entries written before
 * ecosystem tracking and is interpreted by {@link Cache} as a legacy wildcard.
 *
 * <p>Release and BOM collections are synchronized independently. Snapshot and
 * collection accessors provide stable views for serialization and background
 * processing without exposing the backing lists.
 *
 * @author Mark Paluch
 */
@Tag("artifact")
public class CachedArtifact extends CachedArtifactSupport implements ArtifactId {

	private static final Logger LOG = Logger.getInstance(CachedArtifact.class);

	private @Attribute String groupId;

	private @Attribute String artifactId;

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
	 * empty-lookup back-off. This is the persisted form.
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

	/**
	 * Whether this artifact was classified as a Bill of Materials import by a scan.
	 * Persisted separately from the memberships so a BOM whose contents could not
	 * be resolved is still recognized as one.
	 */
	private @Attribute boolean bom;

	private final @XCollection(propertyElementName = "releases", elementName = "release", style = XCollection.Style.v2) List<CachedRelease> releases = new ArrayList<>();

	private final @XCollection(propertyElementName = "boms", elementName = "bom", style = XCollection.Style.v2) List<CachedBom> boms = new ArrayList<>();

	@Transient
	private final Map<ArtifactVersion, BillOfMaterials> bomIndex = new TreeMap<>();

	/**
	 * Project metadata captured for this artifact, or {@literal null} if the
	 * artifact was never inspected.
	 */
	private @Nullable @Property(surroundWithTag = false) CachedMetadata projectMetadata;

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

	/**
	 * Return {@literal true} if the artifactId contains {@link #groupId()} and
	 * {@link #artifactId()} values.
	 *
	 * @return {@code true} if both coordinate components contain text.
	 */
	public boolean hasCoordinates() {
		return StringUtils.hasText(getGroupId()) && StringUtils.hasText(getArtifactId());
	}

	@Override
	public @Nullable PackageSystem getPackageSystem() {
		return this.packageSystem;
	}

	public void setPackageSystem(@Nullable PackageSystem packageSystem) {
		this.packageSystem = packageSystem;
	}

	/**
	 * Return the source that previously produced the preferred release result.
	 *
	 * @return the preferred release source identifier, or {@literal null} if none
	 * is recorded.
	 */
	public @Nullable String getPreferredSource() {
		return preferredSource;
	}

	public void setPreferredSource(@Nullable String preferredSource) {
		this.preferredSource = preferredSource;
	}

	/**
	 * Return the number of consecutive fetches that produced no releases.
	 *
	 * @return the consecutive empty-fetch count.
	 */
	public int getEmptyLookups() {
		return emptyLookups;
	}

	/**
	 * Return when every configured release source was last queried.
	 *
	 * @return the epoch-millisecond full-fetch timestamp, or {@code 0} before the
	 * first full fetch.
	 */
	public long getSourcesCheckedSince() {
		return sourcesCheckedSince;
	}

	/**
	 * Return when this entry was last written.
	 *
	 * @return the epoch-millisecond write timestamp, or {@code 0} for a legacy
	 * entry that must not be expired by age.
	 */
	public long getLastSeen() {
		return lastSeen;
	}

	/**
	 * Return the identifiers of release sources known to yield no result for this
	 * artifact.
	 *
	 * @return an immutable set of the known-empty release source identifiers. The
	 * set is empty when every queried source returned releases.
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
	 * Rebuild the BOM index from the persisted membership entries, discarding any
	 * indexed predictions.
	 */
	public void reindexBoms() {
		synchronized (boms) {
			bomIndex.clear();
			ensureBomIndexed();
		}
	}

	private void ensureBomIndexed() {
		if (!boms.isEmpty() && bomIndex.isEmpty()) {
			boms.forEach(this::index);
		}
	}

	private void index(CachedBom bom) {

		BillOfMaterials billOfMaterials = BillOfMaterials
				.from(VersionedPackage.of(toPackageIdentity(), bom.getVersion()), bom.toMembers());
		bomIndex.put(bom.getVersion(), billOfMaterials);
	}

	/**
	 * Return whether this artifact was classified as a Bill of Materials by any
	 * scan, independently of whether a membership is cached for any version.
	 * <p>Entries persisted before BOM classification report {@literal true} once a
	 * membership is present, so previously cached BOMs keep their classification.
	 *
	 * @return {@code true} if the artifact is known to be a BOM.
	 */
	public boolean isBom() {
		if (bom) {
			return true;
		}
		synchronized (boms) {
			return !boms.isEmpty();
		}
	}

	/**
	 * Return whether a Bill of Materials membership is persisted for the given BOM
	 * version. Predictions indexed by {@link #predictBom} do not count, so a
	 * heuristic guess never suppresses resolving and caching the real membership.
	 *
	 * @param version the BOM version to check.
	 * @return {@code true} if a membership is persisted for the version.
	 */
	public boolean hasBom(ArtifactVersion version) {
		synchronized (boms) {
			for (CachedBom membership : boms) {
				if (membership.getVersion().equals(version)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Return the Bill of Materials indexed for the given version: a persisted
	 * membership or, in its absence, a previously indexed prediction.
	 *
	 * @param version the BOM version to look up.
	 * @return the Bill of Materials, or {@literal null} when neither a membership
	 * nor a prediction is indexed for the version.
	 */
	public @Nullable BillOfMaterials getBom(ArtifactVersion version) {
		synchronized (boms) {
			ensureBomIndexed();
			return bomIndex.get(version);
		}
	}

	/**
	 * Return the cached Bill of Materials memberships of this artifact, ordered by
	 * ascending BOM version.
	 *
	 * @return an immutable snapshot of the memberships. The snapshot is empty when
	 * this artifact carries no membership.
	 */
	public List<CachedBom> getBomMemberships() {
		synchronized (boms) {
			return boms.stream().sorted(Comparator.comparing(CachedBom::getVersion)).toList();
		}
	}

	/**
	 * Mark this artifact as a Bill of Materials, stamp it as seen, and cache the
	 * given membership.
	 * <p>Released BOM contents are immutable, so an already-cached version is left
	 * unchanged. A Bill of Materials with no members marks the artifact without
	 * caching a membership: the empty member set would otherwise be served for that
	 * version for good, since memberships never expire by age.
	 *
	 * @param bom the Bill of Materials to cache.
	 * @param timestamp the current epoch-millisecond timestamp.
	 */
	public void setBillOfMaterials(BillOfMaterials bom, long timestamp) {

		this.bom = true;
		this.lastSeen = timestamp;

		if (bom.getMembers().isEmpty() || hasBom(bom.getVersion())) {
			return;
		}

		synchronized (boms) {
			ensureBomIndexed();
			boms.add(CachedBom.from(bom.getVersion(), bom.getMembers()));
			index(boms.getLast());
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
	 * <p>A non-empty prediction is indexed for subsequent {@link #getBom} lookups
	 * until the real membership is cached or the index is rebuilt.
	 *
	 * @param version the BOM version to predict members for.
	 * @return the predicted Bill of Materials. The result is empty when no
	 * membership is cached or no member follows the release train.
	 */
	public BillOfMaterials predictBom(ArtifactVersion version) {

		String bomGroupId = getGroupId();
		PackageIdentity pkg = toPackageIdentity();
		if (bomGroupId == null) {
			return BillOfMaterials.of(pkg, version, Map.of());
		}

		CachedBom reference = getNearestBomMembership(version);
		if (reference == null) {
			return BillOfMaterials.of(pkg, version, Map.of());
		}

		Map<ArtifactId, ArtifactVersion> predicted = new LinkedHashMap<>();
		reference.toMembers().forEach((member, memberVersion) -> {

			String memberGroupId = member.groupId();
			boolean releaseTrainVersion = Objects.equals(memberVersion, reference.getVersion());
			boolean groupAffinity = memberGroupId.equals(bomGroupId)
					|| memberGroupId.startsWith(bomGroupId + ".");
			if (releaseTrainVersion && groupAffinity) {
				predicted.put(member, version);
			}
		});

		BillOfMaterials prediction = BillOfMaterials.of(pkg, version, predicted);
		synchronized (boms) {
			ensureBomIndexed();
			bomIndex.put(version, prediction);
		}

		return prediction;
	}

	/**
	 * Return the cached membership closest to the given version assuming that BOM
	 * composition remains generally stable throughout several versions.
	 */
	private @Nullable CachedBom getNearestBomMembership(ArtifactVersion version) {

		List<CachedBom> memberships = getBomMemberships();
		if (memberships.isEmpty()) {
			return null;
		}

		CachedBom nearest = memberships.getFirst();
		for (CachedBom membership : memberships) {
			if (membership.getVersion().isNewer(version)) {
				break;
			}
			nearest = membership;
		}
		return nearest;
	}

	/**
	 * Return the release entries in their serialized form.
	 *
	 * @return an immutable snapshot of the release entries.
	 */
	@Transient
	public List<CachedRelease> getCachedReleases() {
		synchronized (releases) {
			return List.copyOf(releases);
		}
	}

	/**
	 * Return whether this entry has any cached releases.
	 *
	 * @return {@code true} if at least one release is cached.
	 */
	public boolean hasReleases() {
		synchronized (releases) {
			return !releases.isEmpty();
		}
	}

	/**
	 * Find a cached release that compares equal to the given version.
	 *
	 * @param version the version to match.
	 * @return the matching cached release, or {@literal null} if none compares
	 * equal.
	 */
	public @Nullable CachedRelease getCachedRelease(ArtifactVersion version) {
		synchronized (releases) {
			for (CachedRelease release : releases) {
				if (version.compareTo(release.version()) == 0) {
					return release;
				}
			}
		}
		return null;
	}

	/**
	 * Return releases as {@link Release} objects.
	 *
	 * <p>Malformed persisted releases are omitted rather than failing the entire
	 * lookup.
	 *
	 * @return a newly created list of parseable releases.
	 */
	@Transient
	public List<Release> getReleases() {

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

	/**
	 * Append one release without de-duplicating existing entries.
	 *
	 * @param release the release to append.
	 * @see #addReleases(Collection)
	 */
	public void addRelease(CachedRelease release) {
		addReleases(release);
	}

	/**
	 * Append releases without de-duplicating existing entries.
	 *
	 * @param releases the releases to append.
	 * @see #addReleases(Collection)
	 */
	public void addReleases(CachedRelease... releases) {
		addReleases(List.of(releases));
	}

	/**
	 * Append releases without de-duplicating existing entries.
	 *
	 * @param releases the releases to append.
	 */
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
	public void updateReleases(FetchedReleases fetchedReleases, long timestamp) {
		updateReleases(fetchedReleases, timestamp, (release, cached) -> {
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
	public void updateReleases(FetchedReleases fetchedReleases, long timestamp,
			BiConsumer<Release, CachedRelease> onNewRelease) {

		updateReleases(fetchedReleases, onNewRelease);
		this.lastSeen = timestamp;
		setPreferredSource(fetchedReleases.getPreferredSource());

		CachedMetadata metadata = fetchedReleases.getProjectMetadata();
		if (metadata != null) {
			this.projectMetadata = metadata;
			this.projectMetadata.setRetrievedAt(timestamp);
		}

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
			Set<ArtifactVersion> known = new TreeSet<>();
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

	public @Transient @Nullable String getProjectName() {
		return projectMetadata != null ? projectMetadata.getProjectName() : null;
	}

	/**
	 * Return the project metadata captured for this artifact.
	 *
	 * @return the project metadata, or {@literal null} if the artifact was never
	 * inspected.
	 */
	public @Nullable CachedMetadata getProjectMetadata() {
		return projectMetadata;
	}

	/**
	 * Store the given inspection result as this artifact's project metadata,
	 * stamping the metadata's retrieval time and this entry's last-seen clock.
	 * <p>The metadata replaces any previously stored element wholesale: a fresh
	 * inspection is the new truth, including the nothing-found marker. The given
	 * instance is retained and stamped in place.
	 *
	 * @param metadata the inspection result to store.
	 * @param timestamp current timestamp for retrieval and expiry tracking.
	 */
	public void updateProjectMetadata(CachedMetadata metadata, long timestamp) {

		metadata.setRetrievedAt(timestamp);
		this.projectMetadata = metadata;
		this.lastSeen = timestamp;
	}

	/**
	 * Record one completed scan attempt that returned no data for this release.
	 *
	 * @param version the release version whose attempt counter should advance.
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
	 * @param version the release version whose result should be stored.
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

	/**
	 * Return a snapshot of this entry for persistence.
	 *
	 * <p>Release and BOM entries are copied. Project metadata is retained as a
	 * shared value because cache writes replace it wholesale rather than mutating
	 * its descriptive fields.
	 *
	 * @return a snapshot detached from the mutable release and BOM collections.
	 */
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

			copy.boms.sort(Comparator.comparing(CachedBom::getVersion));
		}
		copy.projectMetadata = projectMetadata;
		copy.preferredSource = preferredSource;
		copy.bom = bom;
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
