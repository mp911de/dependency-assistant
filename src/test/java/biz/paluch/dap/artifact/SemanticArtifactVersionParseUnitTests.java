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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SemanticArtifactVersion}.
 */
class SemanticArtifactVersionParseUnitTests {

	static SemanticArtifactVersion version(String source) {
		return SemanticArtifactVersion.of(source);
	}

	@Test
	void parsesHyphenBetaBuildQualifierWithNumericTail() {

		assertThat(SemanticArtifactVersion.isVersion("5.0.0-B02")).isTrue();

		ArtifactVersion v = ArtifactVersion.of("5.0.0-B02");
		assertThat(v).isInstanceOf(SemanticArtifactVersion.class);
		assertThat(v.isMilestoneVersion()).isTrue();
		assertThat(v.isReleaseCandidateVersion()).isFalse();
		assertThat(v.toString()).startsWith("5.0.0-");
		assertThat(v.toString()).contains("B");
	}

	@Test
	void semverPathPassesFullQualifierToSuffixParse() {

		SemanticArtifactVersion v = version("5.0.0-B02");

		assertThat(v.getSuffix()).isEqualTo("B2");
		assertThat(v.toString()).isEqualTo("5.0.0-B02");
	}

	@Test
	void correctlyOrdersSemanticVersions() {

		assertThat(version("5.0.0-B02")).isLessThan(version("5.0.0-B03"))
				.isGreaterThan(version("5.0.0-B01"));
		assertThat(version("5.0.0-B02")).isLessThan(version("5.0.0-RC1"));
	}

	@Test
	void parsesFinalAndPlainRelease() {

		assertThat(SemanticArtifactVersion.isVersion("7.2.4.Final")).isTrue();
		SemanticArtifactVersion finalV = version("7.2.4.Final");
		assertThat(finalV.isReleaseVersion()).isTrue();
		assertThat(finalV).hasToString("7.2.4.Final");

		assertThat(SemanticArtifactVersion.isVersion("1.4.5")).isTrue();
		SemanticArtifactVersion plain = version("1.4.5");
		assertThat(plain.isReleaseVersion()).isTrue();
		assertThat(plain).hasToString("1.4.5");
		assertThat(plain.getNextDevelopmentVersion()).isEqualTo(version("1.4.6-SNAPSHOT"));
	}

