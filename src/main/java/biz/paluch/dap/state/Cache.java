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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSources;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionedPackage;
import biz.paluch.dap.checker.Vulnerabilities;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

/**
 * Persistent cache for artifact, repository, and project metadata.
 *
 * <p>Artifact entries retain releases and vulnerability scans, BOM membership,
 * project metadata, and release-source back-off state. Project entries retain
 * property-to-artifact correlations. Repository entries retain discovered Git
 * tags.
 *
 * <p>The cache owns its mutable entries and maintains transient lookup indexes
 * over the XML-serializable lists. {@link #reindex()} restores those indexes
 * after deserialization. Collection accessors return snapshots, although the
 * entries within those snapshots remain live unless documented otherwise.
 *
 * @author Mark Paluch
 */
@Tag("cache")
public class Cache implements ModificationTracker {

	private static final Duration CACHE_EXPIRATION = Duration.ofHours(8);

	private static final long STALE_THRESHOLD_MILLIS = Duration.ofDays(30).toMillis();

	/**
	 * Number of fetch attempts before considering a cached artifact to be
	 * constantly absent meaning that its release source does not provide any
	 * releases. In that case, we pause for {@link #STALE_THRESHOLD_MILLIS} before
	 * attempting to fetch again.
	 */
	private static final int EMPTY_THRESHOLD = 3;

	/**
	 * Duration for which {@link #doNotNag()} suppresses refresh reminders.
	 */
	public static final Duration PLEASE_BE_SILENT_FOR = Duration.ofHours(12);

	/**
	 * Maximum cache age before {@link #shouldNag()} may request a refresh.
	 */
	public static final Duration LAST_TIME_CACHE_WAS_UPDATED = Duration.ofDays(7);

	@Transient
	private final Clock clock;

	@Transient
	private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();

	@Attribute
	private volatile long lastUpdateTimestamp = 0L;

	@Attribute
	private volatile long doNotNagUntil = 0L;

	private final @XCollection(propertyElementName = "artifacts", elementName = "artifact", style = XCollection.Style.v2) List<CachedArtifact> artifacts = new ArrayList<>();

	@Transient
	private final Map<ArtifactId, CachedArtifact> artifactsById = new HashMap<>();

	@Transient
	private final Map<PackageIdentity, CachedArtifact> artifactsByPackageIdentity = new HashMap<>();

	private final @Tag @XCollection(propertyElementName = "projects", elementName = "project", style = XCollection.Style.v2) List<ProjectCache> projects = new ArrayList<>();

	private final @XCollection(propertyElementName = "repositories", elementName = "repository", style = XCollection.Style.v2) List<CachedRepository> repositories = new ArrayList<>();

	@Transient
	private final Map<String, CachedRepository> repositoriesByKey = new HashMap<>();

	/**
	 * Create a new {@code Cache} using the current UTC clock for XML
	 * deserialization.
	 */
	public Cache() {
		this(Clock.systemUTC());
	}

