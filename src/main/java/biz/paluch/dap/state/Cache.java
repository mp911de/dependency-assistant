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

import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Release;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;

@Tag("cache")
public class Cache {

	private static final Clock CLOCK = Clock.systemUTC();

	private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(10);

	/**
	 * Epoch-millisecond timestamp of the last successful update, or {@code 0} if no update has been applied yet.
	 */
	@Attribute private long lastUpdateTimestamp = 0L;
	private final @XCollection(propertyElementName = "artifacts", elementName = "artifact",
			style = XCollection.Style.v2) List<CachedArtifact> artifacts = new ArrayList<>();
	private final @Tag @XCollection(propertyElementName = "projects", elementName = "project",
			style = XCollection.Style.v2) List<ProjectCache> projects = Collections.synchronizedList(new ArrayList<>());

	public long getLastUpdateTimestamp() {
		return lastUpdateTimestamp;
	}

	public void setLastUpdateTimestamp(long lastUpdateTimestamp) {
		this.lastUpdateTimestamp = lastUpdateTimestamp;
	}

	public List<ProjectCache> getProjects() {
		synchronized (projects) {
			return List.copyOf(projects);
		}
	}

	/**
	 * Load cached version options for the given artifact. Returns an empty list if the cache is expired.
	 */
	@Transient
	public List<Release> getReleases(ArtifactId artifactId, boolean ensureRecent) {

		if (ensureRecent) {
			Instant instant = CLOCK.instant();
			Instant lastUpdateInstant = Instant.ofEpochMilli(lastUpdateTimestamp);
			Duration age = Duration.between(lastUpdateInstant, instant);

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
	 * Update the cache with the given version options.
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
	 * Record an update of the cache.
	 */
	public void recordUpdate() {
		this.lastUpdateTimestamp = CLOCK.millis();
	}

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

	public @Nullable ProjectProperty findProperty(String propertyName, Predicate<Property> filter) {

		synchronized (projects) {
			for (ProjectCache project : projects) {
				if (project == null) {
					continue;
				}

				Property property = project.getProperty(propertyName);
				if (property == null || !filter.test(property)) {
					continue;
				}

				return new ProjectProperty(project.getId(), property);
			}
		}

		return null;
	}

	public void doWithProperties(Consumer<Property> propertyConsumer) {
		getProjects().forEach(project -> project.getProperties().forEach(propertyConsumer));
	}
}
