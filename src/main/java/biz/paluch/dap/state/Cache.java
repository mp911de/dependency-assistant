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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.VersionCheckCandidate;
import biz.paluch.dap.artifact.VersionOption;
import biz.paluch.dap.artifact.VersionSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;

public class Cache {

	private static final Clock CLOCK = Clock.systemUTC();

	private static final Duration CACHE_EXPIRATION = Duration.ofMinutes(10);

	/**
	 * Epoch-millisecond timestamp of the last successful POM update, or {@code 0} if no update has been applied yet.
	 */
	@Attribute private long lastUpdateTimestamp = 0L;
	private final @XCollection(propertyElementName = "artifacts", elementName = "artifact",
			style = XCollection.Style.v2) List<Artifact> artifacts = new ArrayList<>();
	private final @Tag @XCollection(propertyElementName = "properties", elementName = "property",
			style = XCollection.Style.v2) List<Property> properties = new ArrayList<>();

	@Transient private final Map<String, Property> propertyMap = new java.util.TreeMap<>();

	public long getLastUpdateTimestamp() {
		return lastUpdateTimestamp;
	}

	public void setLastUpdateTimestamp(long lastUpdateTimestamp) {
		this.lastUpdateTimestamp = lastUpdateTimestamp;
	}

	/**
	 * Load cached version options for the given artifact. Returns an empty list if the cache is expired.
	 */
	@Transient
	public List<VersionOption> getVersionOptions(ArtifactId artifactId, boolean ensureRecent) {

		if (ensureRecent) {
			Instant instant = CLOCK.instant();
			Instant lastUpdateInstant = Instant.ofEpochMilli(lastUpdateTimestamp);
			Duration age = Duration.between(lastUpdateInstant, instant);

			if (age.compareTo(CACHE_EXPIRATION) > 0) {
				return List.of();
			}
		}

		synchronized (artifacts) {
			for (Artifact artifact : artifacts) {
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
	public void putVersionOptions(ArtifactId artifactId, List<VersionOption> versionOptions) {

		Artifact artifactToUse;
		synchronized (artifacts) {
			artifactToUse = null;
			for (Artifact artifact : artifacts) {
				if (artifact.matches(artifactId)) {
					artifactToUse = artifact;
					break;
				}
			}
			if (artifactToUse == null) {
				artifactToUse = new Artifact(artifactId);
				artifacts.add(artifactToUse);
			}
		}

		artifactToUse.setVersionOptions(versionOptions);
	}

	/**
	 * Record an update of the cache.
	 */
	public void recordUpdate() {
		this.lastUpdateTimestamp = CLOCK.millis();
	}

	/**
	 * Returns all known property-to-artifact mappings. Each {@link Property} carries the property name and the
	 * artifact(s) whose version it controls.
	 */
	public List<Property> getProperties() {
		return List.copyOf(properties);
	}

	/**
	 * Update the cache with the given properties.
	 */
	public void setProperties(Collection<VersionCheckCandidate> versionCheckCandidates) {

		properties.clear();
		propertyMap.clear();

		for (VersionCheckCandidate candidate : versionCheckCandidates) {

			for (VersionSource versionSource : candidate.getVersionSources()) {
				if (versionSource instanceof VersionSource.VersionPropertySource vps) {

					Property property = propertyMap.computeIfAbsent(vps.getProperty(), Property::new);
					property.addArtifact(candidate.getArtifactId());
				}
			}
		}

		properties.addAll(propertyMap.values());
	}

	public @Nullable Property getProperty(String propertyName) {

		if (propertyMap.size() != properties.size()) {

			propertyMap.clear();
			for (Property property : properties) {
				propertyMap.put(property.name(), property);
			}
		}

		return propertyMap.get(propertyName);
	}
}
