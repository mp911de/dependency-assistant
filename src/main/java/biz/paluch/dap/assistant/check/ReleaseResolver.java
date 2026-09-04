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

package biz.paluch.dap.assistant.check;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactNotFoundException;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.ReleaseSources;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.state.FetchPlan;
import biz.paluch.dap.state.FetchedReleases;
import biz.paluch.dap.state.HasProjectMetadata;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import biz.paluch.dap.util.TaskScope;
import biz.paluch.dap.util.TaskScope.Subtask;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jspecify.annotations.Nullable;

/**
 * Resolves available releases for an artifact by consulting the {@link Cache}
 * and, when needed, fetching {@link Release}s from its {@link ReleaseSource}s.
 *
 * <p>The resolver owns the full lookup decision for a single artifact: it
 * consults the cache according to the requested {@link Consistency}, asks the
 * cache for a {@link FetchPlan} to honor empty-lookup back-off, narrows the
 * sources accordingly, fetches the remaining sources in parallel, and writes a
 * successful fetch back. A single resolver is reused across artifacts.
 * Per-artifact inputs flow through
 * {@link #getReleases(ReleaseSources, Consistency)}.
 *
 * @author Mark Paluch
 * @see ReleaseSource
 * @see FetchPlan
 */
public class ReleaseResolver {

	private static final Logger LOG = Logger.getInstance(ReleaseResolver.class);

	private static final Duration DEFAULT_SOURCE_TIMEOUT = Duration.ofSeconds(30);

	private final ProgressIndicator indicator;

	private final Cache cache;

	private final Duration sourceTimeout;

	/**
	 * Create a resolver honoring the given progress indicator for cancellation,
	 * backed by the given cache. Sources are fetched on virtual threads scoped to
	 * each lookup.
	 *
	 * @param indicator the progress indicator used for cancellation.
	 * @param cache the release cache consulted and updated by the resolver.
	 */
	public ReleaseResolver(ProgressIndicator indicator, Cache cache) {
		this(indicator, cache, DEFAULT_SOURCE_TIMEOUT);
	}

	ReleaseResolver(ProgressIndicator indicator, Cache cache, Duration sourceTimeout) {
		this.indicator = indicator;
		this.cache = cache;
		this.sourceTimeout = sourceTimeout;
	}

	public static Consistency cached() {
		return Consistency.CACHED;
	}

	public static Consistency refresh() {
		return Consistency.REFRESH;
	}

	public static Consistency reset() {
		return Consistency.RESET;
	}

	/**
	 * Resolve releases for the given artifact, consulting and updating the cache.
	 *
	 * <p>Under {@link Consistency#CACHED} a sufficiently recent cached result is
	 * served without fetching; otherwise the sources are fetched subject to the
	 * cache's {@link FetchPlan}. Releases from completed sources are retained when
	 * a sibling source fails or times out. A failure is returned only when no
	 * source yields releases. {@link ProcessCanceledException} propagates.
	 *
	 * @param sources the artifact and its candidate release sources.
	 * @param consistency the release-cache consistency to use.
	 * @return the lookup result, carrying the resolved releases or a failure
	 * message.
	 */
	public ReleaseLookupResult getReleases(ReleaseSources sources, Consistency consistency) {
		indicator.checkCanceled();
		try {
			if (consistency == Consistency.CACHED) {
				Releases releases = cache.getReleases(sources.artifactId(), true);
				if (!releases.isEmpty()) {
					return ReleaseLookupResult.of(releases);
				}
			}
			return fetchAndCache(sources, consistency);
		} catch (ProcessCanceledException e) {
			throw e;
		} catch (CancellationException e) {
			throw new ProcessCanceledException(e);
		} catch (RuntimeException e) {
			return ReleaseLookupResult.failed(sources.artifactId() + ": " + e.getMessage());
		}
	}

