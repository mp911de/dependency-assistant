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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
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
	public static final Clock CLOCK = Clock.systemUTC();

	private static final Duration CACHE_EXPIRATION = Duration.ofHours(8);

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
	 * Return cached releases for the given artifact from.
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
		synchronized (artifacts) {
			for (CachedArtifact artifact : artifacts) {
				if (artifact.matches(artifactIdToUse)) {
					return Releases.of(artifact.getVersionOptions());
				}
			}
		}

		return Releases.empty();
	}

	/**
	 * Append cached artifact entries to this cache.
	 * <p>
	 * Existing entries are not de-duplicated.
	 *
	 * @param artifacts the artifact entries to append.
	 */
	public void addArtifacts(Collection<CachedArtifact> artifacts) {

		synchronized (this.artifacts) {
			this.artifacts.addAll(artifacts);
		}
	}

	/**
	 * Replace the cached releases for the given artifact.
	 * <p>
	 * If no cache entry exists yet, one is created first.
	 *
	 * @param artifactId the artifact whose releases should be stored.
	 * @param releases the releases to cache.
	 */
	public void putVersionOptions(ArtifactId artifactId, Iterable<? extends Release> releases) {

		List<CachedRelease> converted = new ArrayList<>();
		for (Release release : releases) {
			converted.add(CachedRelease.from(release));
		}

		synchronized (artifacts) {
			CachedArtifact artifactToUse = null;
			for (CachedArtifact artifact : artifacts) {
				if (artifact.matches(artifactId)) {
					artifactToUse = artifact;
					break;
				}
			}
			if (artifactToUse == null) {
				artifactToUse = new CachedArtifact(artifactId);
				artifacts.add(artifactToUse);
			}
			artifactToUse.replaceCachedReleases(converted);
		}
	}

	/**
	 * Record a successful cache update using the current UTC clock.
	 */
	public void recordUpdate() {
		this.lastUpdateTimestamp = CLOCK.millis();
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
			for (CachedArtifact artifact : artifacts) {
				if (artifact.matches(artifactId)) {
					return List.copyOf(artifact.getReleases());
				}
			}
		}
		return List.of();
	}

	/**
	 * Return whether this cache contains any cached release entries.
	 *
	 * @return {@literal true} if at least one artifact entry is present.
	 */
	public boolean hasReleases() {
		return !artifacts.isEmpty();
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
		return Duration.between(lastUpdate, CLOCK.instant());
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

		synchronized (artifacts) {
			for (CachedArtifact artifact : artifacts) {
				CachedArtifact artifactCopy = new CachedArtifact(artifact.getGroupId(), artifact.getArtifactId());
				artifactCopy.replaceCachedReleases(new ArrayList<>(artifact.getReleases()));
				copy.artifacts.add(artifactCopy);
			}
		}

		synchronized (projects) {
			for (ProjectCache project : projects) {
				if (project != null) {
					copy.projects.add(project.snapshot());
				}
			}
		}

		return copy;
	}

}
