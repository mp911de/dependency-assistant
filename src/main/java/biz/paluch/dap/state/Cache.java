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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.*;
import biz.paluch.dap.checker.Vulnerabilities;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

/**
 * Persistent cache for release metadata and per-project property correlations.
 * <p>
 * This type serves as the durable backing store of the plugin state. It
 * keeps:
 * <ul>
 * <li>cached releases keyed by artifact coordinates, and</li>
 * <li>project-scoped property mappings that can later be used to resolve
 * version-managed dependencies.</li>
 * </ul>
 * Lookup methods intentionally return snapshots or derived views rather than
 * exposing the synchronized backing collections directly.
 *
 * @author Mark Paluch
 */
@Tag("cache")
public class Cache {

	/**
	 * Clock used for cache age calculations.
	 */
	@Transient
	private final Clock clock;

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
	 * Epoch-millisecond timestamp of the last successful update, or {@code 0} if no
	 * update has been applied yet.
	 */
	@Attribute
	private volatile long lastUpdateTimestamp = 0L;

	private final @XCollection(propertyElementName = "artifacts", elementName = "artifact", style = XCollection.Style.v2) List<CachedArtifact> artifacts = new ArrayList<>();

	private final @Tag @XCollection(propertyElementName = "projects", elementName = "project", style = XCollection.Style.v2) List<ProjectCache> projects = Collections
			.synchronizedList(new ArrayList<>());

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
	 * Return cached releases for the given artifact.
	 *
	 * @param artifactId the artifact to look up.
	 * @return the cached releases for the artifact, or an empty list if no entry is
	 * present.
	 */
	@Transient
	public Releases getReleases(ArtifactId artifactId) {
		return getReleases(artifactId, false);
	}

	/**
	 * Return cached releases for the given artifact.
	 * <p>
	 * If {@code ensureRecent} is {@literal true}, stale cache content is treated
	 * as absent and the result is an empty list once the cache age exceeds
	 * the configured expiration window. The method does not trigger a refresh.
	 *
	 * @param artifactId the artifact to look up.
	 * @param ensureRecent whether stale cache content should be ignored.
	 * @return the cached releases for the artifact, or an empty list if no entry is
	 * present or the cache is considered stale.
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
			return Releases.of(cachedArtifact.getVersionOptions());
		}

		return Releases.empty();
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
		synchronized (this.artifacts) {
			this.artifacts.addAll(artifacts);
		}
	}

	/**
	 * Replace the cached releases for the given artifact and update their last seen
	 * timestamps.
	 * <p>If no cache entry exists yet, one is created first.
	 *
	 * @param artifactId the artifact whose releases should be stored.
	 * @param releases the releases to cache.
	 */
	public void putVersionOptions(ArtifactId artifactId, Iterable<? extends Release> releases) {

		synchronized (artifacts) {
			CachedArtifact artifactToUse = findCachedArtifact(artifactId);
			if (artifactToUse == null) {
				artifactToUse = new CachedArtifact(artifactId);
				artifacts.add(artifactToUse);
			}
			artifactToUse.setCachedReleases(FetchedReleases.convert(releases), clock.millis());
		}
	}

	public void putBillOfMaterials(DependencyCollector collector, PackageSystem packageSystem) {

		collector.getUsages().forEach(it -> {

			DeclarationSource.Bom bom = it.getDeclarationSource(DeclarationSource.Bom.class);

			if (bom == null) {
				return;
			}

			Map<ArtifactId, ArtifactVersion> members = bom.getArtifacts();
			if (members.isEmpty()) {
				return;
			}
			BillOfMaterials billOfMaterials = BillOfMaterials.of(it.getArtifactId(),
					it.getCurrentVersion(), members);
			putBillOfMaterials(billOfMaterials, packageSystem);
		});
	}

	public void putBillOfMaterials(BillOfMaterials bom, PackageSystem packageSystem) {

		ArtifactId artifactId = bom.getArtifactId();
		synchronized (artifacts) {
			CachedArtifact artifactToUse = findCachedArtifact(artifactId, packageSystem);
			if (artifactToUse == null) {
				artifactToUse = new CachedArtifact(artifactId);
				artifacts.add(artifactToUse);
			}
			artifactToUse.setPackageSystem(packageSystem);
			artifactToUse.setBillOfMaterials(bom);
		}
	}

