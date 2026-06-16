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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Release;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CachedArtifact}.
 *
 * @author Mark Paluch
 */
class CachedArtifactUnitTests {

	private static final ArtifactId ARTIFACT_ID = ArtifactId.of("io.lettuce", "lettuce-core");

	@Test
	void roundTripsEmptyReleaseSources() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);

		updateReleases(artifact, List.of(), 1_000L, Set.of("central", "portal"), FetchPlan.fullFetch());

		assertThat(artifact.getEmptyReleaseSources()).containsExactlyInAnyOrder("central", "portal");
	}

	@Test
	void reportsNoEmptyReleaseSourcesWhenNoneRecorded() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);

		updateReleases(artifact, List.of(), 1_000L, Set.of(), FetchPlan.fullFetch());

		assertThat(artifact.getEmptyReleaseSources()).isEmpty();
	}

	@Test
	void addsEmptySourcesAdditively() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);

		updateReleases(artifact, List.of(), 1_000L, Set.of("central"), FetchPlan.fullFetch());
		updateReleases(artifact, List.of(), 2_000L, Set.of("portal"), FetchPlan.partial());

		assertThat(artifact.getEmptyReleaseSources()).containsExactlyInAnyOrder("central", "portal");
	}

	@Test
	void dropsSourcesThatProduceReleases() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		updateReleases(artifact, List.of(), 1_000L, Set.of("central", "portal"), FetchPlan.fullFetch());

		updateReleases(artifact, List.of(CachedRelease.from(Release.of("1.0.0"))), 2_000L, Set.of("portal"),
				FetchPlan.fullFetch());

		assertThat(artifact.getEmptyReleaseSources()).containsExactly("portal");
	}

	@Test
	void carriesEmptyReleaseSourcesIntoSnapshot() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		updateReleases(artifact, List.of(), 1_000L, Set.of("central"), FetchPlan.fullFetch());

		assertThat(artifact.snapshot().getEmptyReleaseSources()).containsExactly("central");
	}

	@Test
	void countsConsecutiveEmptyLookups() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);

		updateReleases(artifact, List.of(), 1_000L, Set.of("central"), FetchPlan.fullFetch());
		updateReleases(artifact, List.of(), 2_000L, Set.of("central"), FetchPlan.fullFetch());

		assertThat(artifact.getEmptyLookups()).isEqualTo(2);
	}

	@Test
	void resetsEmptyLookupsWhenReleasesReturn() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		updateReleases(artifact, List.of(), 1_000L, Set.of("central"), FetchPlan.fullFetch());

		updateReleases(artifact, List.of(CachedRelease.from(Release.of("1.0.0"))), 2_000L, Set.of(),
				FetchPlan.fullFetch());

		assertThat(artifact.getEmptyLookups()).isZero();
	}

	@Test
	void advancesCheckClockOnlyOnFullQuery() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);

		updateReleases(artifact, List.of(), 1_000L, Set.of("central"), FetchPlan.fullFetch());
		assertThat(artifact.getSourcesCheckedSince()).isEqualTo(1_000L);

		updateReleases(artifact, List.of(), 2_000L, Set.of("central"), FetchPlan.skip());
		assertThat(artifact.getSourcesCheckedSince()).isEqualTo(1_000L);

		updateReleases(artifact, List.of(), 3_000L, Set.of("central"), FetchPlan.fullFetch());
		assertThat(artifact.getSourcesCheckedSince()).isEqualTo(3_000L);
	}

	@Test
	void keepsCheckClockWhenReleasesReturnWithoutFullQuery() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		updateReleases(artifact, List.of(), 1_000L, Set.of("central"), FetchPlan.fullFetch());
		updateReleases(artifact, List.of(CachedRelease.from(Release.of("1.0.0"))), 2_000L, Set.of(), FetchPlan.skip());

		assertThat(artifact.getSourcesCheckedSince()).isEqualTo(1_000L);
	}

	private static void updateReleases(CachedArtifact artifact, List<CachedRelease> releases, long timestamp,
			Collection<String> emptySources, FetchPlan plan) {
		artifact.updateCachedReleases(new FetchedReleases(ARTIFACT_ID, releases, plan, null, emptySources), timestamp);
	}

}
