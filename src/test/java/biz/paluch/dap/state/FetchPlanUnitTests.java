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
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.ReleaseSources;
import biz.paluch.dap.fixtures.TestReleaseSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FetchPlan}.
 *
 * @author Mark Paluch
 */
class FetchPlanUnitTests {

	static final Instant NOW = Instant.parse("2026-06-16T00:00:00Z");

	static final long NOW_MILLIS = NOW.toEpochMilli();

	static final ArtifactId ARTIFACT_ID = ArtifactId.of("io.lettuce", "lettuce-core");

	static final List<String> SOURCES = List.of("central", "portal");

	static final long STALE_TIMESTAMP = NOW_MILLIS - Duration.ofDays(31)
			.toMillis();

	Cache cache = new Cache(Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void fetchPlanShouldFetchEverySourceForUnknownArtifact() {

		FetchPlan plan = cache.createFetchPlan(releaseSources("central", "portal"));

		assertThat(plan.isSkip()).isFalse();
		assertThat(plan.isFullFetch()).isTrue();
		assertThat(plan).accepts("central", "portal");
	}

	@Test
	void fetchedReleasesShouldFetchEverySourceWithoutEmptySources() {

		releasesWithEmptySources(NOW_MILLIS, Set.of());
		FetchPlan plan = cache.createFetchPlan(releaseSources("central", "portal"));

		assertThat(plan.isFullFetch()).isTrue();
		assertThat(plan).accepts("central", "portal");
	}

	@Test
	void fetchedReleasesShouldTrimEmptySourceWithinWindow() {

		releasesWithEmptySources(NOW_MILLIS, Set.of("internal"));
		FetchPlan plan = cache.createFetchPlan(releaseSources("central", "portal", "internal"));

		assertThat(plan.isFullFetch()).isFalse();
		assertThat(plan).rejects("internal").accepts("central", "portal");
	}

	@Test
	void fetchedReleasesShouldReCheckDeadSourceAfterWindow() {

		releasesWithEmptySources(STALE_TIMESTAMP, Set.of("internal"));
		FetchPlan plan = cache.createFetchPlan(releaseSources("central", "portal", "internal"));

		assertThat(plan.isFullFetch()).isTrue();
		assertThat(plan).accepts("central", "portal", "internal");
	}

	@Test
	void fetchedReleasesShouldFetchEverySourceWhenAllKnownEmpty() {

		releasesWithEmptySources(NOW_MILLIS, Set.of("central", "portal"));
		FetchPlan plan = cache.createFetchPlan(releaseSources("central", "portal"));

		assertThat(plan.isFullFetch()).isTrue();
		assertThat(plan).accepts("central", "portal");
	}

	@Test
	void fetchPlanShouldNarrowToPreferredSourceWhenConfigured() {

		CachedArtifact artifact = releasesWithEmptySources(NOW_MILLIS, Set.of());
		artifact.setPreferredSource("central");
		FetchPlan plan = cache.createFetchPlan(releaseSources("central", "portal"));

		assertThat(plan).accepts("central").rejects("portal");
	}

	@Test
	void fetchPlanShouldIgnorePreferredSourceNoLongerPresent() {

		CachedArtifact artifact = releasesWithEmptySources(NOW_MILLIS, Set.of("internal"));
		artifact.setPreferredSource("removed");
		FetchPlan plan = cache.createFetchPlan(releaseSources("central", "portal", "internal"));

		assertThat(plan).accepts("central", "portal").rejects("internal");
	}

	@Test
	void discoveryFetchPlanShouldFetchEverySourceBelowThreshold() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		artifact.updateCachedReleases(new FetchedReleases(ARTIFACT_ID, List.of(), FetchPlan.fullFetch(), null, SOURCES),
				NOW_MILLIS);
		cache.addArtifacts(List.of(artifact));

		assertThat(cache.createFetchPlan(releaseSources("central", "portal")).isFullFetch()).isTrue();
	}

	@Test
	void discoveryFetchPlanShouldSkipWhenAllSourcesKnownEmpty() {
		backedOff(NOW_MILLIS);
		assertThat(cache.createFetchPlan(releaseSources("central", "portal")).isSkip()).isTrue();
	}

	@Test
	void discoveryFetchPlanShouldFetchNewSourceAloneWhenUnknownAppears() {

		backedOff(NOW_MILLIS);
		FetchPlan plan = cache.createFetchPlan(releaseSources("central", "portal", "github"));

		assertThat(plan.isSkip()).isFalse();
		assertThat(plan.isFullFetch()).isFalse();
		assertThat(plan).accepts("github").rejects("central", "portal");
	}

	@Test
	void discoveryFetchPlanShouldFetchEverySourceAfterStaleWindow() {
		backedOff(STALE_TIMESTAMP);
		assertThat(cache.createFetchPlan(releaseSources("central", "portal")).isFullFetch()).isTrue();
	}

	@Test
	void staleRefreshShouldWinOverNewSourceBypass() {

		backedOff(STALE_TIMESTAMP);
		FetchPlan plan = cache.createFetchPlan(releaseSources("central", "portal", "github"));

		assertThat(plan.isFullFetch()).isTrue();
		assertThat(plan).accepts("central", "portal", "github");
	}

	@Test
	void backOffWindowShouldRestartAfterStillEmptyRefresh() {

		CachedArtifact artifact = backedOff(STALE_TIMESTAMP);

		assertThat(cache.createFetchPlan(releaseSources("central", "portal")).isFullFetch()).isTrue();

		artifact.updateCachedReleases(new FetchedReleases(ARTIFACT_ID, List.of(), FetchPlan.fullFetch(), null, SOURCES),
				NOW_MILLIS);

		assertThat(cache.createFetchPlan(releaseSources("central", "portal")).isSkip()).isTrue();
	}

	private CachedArtifact releasesWithEmptySources(long checkedSince, Set<String> emptySources) {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		artifact.updateCachedReleases(
				new FetchedReleases(ARTIFACT_ID, List.of(CachedRelease.from(Release.of("1.0.0"))),
						FetchPlan.fullFetch(), null, emptySources),
				checkedSince);
		cache.addArtifacts(List.of(artifact));
		return artifact;
	}

	/**
	 * Drive an artifact past the empty-lookup threshold with {@link #SOURCES} all
	 * empty. Every empty lookup is a full query stamped at
	 * {@code lastEmptyTimestamp}, so the re-check window ends there.
	 */
	private CachedArtifact backedOff(long lastEmptyTimestamp) {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		for (int i = 0; i < 4; i++) {
			artifact.updateCachedReleases(
					new FetchedReleases(ARTIFACT_ID, List.of(), FetchPlan.fullFetch(), null, SOURCES),
					lastEmptyTimestamp);
		}
		cache.addArtifacts(List.of(artifact));
		return artifact;
	}

	private static ReleaseSources releaseSources(String... ids) {
		return new ReleaseSources(ARTIFACT_ID, PackageSystem.MAVEN,
				Arrays.stream(ids).<ReleaseSource>map(TestReleaseSource::new).toList());
	}

}
