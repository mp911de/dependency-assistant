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
import java.util.Objects;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.fixtures.Coordinates;
import biz.paluch.dap.fixtures.TestFetchedReleases;
import biz.paluch.dap.fixtures.TestVulnerabilities;
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

	private static final Coordinates OLD_MEMBER = Coordinates.of("io.netty:netty-old:1.0.0");

	private static final Coordinates NEW_MEMBER = Coordinates.of("io.netty:netty-new:2.0.0");

	@Test
	void predictsReleaseTrainMembersForUnknownBomVersion() {

		Coordinates codecHttp = Coordinates.of("io.netty:netty-codec-http:4.1.100");
		Coordinates incubatorQuic = Coordinates.of("io.netty.incubator:netty-incubator-codec-quic:4.1.100");
		CachedArtifact bom = new CachedArtifact(NETTY_BOM);
		bom.setBillOfMaterials(Coordinates.bom("io.netty:netty-bom:4.1.100", it -> {
			it.member("io.netty:netty-codec-http:4.1.100");
			it.member("io.netty.incubator:netty-incubator-codec-quic:4.1.100");
			it.member("io.netty:netty-tcnative:2.0.61");
			it.member("org.example:coincidence:4.1.100");
		}));

		assertThat(bom.predictBom(ArtifactVersion.of("4.1.108"))).containsOnly(
				entry(codecHttp.getArtifactId(), ArtifactVersion.of("4.1.108")),
				entry(incubatorQuic.getArtifactId(), ArtifactVersion.of("4.1.108")));
	}

	@Test
	void predictBomReturnsEmptyWithoutCachedMembership() {

		CachedArtifact bom = new CachedArtifact(NETTY_BOM);

		assertThat(bom.predictBom(ArtifactVersion.of("4.1.108"))).isEmpty();
	}

	@Test
	void predictBomUsesNearestCachedMembershipRegardlessOfInsertionOrder() {

		CachedArtifact bom = new CachedArtifact(NETTY_BOM);
		bom.setBillOfMaterials(
				Coordinates.bom("io.netty:netty-bom:2.0.0", it -> it.member("io.netty:netty-new:2.0.0")));
		bom.setBillOfMaterials(
				Coordinates.bom("io.netty:netty-bom:1.0.0", it -> it.member("io.netty:netty-old:1.0.0")));

		assertThat(bom.predictBom(ArtifactVersion.of("1.5.0")))
				.containsOnly(entry(OLD_MEMBER.getArtifactId(), ArtifactVersion.of("1.5.0")));
		assertThat(bom.predictBom(ArtifactVersion.of("3.0.0")))
				.containsOnly(entry(NEW_MEMBER.getArtifactId(), ArtifactVersion.of("3.0.0")));
		assertThat(bom.predictBom(ArtifactVersion.of("0.5.0")))
				.containsOnly(entry(OLD_MEMBER.getArtifactId(), ArtifactVersion.of("0.5.0")));
	}

	@Test
	void returnsBomMembershipsInVersionOrder() {

		CachedArtifact bom = new CachedArtifact(NETTY_BOM);
		bom.setBillOfMaterials(
				Coordinates.bom("io.netty:netty-bom:2.0.0", it -> it.member("io.netty:netty-new:2.0.0")));
		bom.setBillOfMaterials(
				Coordinates.bom("io.netty:netty-bom:1.0.0", it -> it.member("io.netty:netty-old:1.0.0")));

		assertThat(bom.getBomMemberships()).extracting(CachedBom::getVersion)
				.extracting(Objects::toString)
				.containsExactly("1.0.0", "2.0.0");
	}

	@Test
	void updateCachedReleasesRetainsReleasesAbsentFromTheFetch() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		artifact.addRelease(new CachedRelease("0.9.0", null));

		updateReleases(artifact, 2_000L, Set.of(), FetchPlan.fullFetch(), "1.0.0", "1.1.0");

		assertThat(artifact.getReleases()).extracting(CachedRelease::version)
				.extracting(Objects::toString)
				.containsExactlyInAnyOrder("0.9.0", "1.0.0", "1.1.0");
	}

	@Test
	void updateCachedReleasesNotifiesOnlyNewReleases() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		artifact.addRelease(new CachedRelease("1.0.0", null));

		List<String> added = new ArrayList<>();
		artifact.updateReleases(TestFetchedReleases.of(ARTIFACT_ID, FetchPlan.fullFetch(), Set.of(), "1.0.0", "1.1.0"),
				2_000L, (release, cached) -> added.add(release.version().toString()));

		assertThat(added).containsExactly("1.1.0");
	}

	@Test
	void updateCachedReleasesPreservesVulnerabilityScan() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		CachedRelease existing = new CachedRelease("1.0.0", null);
		existing.setVulnerabilities(1_000L, List.of(TestVulnerabilities.HIGH_VULNERABILITY));
		artifact.addRelease(existing);

		updateReleases(artifact, 2_000L, Set.of(), FetchPlan.fullFetch(), "1.0.0");

		CachedRelease refreshed = artifact.getCachedRelease(ArtifactVersion.of("1.0.0"));
		assertThat(refreshed.getLastScanned()).isEqualTo(1_000L);
		assertThat(refreshed.toVulnerabilities()).containsExactly(TestVulnerabilities.HIGH_VULNERABILITY);
	}

	@Test
	void roundTripsReleaseBackOffStateThroughXmlSerialization() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		updateReleases(artifact, 1_000L, Set.of("central", "portal"), FetchPlan.fullFetch());

		Element element = XmlSerializer.serialize(artifact);
		CachedArtifact deserialized = XmlSerializer.deserialize(element, CachedArtifact.class);

		assertThat(deserialized.getEmptyReleaseSources()).containsExactlyInAnyOrder("central", "portal");
		assertThat(deserialized.getEmptyLookups()).isEqualTo(1);
		assertThat(deserialized.getSourcesCheckedSince()).isEqualTo(1_000L);
	}

	@Test
	void reportsNoEmptyReleaseSourcesWhenNoneRecorded() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);

		updateReleases(artifact, 1_000L, Set.of(), FetchPlan.fullFetch());

		assertThat(artifact.getEmptyReleaseSources()).isEmpty();
	}

	@Test
	void addsEmptySourcesAdditively() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);

		updateReleases(artifact, 1_000L, Set.of("central"), FetchPlan.fullFetch());
		updateReleases(artifact, 2_000L, Set.of("portal"), FetchPlan.partial());

		assertThat(artifact.getEmptyReleaseSources()).containsExactlyInAnyOrder("central", "portal");
	}

	@Test
	void dropsSourcesThatProduceReleases() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		updateReleases(artifact, 1_000L, Set.of("central", "portal"), FetchPlan.fullFetch());

		updateReleases(artifact, 2_000L, Set.of("portal"), FetchPlan.fullFetch(), "1.0.0");

		assertThat(artifact.getEmptyReleaseSources()).containsExactly("portal");
	}

	@Test
	void carriesEmptyReleaseSourcesIntoSnapshot() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		updateReleases(artifact, 1_000L, Set.of("central"), FetchPlan.fullFetch());

		assertThat(artifact.snapshot().getEmptyReleaseSources()).containsExactly("central");
	}

	@Test
	void countsConsecutiveEmptyLookups() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);

		updateReleases(artifact, 1_000L, Set.of("central"), FetchPlan.fullFetch());
		updateReleases(artifact, 2_000L, Set.of("central"), FetchPlan.fullFetch());

		assertThat(artifact.getEmptyLookups()).isEqualTo(2);
	}

	@Test
	void resetsEmptyLookupsWhenReleasesReturn() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);
		updateReleases(artifact, 1_000L, Set.of("central"), FetchPlan.fullFetch());

		updateReleases(artifact, 2_000L, Set.of(), FetchPlan.fullFetch(), "1.0.0");

		assertThat(artifact.getEmptyLookups()).isZero();
	}

	@Test
	void advancesCheckClockOnlyOnFullQuery() {

		CachedArtifact artifact = new CachedArtifact(ARTIFACT_ID);

		updateReleases(artifact, 1_000L, Set.of("central"), FetchPlan.fullFetch());
		assertThat(artifact.getSourcesCheckedSince()).isEqualTo(1_000L);

		updateReleases(artifact, 2_000L, Set.of("central"), FetchPlan.skip());
		assertThat(artifact.getSourcesCheckedSince()).isEqualTo(1_000L);

		updateReleases(artifact, 3_000L, Set.of("central"), FetchPlan.fullFetch());
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

	private static void updateReleases(CachedArtifact artifact, long timestamp, Collection<String> emptySources,
			FetchPlan plan, String... versions) {
		artifact.updateReleases(TestFetchedReleases.of(ARTIFACT_ID, plan, emptySources, versions), timestamp);
	}

}