	private ReleaseLookupResult fetchAndCache(ReleaseSources sources, Consistency consistency) {

		FetchPlan fetchPlan;
		if (consistency == Consistency.RESET) {
			fetchPlan = FetchPlan.fullFetch();
		} else {
			fetchPlan = cache.createFetchPlan(sources);
			if (fetchPlan.isSkip()) {
				return ReleaseLookupResult.empty();
			}
			ReleaseSources filtered = sources.filter(it -> fetchPlan.test(it.getId()));
			if (!filtered.isEmpty()) {
				sources = filtered;
			}
		}

		FetchResult result = fetch(sources);
		FetchedReleases fetchedReleases = result.toFetchedReleases(fetchPlan);

		List<Release> newReleases = new ArrayList<>();
		cache.updateReleases(fetchedReleases, sources.packageSystem(),
				(release, cached) -> newReleases.add(release));

		return ReleaseLookupResult.of(result.toReleases(), Releases.of(newReleases));
	}

	/**
	 * Fetch all sources concurrently, giving them {@link #sourceTimeout} together.
	 * A source still pending at the deadline is cancelled and recorded as a
	 * timeout; completed siblings are retained. Results keep the declared source
	 * order, which decides precedence for the preferred source and project
	 * metadata. Cancellation propagates as {@link ProcessCanceledException}.
	 */
	private FetchResult fetch(ReleaseSources releaseSources) {

		ArtifactId artifactId = releaseSources.artifactId();
		Set<ReleaseSource> sources = releaseSources.sources() instanceof Set<ReleaseSource> s ? s
				: new LinkedHashSet<>(releaseSources.sources());

		List<Throwable> errors = new ArrayList<>();
		List<SourceAwareReleases> results = new ArrayList<>();
		ArtifactNotFoundException notFoundException = null;

		try (TaskScope scope = TaskScope.open("ReleaseSources", indicator)) {

			Map<Subtask<SourceAwareReleases>, ReleaseSource> fetches = new LinkedHashMap<>();
			for (ReleaseSource source : sources) {
				fetches.put(scope.fork(() -> fetch(source, artifactId)), source);
			}

			scope.joinAll(sourceTimeout);

			fetches.forEach((fetch, source) -> {
				switch (fetch.state()) {
				case SUCCESS -> results.add(fetch.get());
				case TIMED_OUT -> errors.add(new TimeoutException(
						"Release source %s timed out (Timeout: %s)".formatted(source.getId(), formatTimeout())));
				case CANCELLED -> throw new ProcessCanceledException();
				default -> errors.add(fetch.exception());
				}
			});
		}

		int releaseCount = 0;
		for (SourceAwareReleases release : results) {
			releaseCount += release.releases.size();
			if (release.error != null) {
				if (release.error instanceof ArtifactNotFoundException notFound) {
					notFoundException = notFound;
				}
				String message = "Release source " + release.source.getId() + " failed";
				if (StringUtils.hasText(release.error.getMessage())) {
					message += ": " + release.error.getMessage();
				}
				errors.add(new RuntimeException(message, release.error));
			}
		}

		if (releaseCount == 0) {
			if (!errors.isEmpty()) {
				Throwable exception = errors.getFirst();
				if (exception instanceof RuntimeException re) {
					throw re;
				}

				throw new RuntimeException(exception);
			}
			if (notFoundException != null) {
				throw notFoundException;
			}
		}

		return new FetchResult(artifactId, results);
	}