	/**
	 * Return the cached Bill of Materials for the given BOM coordinates and
	 * version.
	 * <p>Released BOM contents are immutable, so entries never expire by age; the
	 * containing artifact's last-seen eviction bounds their lifetime.
	 *
	 * @param artifactId the BOM artifact coordinates.
	 * @param version the BOM version.
	 * @return the Bill of Materials, or {@literal null} if no membership is cached
	 * for the version.
	 */
	@Transient
	public @Nullable BillOfMaterials getBillOfMaterials(ArtifactId artifactId, ArtifactVersion version) {

		CachedArtifact cachedArtifact = findCachedArtifact(artifactId);
		if (cachedArtifact == null) {
			return null;
		}

		CachedBom membership = cachedArtifact.getBomMembership(version.toString());
		return membership != null ? BillOfMaterials.of(artifactId, version, membership.toMembers()) : null;
	}

	/**
	 * Update the cached releases using the given {@link FetchedReleases}, notifying
	 * {@code onNewRelease} for each release added that was not previously cached.
	 *
	 * @param releases the fetched releases.
	 * @param packageSystem the ecosystem the fetched artifact belongs to; stored on
	 * a freshly created entry.
	 * @param onNewRelease invoked once per newly cached release .
	 */
	public void updateVersionOptions(FetchedReleases releases, PackageSystem packageSystem,
			BiConsumer<Release, CachedRelease> onNewRelease) {

		ArtifactId artifactId = releases.getArtifactId();

		synchronized (artifacts) {
			CachedArtifact artifactToUse = findCachedArtifact(artifactId, packageSystem);
			if (artifactToUse == null) {
				artifactToUse = new CachedArtifact(artifactId);
				artifactToUse.setPackageSystem(packageSystem);
				artifacts.add(artifactToUse);
			}

			artifactToUse.updateCachedReleases(releases, now(), onNewRelease);
		}
	}

	/**
	 * Record a successful cache update, stamping the current time from this cache's
	 * {@link Clock}.
	 */
	public void recordUpdate() {
		this.lastUpdateTimestamp = clock.millis();
	}

	/**
	 * Return the cache entry for the given project identity.
	 * <p>
	 * If no entry exists yet, a new one is created, stored, and returned.
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
			projects.add(projectCache);
			projects.sort(ProjectCache.COMPARATOR);
			return projectCache;
		}
	}

	/**
	 * Find the first project property with the given name that satisfies the
	 * supplied filter.
	 *
	 * @param propertyName the property name to locate.
	 * @param filter the conditional that must accept the matching property.
	 * @return the first matching project property, or {@literal null} if none matches.
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
	 * Invoke the given consumer for each property known to this cache.
	 * <p>
	 * Iteration is based on a snapshot of the current project entries.
	 *
	 * @param propertyConsumer the consumer to invoke.
	 */
	public void doWithProperties(Consumer<VersionProperty> propertyConsumer) {
		getProjects().forEach(project -> project.getProperties().forEach(propertyConsumer));
	}

	/**
	 * Return a snapshot of the cached artifact entries.
	 * <p>The list is a copy taken under the artifacts lock; the entries themselves
	 * are the live instances. The background scan walks this snapshot to read each
	 * artifact's persisted {@link CachedArtifact#getPackageSystem() ecosystem} and
	 * cached releases so it can build the correct vulnerability query from the
	 * cache alone.
	 *
	 * @return an immutable snapshot of the current artifact entries.
	 */
	public List<CachedArtifact> getCachedArtifacts() {
		synchronized (artifacts) {
			return List.copyOf(artifacts);
		}
	}

	/**
	 * Return the raw {@link CachedRelease} entries for the given artifact.
	 * <p>
	 * Unlike {@link #getReleases(ArtifactId, boolean)}, this variant returns the
	 * serialized form including optional extended attributes such as the commit SHA
	 * stored by the GitHub integration.
	 *
	 * @param artifactId the artifact to look up.
	 * @return the cached release entries, or an empty list if no entry is present.
	 */
	public List<CachedRelease> getCachedReleases(ArtifactId artifactId) {
		synchronized (artifacts) {
			CachedArtifact cachedArtifact = findCachedArtifact(artifactId);
			return cachedArtifact != null ? cachedArtifact.getReleases() : Collections.emptyList();
		}
	}

	/**
	 * Invoke the given consumer for a known artifact to this cache. Iteration is
	 * based on the actual artifact.
	 *
	 * @param artifactId the artifact to look up.
	 * @param consumer the consumer to invoke.
	 */
	public void doWithArtifact(ArtifactId artifactId, Consumer<CachedArtifact> consumer) {
		CachedArtifact cachedArtifact = findCachedArtifact(artifactId);
		if (cachedArtifact != null) {
			consumer.accept(cachedArtifact);
		}
	}