	@Test
	void parsesSnapshot() {

		assertThat(SemanticArtifactVersion.isVersion("1.4.5-SNAPSHOT")).isTrue();
		SemanticArtifactVersion v = version("1.4.5-SNAPSHOT");
		assertThat(v.isReleaseVersion()).isFalse();
		assertThat(v.isSnapshotVersion()).isTrue();
		assertThat(v).hasToString("1.4.5-SNAPSHOT");
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"v1.2.3, true, false", "v1.0.0-beta.1, false, true", "v2026.4.0-rc.1, false, true"})
	void semanticArtifactVersionOfNormalizesVPrefixInternally(String source, boolean release, boolean preview) {

		SemanticArtifactVersion version = version(source);

		assertThat(version).hasToString(source);
		assertThat(version.isReleaseVersion()).isEqualTo(release);
		assertThat(version.isPreview()).isEqualTo(preview);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"v1.2.3, 1.2.3, true, false", "v1.0.0-beta.1, 1.0.0-beta.1, false, true",
			"v2026.4.0-rc.1, 2026.4.0-rc.1, false, true"})
	void artifactVersionOfWrapsVPrefixedVersions(String source, String inner, boolean release, boolean preview) {

		ArtifactVersion version = ArtifactVersion.of(source);

		assertThat(version.isWrapped()).isTrue();
		assertThat(version).hasToString(source);
		assertThat(version.unwrap()).hasToString(inner);
		assertThat(version.isReleaseVersion()).isEqualTo(release);
		assertThat(version.isPreview()).isEqualTo(preview);
	}

	@Test
	void artifactVersionOfDoesNotWrapNonPrefixedVersions() {

		ArtifactVersion version = ArtifactVersion.of("1.2.3");

		assertThat(version.isWrapped()).isFalse();
		assertThat(version.unwrap()).isSameAs(version);
	}

	@Test
	void prefixedVersionComparesWithUnprefixedVersion() {

		ArtifactVersion prefixed = ArtifactVersion.of("v1.2.3");
		ArtifactVersion plain = ArtifactVersion.of("1.2.3");
		ArtifactVersion newer = ArtifactVersion.of("2.0.0");

		assertThat(prefixed.compareTo(plain)).isZero();
		assertThat(plain.compareTo(prefixed)).isZero();
		assertThat(prefixed.compareTo(newer)).isNegative();
		assertThat(newer.compareTo(prefixed)).isPositive();
	}

	@Test
	void twoPrefixedVersionsCompareByNumericValue() {

		ArtifactVersion v1 = ArtifactVersion.of("v1.2.3");
		ArtifactVersion v2 = ArtifactVersion.of("v2.0.0");

		assertThat(v1.compareTo(v2)).isNegative();
		assertThat(v2.compareTo(v1)).isPositive();
		assertThat(v1.compareTo(ArtifactVersion.of("v1.2.3"))).isZero();
	}

	@Test
	void canCompareReturnsTrueForPrefixedAndUnprefixedVersionsOfSameType() {

		ArtifactVersion prefixed = ArtifactVersion.of("v1.2.3");
		ArtifactVersion plain = ArtifactVersion.of("1.2.3");

		assertThat(prefixed.canCompare(plain)).isTrue();
		assertThat(plain.canCompare(prefixed)).isTrue();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"1.0.0-dev", "1.0.0-dev.5", "1.0.0-nightly.20260426", "1.0.0-canary",
			"1.0.0-pre", "1.0.0-pre.1", "1.0.0-next", "1.0.0-next.20260426", "1.0.0-preview",
			"1.0.0-preview.3", "0.0.0-experimental"})
	void parsesAdditionalPreviewKeywords(String source) {

		SemanticArtifactVersion version = version(source);

		assertThat(version.isMilestoneVersion()).isTrue();
		assertThat(version.isReleaseCandidateVersion()).isFalse();
		assertThat(version.isPreview()).isTrue();
		assertThat(version).hasToString(source);
	}

	@Test
	void ordersAdditionalPreviewKeywordsByStability() {

		assertThat(version("1.0.0-dev")).isLessThan(version("1.0.0-nightly"));
		assertThat(version("1.0.0-nightly")).isLessThan(version("1.0.0-canary"));
		assertThat(version("1.0.0-dev")).isLessThan(version("1.0.0-alpha"));
		assertThat(version("1.0.0-alpha")).isLessThan(version("1.0.0-beta"));
		assertThat(version("1.0.0-beta")).isLessThan(version("1.0.0-rc.1"));
		assertThat(version("1.0.0-next")).isLessThan(version("1.0.0-rc.1"));
		assertThat(version("1.0.0-rc.1")).isLessThan(version("1.0.0"));
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"1.0.0-alpha.1+sha.abc1234, 1.0.0-alpha.1, true",
			"1.0.0+build.42, 1.0.0, false", "1.0.0-beta.2+20260426, 1.0.0-beta.2, true"})
	void ignoresBuildMetadataForComparison(String source, String expectedEqual, boolean preview) {

		SemanticArtifactVersion version = version(source);
		SemanticArtifactVersion equivalent = version(expectedEqual);

		assertThat(version).hasToString(source);
		assertThat(version.isPreview()).isEqualTo(preview);
		assertThat(version.compareTo(equivalent)).isZero();
		assertThat(version).isEqualTo(equivalent);
		assertThat(version).hasSameHashCodeAs(equivalent);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"1.0.0-alpha.0.3, true, false", "1.0.0-rc.1.2, false, true",
			"1.0.0-alpha.20260426.1, true, false"})
	void parsesMultiSegmentPreReleaseIdentifiers(String source, boolean milestone, boolean releaseCandidate) {

		SemanticArtifactVersion version = version(source);

		assertThat(version.isMilestoneVersion()).isEqualTo(milestone);
		assertThat(version.isReleaseCandidateVersion()).isEqualTo(releaseCandidate);
		assertThat(version.isPreview()).isTrue();
		assertThat(version).hasToString(source);
	}

	@Test
	void ordersMultiSegmentPreReleaseIdentifiers() {

		assertThat(version("1.0.0-alpha.0.3")).isLessThan(version("1.0.0-alpha.1.0"));
		assertThat(version("1.0.0-rc.1.2")).isLessThan(version("1.0.0-rc.2.0"));
		assertThat(version("1.0.0-rc.1.2")).isLessThan(version("1.0.0"));
	}

	@Test
	void parsesNumericPreReleaseIdentifiers() {

		assertThat(version("1.0.0-0").isPreview()).isTrue();
		assertThat(version("1.0.0-0").isMilestoneVersion()).isTrue();
		assertThat(version("1.0.0-1").isPreview()).isTrue();
		assertThat(version("1.0.0-1").isMilestoneVersion()).isTrue();
		assertThat(version("1.0.0-0.3.7").isPreview()).isTrue();
		assertThat(version("1.0.0-0.3.7").isMilestoneVersion()).isTrue();
	}

	@Test
	void ordersNumericPreReleaseIdentifiers() {

		assertThat(version("1.0.0-0")).isLessThan(version("1.0.0-1")).isLessThan(version("1.0.0"));
		assertThat(version("1.0.0-0.3.7")).isLessThan(version("1.0.0-0.3.8")).isLessThan(version("1.0.0"));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(
			strings = { "12.1.3.0_special_74723", "9.4-1205-jdbc42", "13.3.1.jre8-preview", "11.1.0-SNAPSHOT.jre8-preview",
					"1.5.1-native-mt", "0.0.0.1.3.0-HATEOAS-1417-SNAPSHOT.1", "0.1.0.20091028042923", "1.0" })
	void parsesComplexVersionsWithRoundTripToString(String version) {

		assertThat(version(version)).hasToString(version);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "1.4.5.M1", "1.0.0-alpha-1", "2.1.0-alpha0", "2.0.64-beta", "1.0.0-beta.11" })
	void parsesMilestone(String version) {

		SemanticArtifactVersion v = version(version);
		assertThat(v.isReleaseVersion()).isFalse();
		assertThat(v.isMilestoneVersion()).isTrue();
		assertThat(v).hasToString(version);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "1.15.0-rc1", "1.15.0-rc", "1.15.0-RC1" })
	void parsesReleaseCandidate(String version) {

		SemanticArtifactVersion v = version(version);
		assertThat(v.isReleaseVersion()).isFalse();
		assertThat(v.isMilestoneVersion()).isFalse();
		assertThat(v.isReleaseCandidateVersion()).isTrue();
		assertThat(v).hasToString(version);
	}

	@Test
	void odersBetasAndRcAndReleases() {

		assertThat(version("1.0.0-beta.11")).isGreaterThan(version("1.0.0-beta.2"));

		assertThat(version("1.9.0.RELEASE")).isLessThan(version("1.10.0.RELEASE"));
		assertThat(version("1.9.25.1.RELEASE"))
				.isLessThan(version("1.9.25.2.RELEASE"));
		assertThat(version("1.9.10.RELEASE")).isLessThan(version("1.9.11.RELEASE"))
				.isGreaterThan(version("1.9.2.RELEASE"));
		assertThat(version("1.9.0-M2")).isLessThan(version("1.9.0-M3"))
				.isGreaterThan(version("1.9.0-M1"));
		assertThat(version("1.9.0-M2")).isGreaterThan(version("1.9.0-SNAPSHOT"))
				.isLessThan(version("1.9.0-RC1")).isLessThan(version("1.9.0"));

		assertThat(version("1.9.0.M1")).isLessThan(version("1.9.0"))
				.isLessThan(version("1.9.0.RELEASE")).isLessThan(version("1.9.0-SR1"));
		assertThat(version("1.9.0.RELEASE")).isGreaterThan(version("1.9.0.M1"));
	}

	@ParameterizedTest
	@CsvSource({ "1.0.0, 1.0.1-SNAPSHOT", "1.0.0-M1, 1.0.0-SNAPSHOT", "1.0.1, 1.0.2-SNAPSHOT" })
	void nextBugfixVersion(String current, String expected) {

		SemanticArtifactVersion v = version(current);
		assertThat(v.getNextBugfixVersion()).isEqualTo(version(expected));
	}

	@Test
	void nextDevelopmentVersionForGa() {

		SemanticArtifactVersion v = version("1.5.0");
		assertThat(v.getNextDevelopmentVersion().isMilestoneVersion()).isFalse();
		assertThat(v.getNextDevelopmentVersion().isReleaseVersion()).isFalse();
		assertThat(v.getNextDevelopmentVersion()).isEqualTo(version("1.6.0-SNAPSHOT"));
	}

}
