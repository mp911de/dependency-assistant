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
 * Persistent artifact, repository, and project metadata.
 *
 * <p>Call {@link #reindex()} after deserialization. Collection accessors copy
 * the lists but retain live entries unless stated otherwise.
 *
 * @author Mark Paluch
 */
@Tag("cache")
public class Cache implements ModificationTracker {

	private static final Duration CACHE_EXPIRATION = Duration.ofHours(8);

	private static final long STALE_THRESHOLD_MILLIS = Duration.ofDays(30).toMillis();

	/**
	 * Pause repeated empty lookups so unavailable sources are not queried
	 * continuously.
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

	public Cache(Clock clock) {
		this.clock = clock;
	}

	public Clock getClock() {
		return this.clock;
	}

	/**
	 * Return the cache clock time in epoch milliseconds.
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
	 * Record a successful cache update.
	 */
	public void recordUpdate() {
		this.lastUpdateTimestamp = clock.millis();
		this.modificationTracker.incModificationCount();
	}

	/**
	 * Return the last recorded cache update, or {@code null} before the first
	 * update.
	 */
	public @Nullable Instant getLastUpdate() {
		long timestamp = lastUpdateTimestamp;
		return timestamp == 0L ? null : Instant.ofEpochMilli(timestamp);
	}

	/**
	 * Whether recorded release metadata is old enough to prompt for a refresh,
	 * subject to reminder suppression.
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
	 * Suppress refresh reminders for {@link #PLEASE_BE_SILENT_FOR}.
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
	 * Keep first-registered entries authoritative, as in the original list lookup.
	 * Requires the artifacts monitor.
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
	 * Append entries without de-duplication.
	 */
	public void addArtifacts(CachedArtifact... artifacts) {
		addArtifacts(List.of(artifacts));
	}

	/**
	 * Append entries without de-duplication.
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
	 * Replace cached releases and mark the artifact as seen.
	 */
	public void updateReleases(ArtifactId artifactId, Iterable<? extends Release> releases) {
		writeArtifacts(() -> {
			CachedArtifact artifactToUse = getOrCreate(findCachedArtifact(artifactId), null, artifactId);
			artifactToUse.setCachedReleases(FetchedReleases.convert(releases), clock.millis());
		});
	}

	/**
	 * Merge fetched releases, preserving existing vulnerability scans.
	 *
	 * @param onNewRelease invoked for each newly cached release.
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
	 * Return cached releases using coordinates only, ignoring the package system.
	 * An unknown artifact has no releases.
	 */
	@Transient
	public Releases getReleases(PackageIdentity pkg) {
		return getReleases(pkg.getArtifactId());
	}

	/**
	 * Return cached releases regardless of age, or an empty result if unknown.
	 */
	@Transient
	public Releases getReleases(ArtifactId artifactId) {
		return getReleases(artifactId, false);
	}

	/**
	 * Return cached releases without fetching.
	 *
	 * @param ensureRecent whether an expired or uninitialized cache counts as
	 * empty.
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
	 * @return the indexed Bill of Materials, a cached membership, or a prediction
	 * from {@link CachedArtifact#predictBom}. Returns {@literal null} if none is
	 * indexed for the version.
	 */
	@Transient
	public @Nullable BillOfMaterials getBillOfMaterials(VersionedPackage bom) {
		CachedArtifact cachedArtifact = findCachedArtifact(bom.getPackageIdentity());
		return cachedArtifact == null ? null : cachedArtifact.getBom(bom.getVersion());
	}

	public List<ProjectCache> getProjects() {
		synchronized (projects) {
			return List.copyOf(projects);
		}
	}

	/**
	 * Return the existing project entry, creating one if necessary.
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

	public void removeProject(ProjectId identity) {

		synchronized (projects) {
			boolean removed = projects.removeIf(project -> project != null && project.matches(identity));
			if (removed) {
				modificationTracker.incModificationCount();
			}
		}
	}

	/**
	 * Find the first property accepted by the filter, or {@code null} if none
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

	public List<CachedArtifact> getCachedArtifacts() {
		return readArtifacts(() -> List.copyOf(artifacts));
	}

	/**
	 * Return persisted releases including source-specific attributes, or an empty
	 * list for an unknown artifact.
	 */
	public List<CachedRelease> getCachedReleases(ArtifactId artifactId) {
		CachedArtifact cachedArtifact = findCachedArtifact(artifactId);
		return cachedArtifact != null ? cachedArtifact.getCachedReleases() : Collections.emptyList();
	}

	/**
	 * Return the scan result, preserving the distinction between absent and clean.
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
	 * Find matching coordinates and ecosystem, or {@code null} if absent. A missing
	 * ecosystem on either side is a wildcard for legacy entries.
	 */
	public @Nullable CachedArtifact findCachedArtifact(ArtifactId artifactId, @Nullable PackageSystem packageSystem) {
		if (packageSystem == null) {
			return findCachedArtifact(artifactId);
		}
		return findCachedArtifact(PackageIdentity.of(artifactId.detach(), packageSystem));
	}

	/**
	 * Find by coordinates regardless of ecosystem, or {@code null} if absent.
	 */
	public @Nullable CachedArtifact findCachedArtifact(ArtifactId artifactId) {
		return readArtifacts(() -> {
			ensureIndexed();
			return artifactsById.get(artifactId.detach());
		});
	}

	/**
	 * Find by package identity, allowing legacy entries without an ecosystem.
	 * Return {@code null} if absent.
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
	 * Whether existing metadata needs inspection or a retry under the cache policy.
	 * An absent artifact does not require inspection.
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
	 * Select release sources with back-off for repeated empty lookups.
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
	 * Whether any release is cached. BOM membership alone does not count.
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
	 * Whether any project is cached.
	 */
	public boolean hasDependencies() {
		return readArtifacts(() -> {
			return !projects.isEmpty();
		});
	}

	/**
	 * Return the time since the last recorded update, or {@code null} if none.
	 */
	public @Nullable Duration getAge() {
		Instant lastUpdate = getLastUpdate();
		if (lastUpdate == null) {
			return null;
		}
		return Duration.between(lastUpdate, clock.instant());
	}

	/**
	 * Copy the cache for persistence, excluding expired entries.
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
	 * Visit a live entry if present and advance modification tracking.
	 */
	public void doWithArtifact(ArtifactId artifactId, Consumer<CachedArtifact> consumer) {
		CachedArtifact cachedArtifact = findCachedArtifact(artifactId);
		if (cachedArtifact != null) {
			consumer.accept(cachedArtifact);
			modificationTracker.incModificationCount();
		}
	}

	/**
	 * Visit a live entry if present and advance modification tracking.
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
	 * Find a repository by key, or {@code null} if absent.
	 */
	public @Nullable CachedRepository findRepository(String key) {
		return readRepositories(() -> {
			ensureRepositoriesIndexed();
			return repositoriesByKey.get(key);
		});
	}

	/**
	 * Create an entry or update its URL without changing its timestamps.
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

	public List<CachedRepository> getRepositories() {
		return readRepositories(() -> List.copyOf(repositories));
	}

	/**
	 * Visit a live entry if present and advance modification tracking.
	 */
	public void doWithRepository(String key, Consumer<CachedRepository> consumer) {

		CachedRepository repository = findRepository(key);
		if (repository != null) {
			consumer.accept(repository);
			modificationTracker.incModificationCount();
		}
	}

	/**
	 * Deserialization bypasses mutators, so restore the index before lookup.
	 * Requires the repositories monitor.
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
