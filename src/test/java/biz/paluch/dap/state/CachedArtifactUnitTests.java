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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CachedArtifact}.
 *
 * @author Mark Paluch
 */
class CachedArtifactUnitTests {

	private static final ArtifactId ARTIFACT_ID = ArtifactId.of("io.lettuce", "lettuce-core");

	private static final ArtifactId NETTY_BOM = ArtifactId.of("io.netty", "netty-bom");

	private static final ArtifactId CODEC_HTTP = ArtifactId.of("io.netty", "netty-codec-http");

	@Test
	void predictsReleaseTrainMembersForUnknownBomVersion() {

		CachedArtifact bom = new CachedArtifact(NETTY_BOM);
		bom.setBillOfMaterials(BillOfMaterials.of(NETTY_BOM, ArtifactVersion.of("4.1.100"), Map.of(
				CODEC_HTTP, ArtifactVersion.of("4.1.100"),
				ArtifactId.of("io.netty.incubator", "netty-incubator-codec-quic"), ArtifactVersion.of("4.1.100"),
				ArtifactId.of("io.netty", "netty-tcnative"), ArtifactVersion.of("2.0.61"),
				ArtifactId.of("org.example", "coincidence"), ArtifactVersion.of("4.1.100"))));

		assertThat(bom.predictBom(ArtifactVersion.of("4.1.108"))).containsOnly(
				entry(CODEC_HTTP, ArtifactVersion.of("4.1.108")),
				entry(ArtifactId.of("io.netty.incubator", "netty-incubator-codec-quic"),
						ArtifactVersion.of("4.1.108")));
	}

	@Test
	void predictBomReturnsEmptyWithoutCachedMembership() {

		CachedArtifact bom = new CachedArtifact(NETTY_BOM);

		assertThat(bom.predictBom(ArtifactVersion.of("4.1.108"))).isEmpty();
	}

	@Test
	void predictBomUsesNearestCachedMembership() {

		ArtifactId oldMember = ArtifactId.of("io.netty", "netty-old");
		ArtifactId newMember = ArtifactId.of("io.netty", "netty-new");

		CachedArtifact bom = new CachedArtifact(NETTY_BOM);
		bom.setBillOfMaterials(BillOfMaterials.of(NETTY_BOM, ArtifactVersion.of("1.0.0"),
				Map.of(oldMember, ArtifactVersion.of("1.0.0"))));
		bom.setBillOfMaterials(BillOfMaterials.of(NETTY_BOM, ArtifactVersion.of("2.0.0"),
				Map.of(newMember, ArtifactVersion.of("2.0.0"))));

		assertThat(bom.predictBom(ArtifactVersion.of("1.5.0")))
				.containsOnly(entry(oldMember, ArtifactVersion.of("1.5.0")));
		assertThat(bom.predictBom(ArtifactVersion.of("3.0.0")))
				.containsOnly(entry(newMember, ArtifactVersion.of("3.0.0")));
		assertThat(bom.predictBom(ArtifactVersion.of("0.5.0")))
				.containsOnly(entry(oldMember, ArtifactVersion.of("0.5.0")));
	}

	@Test
	void updateCachedReleasesRetainsReleasesAbsentFromTheFetch() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		artifact.addRelease(new CachedRelease("0.9.0", null));

		updateReleases(artifact,
				List.of(CachedRelease.from(Release.of("1.0.0")), CachedRelease.from(Release.of("1.1.0"))),
				2_000L, Set.of(), FetchPlan.fullFetch());

		assertThat(artifact.getReleases()).extracting(CachedRelease::version)
				.containsExactlyInAnyOrder("0.9.0", "1.0.0", "1.1.0");
	}

	@Test
	void updateCachedReleasesNotifiesOnlyNewReleases() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		artifact.addRelease(new CachedRelease("1.0.0", null));

		List<String> added = new ArrayList<>();
		artifact.updateCachedReleases(new FetchedReleases(ARTIFACT_ID,
				List.of(CachedRelease.from(Release.of("1.0.0")), CachedRelease.from(Release.of("1.1.0"))),
				FetchPlan.fullFetch(), null, Set.of()), 2_000L,
				(release, cached) -> added.add(release.version().toString()));

		assertThat(added).containsExactly("1.1.0");
	}

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
	void roundTripsEcosystemThroughXmlSerialization() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		artifact.setPackageSystem(PackageSystem.NPM);

		Element element = XmlSerializer.serialize(artifact);
		CachedArtifact deserialized = XmlSerializer.deserialize(element, CachedArtifact.class);

		assertThat(deserialized.getPackageSystem()).isEqualTo(PackageSystem.NPM);
	}

	@Test
	void deserializedArtifactWithoutEcosystemReadsNull() {

		Element element = XmlSerializer.serialize(new CachedArtifact(ARTIFACT_ID));
		CachedArtifact deserialized = XmlSerializer.deserialize(element, CachedArtifact.class);

		assertThat(deserialized.getPackageSystem()).isNull();
	}

	@Test
	void carriesEcosystemIntoSnapshot() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		artifact.setPackageSystem(PackageSystem.MAVEN);

		assertThat(artifact.snapshot().getPackageSystem()).isEqualTo(PackageSystem.MAVEN);
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