	/**
	 * Invoke the given consumer for a known artifact to this cache. Iteration is
	 * based on the actual artifact.
	 *
	 * @param pkg the package identity to look up.
	 * @param consumer the consumer to invoke.
	 */
	public void doWithArtifact(PackageIdentity pkg, Consumer<CachedArtifact> consumer) {
		CachedArtifact cachedArtifact = findCachedArtifact(pkg);
		if (cachedArtifact != null) {
			consumer.accept(cachedArtifact);
		}
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
	 * Return the per-version {@link Vulnerabilities} for the given artifact across
	 * all package systems.
	 *
	 * @param artifactId the artifact to look up.
	 * @return a map of cached version to its vulnerability scan; empty if no entry
	 * is present.
	 */
	@Transient
	public Map<ArtifactVersion, Vulnerabilities> getVulnerabilities(ArtifactId artifactId) {
		return getVulnerabilities(artifactId, (PackageSystem) null);
	}

	/**
	 * Return the per-version {@link Vulnerabilities} for the artifact and package
	 * system carried by the given identity.
	 *
	 * @param identity the package identity to look up.
	 * @return a map of cached version to its vulnerability scan; empty if no entry
	 * is present.
	 */
	@Transient
	public Map<ArtifactVersion, Vulnerabilities> getVulnerabilities(PackageIdentity identity) {
		return getVulnerabilities(identity.getArtifactId(), identity.getPackageSystem());
	}

	/**
	 * Return the per-version {@link Vulnerabilities} for the given artifact,
	 * optionally restricted to a package system.
	 *
	 * @param artifactId the artifact to look up.
	 * @param packageSystem the package system to match, or {@literal null} to match
	 * any package system.
	 * @return a map of cached version to its vulnerability scan; empty if no entry
	 * is present.
	 */
	@Transient
	public Map<ArtifactVersion, Vulnerabilities> getVulnerabilities(ArtifactId artifactId,
			@Nullable PackageSystem packageSystem) {

		CachedArtifact cachedArtifact = findCachedArtifact(artifactId, packageSystem);
		if (cachedArtifact == null) {
			return Map.of();
		}

		Map<ArtifactVersion, Vulnerabilities> vulnerabilities = new HashMap<>();
		for (CachedRelease release : cachedArtifact.getReleases()) {
			vulnerabilities.put(release.toRelease().getVersion(), release.toVulnerabilities());
		}

		return vulnerabilities;
	}

	/**
	 * Find a cached artifact.
	 * @param artifactId the artifact to look up.
	 * @return the cached artifact or {@literal null} if none found.
	 */
	public @Nullable CachedArtifact findCachedArtifact(ArtifactId artifactId) {
		synchronized (artifacts) {
			for (CachedArtifact artifact : artifacts) {
				// TODO: more performant lookup? Use Comparator and a tree set?
				if (artifact.matches(artifactId)) {
					return artifact;
				}
			}
		}
		return null;
	}

	/**
	 * Find a cached artifact.
	 * @param pkg the package identity to look up.
	 * @return the cached artifact or {@literal null} if none found.
	 */
	public @Nullable CachedArtifact findCachedArtifact(PackageIdentity pkg) {
		return findCachedArtifact(pkg.getArtifactId(), pkg.getPackageSystem());
	}

	/**
	 * Find a cached artifact.
	 * @param artifactId the artifact to look up.
	 * @param packageSystem the artifact ecosytem.
	 * @return the cached artifact or {@literal null} if none found.
	 */
	public @Nullable CachedArtifact findCachedArtifact(ArtifactId artifactId, @Nullable PackageSystem packageSystem) {
		synchronized (artifacts) {
			for (CachedArtifact artifact : artifacts) {
				// TODO: more performant lookup? Use Comparator and a tree set?
				if (artifact.matches(artifactId, packageSystem)) {
					return artifact;
				}
			}
		}
		return null;
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

		if (!cached.getReleases().isEmpty()) {
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
	 * <p>Artifact entries carrying only Bill of Materials membership do not count;
	 * the release store is considered empty until a release fetch produced results.
	 *
	 * @return {@literal true} if at least one artifact entry has cached releases.
	 */
	public boolean hasReleases() {

		synchronized (artifacts) {
			for (CachedArtifact artifact : artifacts) {
				if (artifact.hasReleases()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Return whether this cache contains any known projects.
	 *
	 * @return {@literal true} if at least one project entry is present.
	 */
	public boolean hasDependencies() {
		synchronized (projects) {
			return !projects.isEmpty();
		}
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
		long threshold = clock.millis() - STALE_THRESHOLD_MILLIS;

		synchronized (artifacts) {
			for (CachedArtifact artifact : artifacts) {
				if (artifact.getLastSeen() > 0 && artifact.getLastSeen() < threshold) {
					continue;
				}
				copy.artifacts.add(artifact.snapshot());
			}
		}

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

		return copy;
	}

}
