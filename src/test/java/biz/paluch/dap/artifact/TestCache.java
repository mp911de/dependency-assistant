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

package biz.paluch.dap.artifact;

import java.time.Clock;
import java.util.Collection;
import java.util.List;

import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;

/**
 * Test utility {@link Cache} for seeding release and vulnerability state.
 *
 * <p>Intended for tests that prepare cache content directly instead of going
 * through scanner or repository collaborators. Vulnerability helpers update
 * existing cached releases only, so tests must register the artifact and
 * release first through {@link #addArtifacts(CachedArtifact...)} or
 * {@link #putVersionOptions(ArtifactId, Iterable)}.
 *
 * @author Mark Paluch
 */
public class TestCache extends Cache {

	/**
	 * Create a test cache using the default UTC clock.
	 */
	public TestCache() {
	}

	/**
	 * Create a test cache using the given clock for cache timestamps.
	 *
	 * @param clock the clock used for cache timestamps.
	 */
	public TestCache(Clock clock) {
		super(clock);
	}

	/**
	 * Record vulnerability results for a version parsed from the given string.
	 *
	 * @param artifactId the cached artifact to update.
	 * @param version the version text to update.
	 * @param vulnerabilities the scan results to store.
	 * @throws IllegalStateException if no cached artifact exists for
	 * {@code artifactId}.
	 * @see #addVulnerabilities(ArtifactId, ArtifactVersion, Collection)
	 */
	public void addVulnerabilities(ArtifactId artifactId, String version, Collection<Vulnerability> vulnerabilities) {
		addVulnerabilities(artifactId, ArtifactVersion.of(version), vulnerabilities);
	}

	/**
	 * Record vulnerability results for a cached artifact version.
	 *
	 * <p>An empty vulnerability collection records a completed clean scan. The
	 * target release must already exist in the cached artifact.
	 *
	 * @param artifactId the cached artifact to update.
	 * @param version the cached version to update.
	 * @param vulnerabilities the scan results to store.
	 * @throws IllegalStateException if no cached artifact exists for
	 * {@code artifactId}.
	 */
	public void addVulnerabilities(ArtifactId artifactId, ArtifactVersion version,
			Collection<Vulnerability> vulnerabilities) {
		CachedArtifact cachedArtifact = findCachedArtifact(artifactId);
		if (cachedArtifact == null) {
			throw new IllegalStateException("No CachedArtifact found for " + artifactId);
		}
		cachedArtifact.recordVulnerabilities(999, version, vulnerabilities);
	}

	/**
	 * Record vulnerability results for a version parsed from the given string.
	 *
	 * @param artifactId the cached artifact to update.
	 * @param version the version text to update.
	 * @param vulnerabilities the scan results to store.
	 * @throws IllegalStateException if no cached artifact exists for
	 * {@code artifactId}.
	 * @see #addVulnerabilities(ArtifactId, ArtifactVersion, Vulnerability...)
	 */
	public void addVulnerabilities(ArtifactId artifactId, String version, Vulnerability... vulnerabilities) {
		addVulnerabilities(artifactId, ArtifactVersion.of(version), vulnerabilities);
	}

	/**
	 * Record vulnerability results for a version parsed from the given string.
	 *
	 * @param artifactId the cached artifact to update.
	 * @param version the version text to update.
	 * @param vulnerabilities the scan result to store.
	 * @throws IllegalStateException if no cached artifact exists for
	 * {@code artifactId}.
	 * @see #addVulnerabilities(ArtifactId, ArtifactVersion, Vulnerabilities)
	 */
	public void addVulnerabilities(ArtifactId artifactId, String version, Vulnerabilities vulnerabilities) {
		addVulnerabilities(artifactId, ArtifactVersion.of(version), vulnerabilities);
	}

	/**
	 * Record vulnerability results for a cached artifact version.
	 *
	 * @param artifactId the cached artifact to update.
	 * @param version the cached version to update.
	 * @param vulnerabilities the scan results to store.
	 * @throws IllegalStateException if no cached artifact exists for
	 * {@code artifactId}.
	 * @see #addVulnerabilities(ArtifactId, ArtifactVersion, Collection)
	 */
	public void addVulnerabilities(ArtifactId artifactId, ArtifactVersion version, Vulnerability... vulnerabilities) {
		addVulnerabilities(artifactId, version, List.of(vulnerabilities));
	}

	/**
	 * Record vulnerability results for a cached artifact version.
	 *
	 * @param artifactId the cached artifact to update.
	 * @param version the cached version to update.
	 * @param vulnerabilities the scan result to store.
	 * @throws IllegalStateException if no cached artifact exists for
	 * {@code artifactId}.
	 * @see #addVulnerabilities(ArtifactId, ArtifactVersion, Collection)
	 */
	public void addVulnerabilities(ArtifactId artifactId, ArtifactVersion version, Vulnerabilities vulnerabilities) {
		addVulnerabilities(artifactId, version, vulnerabilities.get());
	}

	/**
	 * Seed a vulnerable release in one step: cache the release for the artifact and
	 * record the given vulnerabilities for it.
	 *
	 * @param artifactId the artifact to seed.
	 * @param version the vulnerable version.
	 * @param vulnerabilities the advisories affecting the version.
	 */
	public void vulnerable(ArtifactId artifactId, String version, Vulnerability... vulnerabilities) {
		putVersionOptions(artifactId, List.of(Release.of(version)));
		addVulnerabilities(artifactId, version, vulnerabilities);
	}

	/**
	 * Return the test cache installed for the given project.
	 *
	 * @param project the project whose state service provides the cache.
	 * @return the cache installed in the project state service.
	 * @throws ClassCastException if the project state service does not hold a
	 * {@code TestCache}.
	 */
	public static TestCache getInstance(Project project) {
		return (TestCache) StateService.getInstance(project).getCache();
	}

}