	/**
	 * Create a new {@code Cache} given a {@link Clock}.
	 * @param clock the clock to use.
	 */
	public Cache(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Return the clock shared with time-based cache policies.
	 *
	 * @return the cache clock.
	 */
	public Clock getClock() {
		return this.clock;
	}

	/**
	 * Return the current epoch milliseconds from this cache's clock.
	 *
	 * @return the current time in epoch milliseconds.
	 */
	public long now() {
		return clock.millis();
	}

	@Override
	public long getModificationCount() {
		return modificationTracker.getModificationCount();
	}

	/**
	 * Advance the persistence modification count for state stored alongside the
	 * cache.
	 */
	public void incrementModification() {
		this.modificationTracker.incModificationCount();
	}

	/**
	 * Record a successful cache update, stamping the current time from this cache's
	 * {@link Clock}.
	 */
	public void recordUpdate() {
		this.lastUpdateTimestamp = clock.millis();
		this.modificationTracker.incModificationCount();
	}

	/**
	 * Return the {@link Instant} of the last recorded cache update.
	 *
	 * @return the last-update instant, or {@literal null} if no update has been
	 * applied yet.
	 */
	public @Nullable Instant getLastUpdate() {
		long timestamp = lastUpdateTimestamp;
		return timestamp == 0L ? null : Instant.ofEpochMilli(timestamp);
	}

	/**
	 * Return whether the user should be asked to refresh because release metadata
	 * is unavailable or outdated.
	 *
	 * @return {@literal true} when the cache should nag the user.
	 */
	public boolean shouldNag() {

		Duration age = getAge();
		Instant lastUpdate = getLastUpdate();
		if (age != null && lastUpdate != null && age.compareTo(LAST_TIME_CACHE_WAS_UPDATED) > 0) {
			return doNotNagUntil == 0 || doNotNagUntil < clock.millis();
		}
		return false;
	}

	/**
	 * Silence the nag automatism for {@code 12} hours.
	 */
	public void doNotNag() {
		doNotNagUntil = clock.instant().plus(PLEASE_BE_SILENT_FOR).toEpochMilli();
		modificationTracker.incModificationCount();
	}

	/**
	 * Rebuild the lookup indexes from the persisted entries, e.g. after state
	 * initialization repopulated the backing lists without passing through the
	 * mutator methods.
	 */
	public void reindex() {
		synchronized (this.artifacts) {
			artifactsById.clear();
			artifactsByPackageIdentity.clear();
			ensureIndexed();
		}
		synchronized (this.repositories) {
			repositoriesByKey.clear();
			ensureRepositoriesIndexed();
		}
	}

	private void ensureIndexed() {
		if (artifactsById.isEmpty() && artifactsByPackageIdentity.isEmpty() && !artifacts.isEmpty()) {
			for (CachedArtifact artifact : artifacts) {
				index(artifact);
			}
		}
	}

	/**
	 * Register the given entry in the lookup indexes. First-registered entries win
	 * so index lookups return the same artifact as the former first-match list
	 * scan. Must be called under the {@code artifacts} monitor.
	 */
	private void index(CachedArtifact artifact) {

		if (!artifact.hasCoordinates()) {
			return;
		}

		artifact.reindexBoms();
		artifactsById.putIfAbsent(artifact.toArtifactId(), artifact);
		if (artifact.getPackageSystem() != null) {
			artifactsByPackageIdentity.putIfAbsent(artifact.toPackageIdentity(), artifact);
		}
	}

	/**
	 * Append cached artifact entries to this cache.
	 * <p>Existing entries are not de-duplicated.
	 *
	 * @param artifacts the artifact entries to append.
	 */
	public void addArtifacts(CachedArtifact... artifacts) {
		addArtifacts(List.of(artifacts));
	}

	/**
	 * Append cached artifact entries to this cache.
	 * <p>Existing entries are not de-duplicated.
	 *
	 * @param artifacts the artifact entries to append.
	 */
	public void addArtifacts(Collection<CachedArtifact> artifacts) {
		writeArtifacts(() -> {
			for (CachedArtifact artifact : artifacts) {
				this.artifacts.add(artifact);
				index(artifact);
			}
		});
	}

	/**
	 * Update the cached releases for the given artifact and their last seen
	 * timestamps.
	 * <p>If no cache entry exists yet, one is created first.
	 *
	 * @param artifactId the artifact whose releases should be stored.
	 * @param releases the releases to cache.
	 */
	public void updateReleases(ArtifactId artifactId, Iterable<? extends Release> releases) {
		writeArtifacts(() -> {
			CachedArtifact artifactToUse = getOrCreate(findCachedArtifact(artifactId), null, artifactId);
			artifactToUse.setCachedReleases(FetchedReleases.convert(releases), clock.millis());
		});
	}

	/**
	 * Update the cached releases using the given {@link FetchedReleases}, notifying
	 * {@code onNewRelease} for each release added that was not previously cached.
	 *
	 * @param releases the fetched releases.
	 * @param packageSystem the ecosystem the fetched artifact belongs to. The value
	 * is stored on a freshly created entry.
	 * @param onNewRelease invoked once per newly cached release .
	 */
	public void updateReleases(FetchedReleases releases, PackageSystem packageSystem,
			BiConsumer<Release, CachedRelease> onNewRelease) {

		ArtifactId artifactId = releases.getArtifactId();
		CachedArtifact cachedArtifact = findCachedArtifact(artifactId, packageSystem);
		writeArtifacts(() -> {
			CachedArtifact artifactToUse = getOrCreate(cachedArtifact, packageSystem, artifactId);
			artifactToUse.updateReleases(releases, now(), onNewRelease);
		});
	}

	/**
	 * Return cached releases for the given artifact.
	 *
	 * <p>This overload uses only the artifact coordinates. The package system does
	 * not participate in release lookup.
	 *
	 * @param pkg the artifact to look up.
	 * @return the cached releases for the artifact, or an empty result if no entry
	 * is present.
	 * @see #getReleases(ArtifactId)
	 */
	@Transient
	public Releases getReleases(PackageIdentity pkg) {
		return getReleases(pkg.getArtifactId());
	}

	/**
	 * Return cached releases for the given artifact.
	 *
	 * @param artifactId the artifact to look up.
	 * @return the cached releases for the artifact, or an empty result if no entry
	 * is present.
	 */
	@Transient
	public Releases getReleases(ArtifactId artifactId) {
		return getReleases(artifactId, false);
	}

	/**
	 * Return cached releases for the given artifact.
	 * <p>If {@code ensureRecent} is {@literal true}, stale cache content is treated
	 * as absent and the result is an empty list once the cache age exceeds the
	 * configured expiration window. The method does not trigger a refresh.
	 *
	 * @param artifactId the artifact to look up.
	 * @param ensureRecent whether stale cache content should be ignored.
	 * @return the cached releases for the artifact, or an empty result if no entry
	 * is present or the cache is considered stale.
	 */
	@Transient
	public Releases getReleases(ArtifactId artifactId, boolean ensureRecent) {

		if (ensureRecent) {
			Duration age = getAge();

			if (age == null || age.compareTo(CACHE_EXPIRATION) > 0) {
				return Releases.empty();
			}
		}

		ArtifactId artifactIdToUse = artifactId instanceof GitArtifactId gid ? gid.releaseSource() : artifactId;

		CachedArtifact cachedArtifact = findCachedArtifact(artifactIdToUse);
		if (cachedArtifact != null) {
			return Releases.of(cachedArtifact.getReleases());
		}

		return Releases.empty();
	}

	/**
	 * Record the given Bill of Materials, marking its artifact as a BOM.
	 * <p>A Bill of Materials with no members marks the artifact without caching a
	 * membership, so an unresolvable BOM stays resolvable later.
	 *
	 * @param bom the Bill of Materials to record. The value carries its ecosystem.
	 * @see CachedArtifact#setBillOfMaterials(BillOfMaterials, long)
	 */
	public void putBillOfMaterials(BillOfMaterials bom) {

		ArtifactId artifactId = bom.getArtifactId();
		PackageSystem packageSystem = bom.getPackageSystem();
		CachedArtifact cachedArtifact = findCachedArtifact(bom.getPackageIdentity());
		writeArtifacts(() -> getOrCreate(cachedArtifact, packageSystem, artifactId)
				.setBillOfMaterials(bom, now()));
	}

	/**
	 * Return the cached Bill of Materials for the given BOM identity and version.
	 * <p>The lookup is ecosystem-aware, matching
	 * {@link #putBillOfMaterials(BillOfMaterials)}. Released BOM contents are
	 * immutable, so entries never expire by age. The containing artifact's
	 * last-seen eviction bounds their lifetime.
	 *
	 * @param bom the BOM identity and version to look up.
	 * @return the indexed Bill of Materials, a cached membership, or a prediction
	 * from {@link CachedArtifact#predictBom}. Returns {@literal null} if none is
	 * indexed for the version.
	 */
	@Transient
	public @Nullable BillOfMaterials getBillOfMaterials(VersionedPackage bom) {
		CachedArtifact cachedArtifact = findCachedArtifact(bom.getPackageIdentity());
		return cachedArtifact == null ? null : cachedArtifact.getBom(bom.getVersion());
	}

	/**
	 * Return a snapshot of the known project cache entries.
	 *
	 * @return an immutable snapshot of the current project entries.
	 */
	public List<ProjectCache> getProjects() {
		synchronized (projects) {
			return List.copyOf(projects);
		}
	}

	/**
	 * Return the cache entry for the given project identity.
	 * <p>If no entry exists yet, a new one is created, stored, and returned.
	 * Callers use the returned entry to record project state, so this access counts
	 * as a cache modification.
	 *
	 * @param identity the project identity.
	 * @return the existing or newly created project cache entry.
	 */
	public ProjectCache getProject(ProjectId identity) {

		synchronized (projects) {
			for (ProjectCache project : projects) {

				if (project == null) {
					continue;
				}
				if (project.matches(identity)) {
					return project;
				}
			}

			ProjectCache projectCache = new ProjectCache(identity);
			modificationTracker.incModificationCount();
			projects.add(projectCache);
			projects.sort(ProjectCache.COMPARATOR);
			return projectCache;
		}
	}

	/**
	 * Remove the cache entry for the given project identity.
	 * <p>Removing an entry counts as a cache modification. Ignoring unknown
	 * {@link ProjectId id}.
	 *
	 * @param identity the project identity to remove.
	 */
	public void removeProject(ProjectId identity) {

		synchronized (projects) {
			boolean removed = projects.removeIf(project -> project != null && project.matches(identity));
			if (removed) {
				modificationTracker.incModificationCount();
			}
		}
	}

	/**
	 * Find the first project property with the given name that satisfies the
	 * supplied filter.
	 *
	 * @param propertyName the property name to locate.
	 * @param filter the conditional that must accept the matching property.
	 * @return the first matching project property, or {@literal null} if none
	 * matches.
	 */
	public @Nullable ProjectProperty findProperty(String propertyName, Predicate<VersionProperty> filter) {

		synchronized (projects) {
			for (ProjectCache project : projects) {
				if (project == null) {
					continue;
				}

				VersionProperty property = project.getProperty(propertyName);
				if (property == null || !filter.test(property)) {
					continue;
				}

				return new ProjectProperty(project.getId(), property);
			}
		}

		return null;
	}

	/**
	 * Return a snapshot of the cached artifact entries.
	 * <p>The list is a copy taken under the artifacts lock. The entries themselves
	 * are live instances. The background scan walks this snapshot to read each
	 * artifact's persisted {@link CachedArtifact#getPackageSystem() ecosystem} and
	 * cached releases so it can build the correct vulnerability query from the
	 * cache alone.
	 *
	 * @return an immutable snapshot of the current artifact entries.
	 */
	public List<CachedArtifact> getCachedArtifacts() {
		return readArtifacts(() -> List.copyOf(artifacts));
	}

	/**
	 * Return the raw {@link CachedRelease} entries for the given artifact.
	 * <p>Unlike {@link #getReleases(ArtifactId, boolean)}, this variant returns the
	 * serialized form including optional extended attributes such as the commit SHA
	 * stored by the GitHub integration.
	 *
	 * @param artifactId the artifact to look up.
	 * @return the cached release entries, or an empty list if no entry is present.
	 */
	public List<CachedRelease> getCachedReleases(ArtifactId artifactId) {
		CachedArtifact cachedArtifact = findCachedArtifact(artifactId);
		return cachedArtifact != null ? cachedArtifact.getCachedReleases() : Collections.emptyList();
	}

	/**
	 * Return the three-state vulnerabilities for the given artifact version.
	 * <p>The result is absent when the version is unknown or has no vulnerability
	 * scan. A scanned version with no vulnerabilities is clean, one with
	 * vulnerabilities is vulnerable.
	 *
	 * @param artifactId the artifact to look up.
	 * @param version the exact version whose scan is requested.
	 * @return the vulnerability scan.
	 */
	@Transient
	public Vulnerabilities getVulnerabilities(ArtifactId artifactId, ArtifactVersion version) {
		CachedArtifact artifact = findCachedArtifact(artifactId);
		if (artifact == null) {
			return Vulnerabilities.absent();
		}
		CachedRelease cachedRelease = artifact.getCachedRelease(version);
		if (cachedRelease == null) {
			return Vulnerabilities.absent();
		}

		return cachedRelease.toVulnerabilities();
	}

	/**
	 * Find a cached artifact by coordinates and ecosystem.
	 * <p>An entry without a recorded ecosystem acts as a wildcard so entries
	 * persisted before ecosystem tracking still match.
	 *
	 * @param artifactId the artifact to look up.
	 * @param packageSystem the artifact ecosystem, or {@literal null} to match any
	 * ecosystem.
	 * @return the cached artifact or {@literal null} if none found.
	 */
	public @Nullable CachedArtifact findCachedArtifact(ArtifactId artifactId, @Nullable PackageSystem packageSystem) {
		if (packageSystem == null) {
			return findCachedArtifact(artifactId);
		}
		return findCachedArtifact(PackageIdentity.of(artifactId.detach(), packageSystem));
	}

	/**
	 * Find a cached artifact by coordinates, regardless of its package ecosystem.
	 * @param artifactId the artifact to look up.
	 * @return the cached artifact or {@literal null} if none found.
	 */
	public @Nullable CachedArtifact findCachedArtifact(ArtifactId artifactId) {
		return readArtifacts(() -> {
			ensureIndexed();
			return artifactsById.get(artifactId.detach());
		});
	}

	/**
	 * Find a cached artifact.
	 * @param pkg the package identity to look up.
	 * @return the cached artifact or {@literal null} if none found.
	 */
	public @Nullable CachedArtifact findCachedArtifact(PackageIdentity pkg) {
		return readArtifacts(() -> {
			ensureIndexed();

			ArtifactId artifactId = pkg.getArtifactId().detach();
			CachedArtifact artifact = artifactsByPackageIdentity.get(PackageIdentity.of(artifactId,
					pkg.getPackageSystem()));
			if (artifact != null) {
				return artifact;
			}

			artifact = artifactsById.get(artifactId);
			return artifact != null && artifact.getPackageSystem() == null ? artifact : null;
		});
	}

	/**
	 * Return whether project metadata should be inspected for the given artifact.
	 *
	 * <p>An existing entry without metadata requires inspection. A nothing-found
	 * marker is retried until the attempt threshold is reached. Timestamped
	 * metadata is retried after the stale interval.
	 *
	 * @param cachedArtifact the artifact entry, or {@literal null} when no entry
	 * exists.
	 * @return {@code true} if project metadata should be inspected.
	 */
	public boolean requiresMetadataRefresh(@Nullable CachedArtifact cachedArtifact) {

		if (cachedArtifact == null) {
			return false;
		}

		CachedMetadata metadata = cachedArtifact.getProjectMetadata();
		if (metadata == null) {
			return true;
		}

		if (metadata.getRetrievedAt() > EMPTY_THRESHOLD) {
			long staleThreshold = clock.millis() - STALE_THRESHOLD_MILLIS;
			if (staleThreshold > metadata.getRetrievedAt()) {
				return true;
			}
		} else if (metadata.getRetrievedAt() < EMPTY_THRESHOLD) {
			return true;
		}

		return false;
	}

	/**
	 * Decide how the given artifact should be fetched from its release sources,
	 * given empty-lookup back-off to avoid constantly re-querying a source assumed
	 * to remain empty.
	 *
	 * @param sources the release sources configured for the artifact.
	 * @return the fetch plan to apply.
	 */
	public FetchPlan createFetchPlan(ReleaseSources sources) {

		CachedArtifact cached = findCachedArtifact(sources.pkg());

		if (cached == null) {
			return FetchPlan.fullFetch();
		}

		String preferred = preferredSourceIn(cached, sources.sourceIds());
		long staleThreshold = clock.millis() - STALE_THRESHOLD_MILLIS;
		Set<String> knownEmpty = cached.getEmptyReleaseSources();
		boolean isAllKnownEmpty = sources.containsOnlyReleaseSourceIds(knownEmpty);

		if (!cached.getCachedReleases().isEmpty()) {
			if (knownEmpty.isEmpty() || staleThreshold > cached.getSourcesCheckedSince()
					|| isAllKnownEmpty) {
				return FetchPlan.fetch(true, preferred, Set.of());
			}
			return FetchPlan.fetch(false, preferred, knownEmpty);
		}

		if (cached.getEmptyLookups() <= EMPTY_THRESHOLD || staleThreshold > cached.getSourcesCheckedSince()) {
			return FetchPlan.fetch(true, preferred, Set.of());
		}

		if (isAllKnownEmpty) {
			return FetchPlan.skip();
		}

		return FetchPlan.partial(preferred, knownEmpty);
	}

	private static @Nullable String preferredSourceIn(CachedArtifact artifact, Collection<String> currentSources) {

		String preferred = artifact.getPreferredSource();
		return preferred != null && currentSources.contains(preferred) ? preferred : null;
	}

	/**
	 * Return whether this cache contains any cached release entries.
	 * <p>Artifact entries carrying only Bill of Materials membership do not count.
	 * The release store is considered empty until a release fetch produced results.
	 *
	 * @return {@literal true} if at least one artifact entry has cached releases.
	 */
	public boolean hasReleases() {
		return readArtifacts(() -> {
			for (CachedArtifact artifact : artifacts) {
				if (artifact.hasReleases()) {
					return true;
				}
			}
			return false;
		});
	}

	/**
	 * Return whether this cache contains any known projects.
	 *
	 * @return {@literal true} if at least one project entry is present.
	 */
	public boolean hasDependencies() {
		return readArtifacts(() -> {
			return !projects.isEmpty();
		});
	}

	/**
	 * Return the age of the cache relative to the last recorded update.
	 *
	 * @return the duration since {@link #recordUpdate()} was last applied, or
	 * {@literal null} if no update has been applied yet.
	 */
	public @Nullable Duration getAge() {
		Instant lastUpdate = getLastUpdate();
		if (lastUpdate == null) {
			return null;
		}
		return Duration.between(lastUpdate, clock.instant());
	}

	/**
	 * Return a deep snapshot of this cache safe for serialization while concurrent
	 * mutations may still be in progress.
	 *
	 * @return a snapshot suitable for serialization.
	 */
	Cache snapshot() {

		Cache copy = new Cache();
		copy.lastUpdateTimestamp = this.lastUpdateTimestamp;
		copy.doNotNagUntil = this.doNotNagUntil;
		long threshold = clock.millis() - STALE_THRESHOLD_MILLIS;

		Comparator<CachedArtifact> artifactComparator = Comparator
				.comparing(CachedArtifact::getPackageSystem, Comparator.nullsFirst(Enum::compareTo))
				.thenComparing(CachedArtifact::groupId)
				.thenComparing(CachedArtifact::artifactId);

		Comparator<ProjectCache> projectCacheComparator = Comparator
				.comparing(ProjectCache::getGroupId, Comparator.nullsFirst(String::compareTo))
				.thenComparing(ProjectCache::getArtifactId, Comparator.nullsFirst(String::compareTo))
				.thenComparing(ProjectCache::getDescriptor, Comparator.nullsFirst(String::compareTo));

		synchronized (artifacts) {
			for (CachedArtifact artifact : artifacts) {
				if (artifact.getLastSeen() > 0 && artifact.getLastSeen() < threshold) {
					continue;
				}
				copy.artifacts.add(artifact.snapshot());
			}
		}

		copy.artifacts.sort(artifactComparator);

		synchronized (projects) {
			for (ProjectCache project : projects) {
				if (project == null) {
					continue;
				}
				if (project.getLastSeen() > 0 && project.getLastSeen() < threshold) {
					continue;
				}
				copy.projects.add(project.snapshot());
			}
		}

		copy.projects.sort(projectCacheComparator);

		synchronized (repositories) {
			for (CachedRepository repository : repositories) {
				if (repository.getLastSeen() > 0 && repository.getLastSeen() < threshold) {
					continue;
				}
				copy.repositories.add(repository.snapshot());
			}
		}

		copy.repositories.sort(Comparator.comparing(CachedRepository::getKey));

		return copy;
	}

	/**
	 * Invoke the given consumer for a known artifact to this cache. Iteration is
	 * based on the actual artifact. Consumers typically mutate the entry, so a
	 * successful lookup counts as a cache modification.
	 *
	 * @param artifactId the artifact to look up.
	 * @param consumer the consumer to invoke.
	 */
	public void doWithArtifact(ArtifactId artifactId, Consumer<CachedArtifact> consumer) {
		CachedArtifact cachedArtifact = findCachedArtifact(artifactId);
		if (cachedArtifact != null) {
			consumer.accept(cachedArtifact);
			modificationTracker.incModificationCount();
		}
	}

	/**
	 * Invoke the given consumer for a known artifact to this cache. Iteration is
	 * based on the actual artifact. Consumers typically mutate the entry, so a
	 * successful lookup counts as a cache modification.
	 *
	 * @param pkg the package identity to look up.
	 * @param consumer the consumer to invoke.
	 */
	public void doWithArtifact(PackageIdentity pkg, Consumer<CachedArtifact> consumer) {
		CachedArtifact cachedArtifact = findCachedArtifact(pkg);
		if (cachedArtifact != null) {
			consumer.accept(cachedArtifact);
			modificationTracker.incModificationCount();
		}
	}

	private CachedArtifact getOrCreate(@Nullable CachedArtifact cachedArtifact, @Nullable PackageSystem packageSystem,
			ArtifactId artifactId) {
		CachedArtifact artifactToUse = cachedArtifact;
		if (artifactToUse == null) {
			artifactToUse = createArtifact(packageSystem, artifactId);
		}
		postProcessArtifact(packageSystem, artifactToUse);
		return artifactToUse;
	}

	private void postProcessArtifact(@Nullable PackageSystem packageSystem, CachedArtifact artifactToUse) {
		if (packageSystem != null && artifactToUse.getPackageSystem() != packageSystem) {
			artifactToUse.setPackageSystem(packageSystem);
			index(artifactToUse);
		}
	}

	private CachedArtifact createArtifact(@Nullable PackageSystem packageSystem, ArtifactId artifactId) {
		CachedArtifact artifactToUse;
		artifactToUse = new CachedArtifact(artifactId);
		artifactToUse.setPackageSystem(packageSystem);
		artifacts.add(artifactToUse);
		index(artifactToUse);
		return artifactToUse;
	}

	/**
	 * Find a cached repository by its key.
	 * @param key the repository key.
	 * @return the cached repository or {@literal null} if none found.
	 */
	public @Nullable CachedRepository findRepository(String key) {
		return readRepositories(() -> {
			ensureRepositoriesIndexed();
			return repositoriesByKey.get(key);
		});
	}

	/**
	 * Create the repository entry for the given key, or update the URL of the
	 * existing entry. Timestamps stay untouched: a fresh entry carries
	 * {@code lastSeen} zero until a scan writes to it.
	 * @param key the repository key.
	 * @param url the browsable repository URL.
	 * @return the created or updated repository entry.
	 */
	public CachedRepository createOrUpdateRepository(String key, String url) {
		return writeRepositories(() -> {

			ensureRepositoriesIndexed();
			CachedRepository repository = repositoriesByKey.get(key);
			if (repository == null) {
				repository = new CachedRepository(key, url);
				repositories.add(repository);
				repositoriesByKey.put(key, repository);
			} else {
				repository.setUrl(url);
			}

			return repository;
		});
	}

	/**
	 * Return a snapshot of all cached repository entries.
	 * @return an immutable snapshot of the repository entries.
	 */
	public List<CachedRepository> getRepositories() {
		return readRepositories(() -> List.copyOf(repositories));
	}

	/**
	 * Invoke the given consumer for a known repository of this cache. Consumers
	 * typically mutate the entry, so a successful lookup counts as a cache
	 * modification.
	 * @param key the repository key to look up.
	 * @param consumer the consumer to invoke.
	 */
	public void doWithRepository(String key, Consumer<CachedRepository> consumer) {

		CachedRepository repository = findRepository(key);
		if (repository != null) {
			consumer.accept(repository);
			modificationTracker.incModificationCount();
		}
	}

	/**
	 * Build the key index from the repository entries. Required before any index
	 * access because deserialization populates {@link #repositories} without
	 * passing through the mutator methods. Must be called under the
	 * {@code repositories} monitor.
	 */
	private void ensureRepositoriesIndexed() {
		if (repositoriesByKey.isEmpty() && !repositories.isEmpty()) {
			for (CachedRepository repository : repositories) {
				repositoriesByKey.putIfAbsent(repository.getKey(), repository);
			}
		}
	}

	private <T extends @Nullable Object> T readRepositories(Supplier<T> action) {
		synchronized (this.repositories) {
			return action.get();
		}
	}

	private <T extends @Nullable Object> T writeRepositories(Supplier<T> action) {
		synchronized (this.repositories) {
			this.modificationTracker.incModificationCount();
			return action.get();
		}
	}

	private <T extends @Nullable Object> T readArtifacts(Supplier<T> action) {
		synchronized (this.artifacts) {
			return action.get();
		}
	}

	private <T extends @Nullable Object> T writeArtifacts(Supplier<T> action) {
		synchronized (this.artifacts) {
			this.modificationTracker.incModificationCount();
			return action.get();
		}
	}

	private void writeArtifacts(Runnable action) {
		writeArtifacts(() -> {
			action.run();
			return null;
		});
	}

}
