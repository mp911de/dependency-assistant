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

package biz.paluch.dap.fixtures;

import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for {@link CachedRelease} entries on a single
 * {@link CachedArtifact}.
 *
 * <p>A {@link CachedArtifact} is identified by a group and an artifact
 * identifier; these are generic artifact coordinates independent of any
 * specific build system. Use {@link #artifact(String, String, Consumer)} as the
 * primary entry point.
 *
 * @author Mark Paluch
 */
public class ReleaseBuilder {

	private final List<CachedRelease> releases;

	private ReleaseBuilder(List<CachedRelease> releases) {
		this.releases = releases;
	}

	/**
	 * Create a {@link CachedArtifact} populated through a configurer callback.
	 *
	 * @param groupId the group identifier of the artifact.
	 * @param artifactId the artifact identifier.
	 * @param configurer callback that populates releases via
	 * {@code ReleaseBuilder}.
	 * @return the configured {@link CachedArtifact}.
	 */
	public static CachedArtifact artifact(String groupId, String artifactId,
			Consumer<ReleaseBuilder> configurer) {
		return artifact(ArtifactId.of(groupId, artifactId), configurer);
	}

	/**
	 * Create a {@link CachedArtifact} for the given coordinates populated through a
	 * configurer callback.
	 *
	 * @param artifactId the artifact coordinates.
	 * @param configurer callback that populates releases via
	 * {@code ReleaseBuilder}.
	 * @return the configured {@link CachedArtifact}.
	 */
	public static CachedArtifact artifact(ArtifactId artifactId, Consumer<ReleaseBuilder> configurer) {

		CachedArtifact artifact = new CachedArtifact(artifactId);
		configurer.accept(new ReleaseBuilder(artifact.getReleases()));
		return artifact;
	}

	/**
	 * Add a release with the given version.
	 *
	 * @param version the release version string.
	 * @return this builder.
	 * @see #add(String, String, String)
	 */
	public ReleaseBuilder add(String version) {
		return add(version, null);
	}

	/**
	 * Add a release with the given version and release date.
	 *
	 * @param version the release version string.
	 * @param date the release date; can be {@literal null}.
	 * @return this builder.
	 * @see #add(String, String, String)
	 */
	public ReleaseBuilder add(String version, @Nullable String date) {
		releases.add(new CachedRelease(version, date));
		return this;
	}

	/**
	 * Add a release with the given version, release date, and commit SHA.
	 *
	 * @param version the release version string.
	 * @param date the release date; can be {@literal null}.
	 * @param sha the commit SHA associated with the release; can be
	 * {@literal null}.
	 * @return this builder.
	 */
	public ReleaseBuilder add(String version, @Nullable String date, @Nullable String sha) {
		releases.add(new CachedRelease(version, date, sha));
		return this;
	}

}