	/**
	 * Query one source. Not found and ordinary failures are captured in the result,
	 * cancellation propagates.
	 */
	private SourceAwareReleases fetch(ReleaseSource source, ArtifactId artifactId) {

		try {
			Sequence<Release> fetched = source.getReleases(artifactId, indicator);
			CachedMetadata projectMetadata = fetched instanceof HasProjectMetadata metadata
					? metadata.getProjectMetadata()
					: null;
			return new SourceAwareReleases(source, Releases.of(fetched), projectMetadata, null);
		} catch (CancellationException e) {
			// covers ProcessCanceledException, which extends CancellationException
			throw e;
		} catch (ArtifactNotFoundException e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("[%s][%s] No artifacts found.".formatted(source.toString(artifactId), source.getId()));
			}
			return new SourceAwareReleases(source, Releases.empty(), null, e);
		} catch (Exception e) {
			LOG.warn("[%s][%s] Failed to fetch releases".formatted(source.toString(artifactId), source.getId()), e);
			return new SourceAwareReleases(source, Releases.empty(), null, e);
		}
	}

	private String formatTimeout() {

		NlsMessages.NlsDurationFormatter formatter = new NlsMessages.NlsDurationFormatter();
		formatter.setDurationTimeUnit(TimeUnit.MILLISECONDS);
		formatter.setNarrow(true);
		return formatter.formatDuration(sourceTimeout.toMillis());
	}

	record SourceAwareReleases(ReleaseSource source, Releases releases, @Nullable CachedMetadata projectMetadata,
			@Nullable Exception error) {
	}

	/**
	 * Fetch outcome for one artifact, retaining the source outcomes and the merged
	 * {@link Releases} computed once on construction.
	 *
	 * <p>The merged releases are derived from the source results, so they are
	 * computed eagerly and exposed through {@link #toReleases()}.
	 * {@link #getPreferredSource()} reuses that instance rather than asking callers
	 * to pass it back. The empty and producing source identifiers describe this
	 * fetch alone. Combining them with any previously recorded state is the cache's
	 * responsibility.
	 */
	private static class FetchResult {

		private final ArtifactId artifactId;

		private final List<SourceAwareReleases> sources;

		private final Releases releases;

		FetchResult(ArtifactId artifactId, List<SourceAwareReleases> sources) {
			this.artifactId = artifactId;
			this.sources = sources;
			this.releases = merge(sources);
		}

		private static Releases merge(List<SourceAwareReleases> sources) {

			Set<Release> result = new TreeSet<>(Comparator.<Release>naturalOrder().reversed());
			for (SourceAwareReleases entry : sources) {
				result.addAll(entry.releases.toList());
			}
			return Releases.of(result);
		}

		/**
		 * Return the merged releases across all sources, newest first.
		 * @return the merged releases.
		 */
		public Releases toReleases() {
			return releases;
		}

		/**
		 * Return the identifier of the single source that already holds every merged
		 * release, which lets later fetches narrow to it without losing versions.
		 * @return the preferred source identifier, or {@literal null} if no single
		 * source holds all releases.
		 */
		public @Nullable String getPreferredSource() {

			List<Release> merged = releases.toList();
			for (SourceAwareReleases entry : sources) {
				if (!entry.releases.isEmpty() && entry.releases.containsAll(merged)) {
					return entry.source.getId();
				}
			}

			return null;
		}

		/**
		 * Return the identifiers of sources that returned no releases without failing.
		 * @return the empty source identifiers; transient failures are excluded so they
		 * are not mistaken for permanent absence.
		 */
		public Set<String> emptySources() {

			Set<String> ids = new LinkedHashSet<>();
			for (SourceAwareReleases entry : sources) {
				if (entry.error == null && entry.releases.isEmpty()) {
					ids.add(entry.source.getId());
				}
			}
			return ids;
		}

		/**
		 * Return the project metadata captured by any of the sources during this fetch,
		 * or {@literal null} if no source captured metadata.
		 */
		public @Nullable CachedMetadata getProjectMetadata() {

			for (SourceAwareReleases entry : sources) {
				if (entry.projectMetadata != null) {
					return entry.projectMetadata;
				}
			}

			return null;
		}

		public FetchedReleases toFetchedReleases(FetchPlan fetchPlan) {
			return new FetchedReleases(artifactId, FetchedReleases.convert(releases),
					fetchPlan, getPreferredSource(), emptySources(), getProjectMetadata());
		}

	}

	/**
	 * Release-cache consistency mode used while resolving update candidates.
	 *
	 * @author Mark Paluch
	 */
	public enum Consistency {

		/**
		 * Allow cached releases to be used.
		 */
		CACHED,

		/**
		 * Do not serve an already-cached result, but still fetch through the cache's
		 * incremental {@link FetchPlan}, which may skip sources that are already up to
		 * date.
		 */
		REFRESH,

		/**
		 * Ignore cached releases and the incremental fetch plan, perform a full fetch,
		 * and update the cache with the outcome.
		 */
		RESET;
	}

}
