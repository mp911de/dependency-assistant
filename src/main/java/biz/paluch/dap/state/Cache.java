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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Release;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

/**
 * Persistent cache for release metadata and per-project property correlations.
 * <p>This type serves as the durable backing store of the plugin state. It
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

	public static final Clock CLOCK = Clock.systemUTC();

	private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(10);

	/**
	 * Epoch-millisecond timestamp of the last successful update, or {@code 0} if no
	 * update has been applied yet.
	 */
	@Attribute
	private long lastUpdateTimestamp = 0L;

	private final @XCollection(propertyElementName = "artifacts", elementName = "artifact", style = XCollection.Style.v2) List<CachedArtifact> artifacts = new ArrayList<>();

	private final @Tag @XCollection(propertyElementName = "projects", elementName = "project", style = XCollection.Style.v2) List<ProjectCache> projects = Collections
			.synchronizedList(new ArrayList<>());

	/**
	 * Return the {@link Instant} of the last recorded cache update.
	 */
	public Instant getLastUpdate() {
		return Instant.ofEpochMilli(lastUpdateTimestamp);
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
	 * <p>If {@code ensureRecent} is {@literal true}, stale cache content is treated
	 * as absent and this method returns an empty list once the cache age exceeds
	 * the configured expiration window. The method does not trigger a refresh.
	 *
	 * @param artifactId the artifact to look up.
	 * @param ensureRecent whether stale cache content should be ignored.
	 * @return the cached releases for the artifact, or an empty list if no entry is
	 * present or the cache is considered stale.
	 */
	@Transient
	public List<Release> getReleases(ArtifactId artifactId, boolean ensureRecent) {

		if (ensureRecent) {
			Duration age = getAge();

			if (age.compareTo(CACHE_EXPIRATION) > 0) {
				return List.of();
			}
		}

		synchronized (artifacts) {
			for (CachedArtifact artifact : artifacts) {
				if (artifact.matches(artifactId)) {
					return artifact.getVersionOptions();
				}
			}
		}

		return List.of();
	}

	/**
	 * Append cached artifact entries to this cache.
	 * <p>This method does not attempt to de-duplicate existing entries.
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
	 * <p>If no cache entry exists yet, one is created first.
	 *
	 * @param artifactId the artifact whose releases should be stored.
	 * @param releases the releases to cache.
	 */
	public void putVersionOptions(ArtifactId artifactId, List<Release> releases) {

		CachedArtifact artifactToUse;
		synchronized (artifacts) {
			artifactToUse = null;
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
		}

		artifactToUse.setVersionOptions(releases);
	}

	/**
	 * Record a successful cache update using the current UTC clock.
	 */
	public void recordUpdate() {
		this.lastUpdateTimestamp = CLOCK.millis();
	}

	/**
	 * Return the cache entry for the given project identity.
	 * <p>If no entry exists yet, this method creates, stores, and returns a new
	 * one.
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
	 * @param filter the predicate that must accept the matching property.
	 * @return the first matching project property, or {@code null} if none matches.
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
	 * <p>Iteration is based on a snapshot of the current project entries.
	 *
	 * @param propertyConsumer the consumer to invoke.
	 */
	public void doWithProperties(Consumer<VersionProperty> propertyConsumer) {
		getProjects().forEach(project -> project.getProperties().forEach(propertyConsumer));
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
	 * @return the duration since {@link #recordUpdate()} or the configured
	 * timestamp was last applied.
	 */
	public Duration getAge() {
		Instant instant = CLOCK.instant();
		Instant lastUpdateInstant = Instant.ofEpochMilli(lastUpdateTimestamp);
		return Duration.between(lastUpdateInstant, instant);
	}

}
