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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import org.jspecify.annotations.Nullable;

/**
 * Operation-scoped result of fetching releases for one artifact.
 *
 * <p>The result carries the fetch plan and source observations needed to merge
 * the releases into {@link Cache}. Input collections are retained and exposed
 * directly. Callers must not mutate them after construction.
 *
 * @author Mark Paluch
 */
public class FetchedReleases implements HasArtifactId {

	private final ArtifactId artifactId;

	private final Collection<CachedRelease> releases;

	private final NavigableMap<Release, CachedRelease> releasePairs;

	private final FetchPlan plan;

	private final @Nullable String preferredSource;

	private final Collection<String> emptySources;

	private final @Nullable CachedMetadata projectMetadata;

	/**
	 * Create a new {@code FetchedReleases} instance.
	 * @param artifactId the artifact identifier for which the releases were
	 * fetched.
	 * @param releases the fetched releases in cache representation. The collection
	 * is retained.
	 * @param plan the underlying fetch plan.
	 * @param preferredSource the preferred source for the artifact, can either
	 * contain {@link ReleaseSource#getId()} or be empty (or {@literal null}).
	 * @param emptySources the {@link ReleaseSource#getId() release source
	 * identifiers} that returned no releases. The collection is retained.
	 * @param projectMetadata project metadata captured during the fetch, or
	 * {@literal null} if the fetch produced none.
	 */
	public FetchedReleases(ArtifactId artifactId, Collection<CachedRelease> releases, FetchPlan plan,
			@Nullable String preferredSource, Collection<String> emptySources,
			@Nullable CachedMetadata projectMetadata) {
		this.artifactId = artifactId;
		this.releases = releases;
		this.plan = plan;
		this.preferredSource = preferredSource;
		this.emptySources = emptySources;
		this.projectMetadata = projectMetadata;

		NavigableMap<Release, CachedRelease> pairs = new TreeMap<>();
		for (CachedRelease cached : releases) {
			pairs.put(cached.toRelease(), cached);
		}
		this.releasePairs = pairs;
	}

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	/**
	 * Return the retained fetched-release collection.
	 *
	 * @return the releases supplied at construction time.
	 */
	public Collection<CachedRelease> getReleases() {
		return this.releases;
	}

	/**
	 * Perform the given action for each fetched release paired with its cached
	 * representation, in {@link Release} order.
	 *
	 * @param action the action to perform.
	 */
	public void forEach(BiConsumer<? super Release, ? super CachedRelease> action) {
		this.releasePairs.forEach(action);
	}

	/**
	 * Return whether every configured source was queried.
	 *
	 * @return {@code true} if the merge may advance the full-fetch clock.
	 */
	public boolean isFullFetch() {
		return this.plan.isFullFetch();
	}

	/**
	 * Return the release source preferred for later fetches.
	 *
	 * @return the preferred source identifier, or {@literal null} if no source was
	 * preferred.
	 */
	public @Nullable String getPreferredSource() {
		return this.preferredSource;
	}

	/**
	 * Return the retained identifiers of sources that returned no releases.
	 *
	 * @return the empty-source identifiers supplied at construction time.
	 */
	public Collection<String> getEmptySources() {
		return this.emptySources;
	}

	/**
	 * Return the project metadata captured during the fetch.
	 *
	 * @return the captured metadata, or {@literal null} if the fetch produced none.
	 */
	public @Nullable CachedMetadata getProjectMetadata() {
		return this.projectMetadata;
	}

	/**
	 * Convert the given {@link Release}s to {@link CachedRelease}s.
	 * @param releases iterable of releases to convert.
	 * @return the resulting list of cached releases.
	 */
	public static List<CachedRelease> convert(Iterable<? extends Release> releases) {
		List<CachedRelease> converted = new ArrayList<>();
		for (Release release : releases) {
			converted.add(CachedRelease.from(release));
		}
		return converted;
	}

}
