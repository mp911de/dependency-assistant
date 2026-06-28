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

import java.util.Set;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ReleaseSource;
import org.jspecify.annotations.Nullable;

/**
 * Decision describing how a {@link CachedArtifact}'s release sources should be
 * fetched under empty-lookup back-off.
 *
 * @author Mark Paluch
 */
public class FetchPlan implements Predicate<String> {

	private static final FetchPlan SKIP = new FetchPlan(true, false, null, Set.of());

	private static final FetchPlan FULL_FETCH = new FetchPlan(false, true, null, Set.of());

	private final boolean skip;

	private final boolean fullFetch;

	private final @Nullable String onlySource;

	private final Set<String> excludedSources;

	private FetchPlan(boolean skip, boolean fullQuery, @Nullable String onlySource, Set<String> excludedSources) {
		this.skip = skip;
		this.fullFetch = fullQuery;
		this.onlySource = onlySource;
		this.excludedSources = excludedSources;
	}

	/**
	 * Return the plan that skips fetching and serves the cached result.
	 * @return the skipping plan.
	 */
	static FetchPlan skip() {
		return SKIP;
	}

	/**
	 * Unconditional (full) fetch plan.
	 */
	public static FetchPlan fullFetch() {
		return FULL_FETCH;
	}

	/**
	 * Return a partial fetching plan that queries all sources, delegating to
	 * {@link #partial(String, Set)} with no preferred source and no excluded
	 * sources.
	 *
	 * @return the fetching plan.
	 */
	static FetchPlan partial() {
		return partial(null, Set.of());
	}

	/**
	 * Return a fetching plan for fetching releases from a limited set of
	 * {@link ReleaseSource#getId() release sources}.
	 * @param onlySource the only source to query (the preferred source), or
	 * {@literal null} to query all sources except {@code excludedSources}.
	 * @param excludedSources identifiers of sources to skip; ignored when
	 * {@code onlySource} is set.
	 * @return the fetching plan.
	 */
	static FetchPlan partial(@Nullable String onlySource, Set<String> excludedSources) {
		return new FetchPlan(false, false, onlySource, excludedSources);
	}

	/**
	 * Return a fetching plan, optionally narrowing to a single source and excluding
	 * known-empty sources.
	 * @param fullQuery whether every configured source is queried (no source
	 * skipped); only then is the artifact's re-check clock advanced.
	 * @param onlySource the only source to query (the preferred source), or
	 * {@literal null} to query all sources except {@code excludedSources}.
	 * @param excludedSources identifiers of sources to skip; ignored when
	 * {@code onlySource} is set.
	 * @return the fetching plan.
	 */
	static FetchPlan fetch(boolean fullQuery, @Nullable String onlySource, Set<String> excludedSources) {
		return new FetchPlan(false, fullQuery, onlySource, excludedSources);
	}

	/**
	 * Return whether fetching should be skipped in favor of the cached result.
	 * @return {@literal true} to skip fetching; {@literal false} otherwise.
	 */
	public boolean isSkip() {
		return skip;
	}

	/**
	 * Return whether this plan queries every configured source.
	 * @return {@literal true} if no source is skipped; {@literal false} otherwise.
	 */
	public boolean isFullFetch() {
		return fullFetch;
	}

	/**
	 * Test whether the given release source should be queried under this plan.
	 */
	@Override
	public boolean test(String sourceId) {

		if (onlySource != null) {
			return onlySource.equals(sourceId);
		}
		return !excludedSources.contains(sourceId);
	}

}
