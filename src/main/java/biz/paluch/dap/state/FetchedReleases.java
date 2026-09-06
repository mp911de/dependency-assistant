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
	 * Create a fetch result. Supplied collections must not change after
	 * construction.
	 *
	 * @param preferredSource the source identifier preferred for later fetches, if
	 * any.
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

	public Collection<CachedRelease> getReleases() {
		return this.releases;
	}

	/**
	 * Visit release/cache pairs in release order.
	 */
	public void forEach(BiConsumer<? super Release, ? super CachedRelease> action) {
		this.releasePairs.forEach(action);
	}

	/**
	 * Whether this fetch may advance the periodic source re-check clock.
	 */
	public boolean isFullFetch() {
		return this.plan.isFullFetch();
	}

	public @Nullable String getPreferredSource() {
		return this.preferredSource;
	}

	public Collection<String> getEmptySources() {
		return this.emptySources;
	}

	public @Nullable CachedMetadata getProjectMetadata() {
		return this.projectMetadata;
	}

	public static List<CachedRelease> convert(Iterable<? extends Release> releases) {
		List<CachedRelease> converted = new ArrayList<>();
		for (Release release : releases) {
			converted.add(CachedRelease.from(release));
		}
		return converted;
	}

}
