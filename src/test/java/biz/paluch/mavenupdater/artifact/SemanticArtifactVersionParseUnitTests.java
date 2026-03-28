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
package biz.paluch.mavenupdater.artifact;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link SemanticArtifactVersion}.
 */
class SemanticArtifactVersionParseUnitTests {

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

		SemanticArtifactVersion v = SemanticArtifactVersion.of("5.0.0-B02");

		assertThat(v.getSuffix()).isEqualTo("B2");
		assertThat(v.toString()).isEqualTo("5.0.0-B02");
	}

	@Test
	void correctlyOrdersSemanticVersions() {

		assertThat(SemanticArtifactVersion.of("5.0.0-B02")).isLessThan(SemanticArtifactVersion.of("5.0.0-B03"))
				.isGreaterThan(SemanticArtifactVersion.of("5.0.0-B01"));
		assertThat(SemanticArtifactVersion.of("5.0.0-B02")).isLessThan(SemanticArtifactVersion.of("5.0.0-RC1"));
	}

	@Test
	void parsesFinalAndPlainRelease() {

		assertThat(SemanticArtifactVersion.isVersion("7.2.4.Final")).isTrue();
		SemanticArtifactVersion finalV = SemanticArtifactVersion.of("7.2.4.Final");
		assertThat(finalV.isReleaseVersion()).isTrue();
		assertThat(finalV).hasToString("7.2.4.Final");

		assertThat(SemanticArtifactVersion.isVersion("1.4.5")).isTrue();
		SemanticArtifactVersion plain = SemanticArtifactVersion.of("1.4.5");
		assertThat(plain.isReleaseVersion()).isTrue();
		assertThat(plain).hasToString("1.4.5");
		assertThat(plain.getNextDevelopmentVersion()).isEqualTo(SemanticArtifactVersion.of("1.4.6-SNAPSHOT"));
	}

	@Test
	void parsesSnapshot() {

		assertThat(SemanticArtifactVersion.isVersion("1.4.5-SNAPSHOT")).isTrue();
		SemanticArtifactVersion v = SemanticArtifactVersion.of("1.4.5-SNAPSHOT");
		assertThat(v.isReleaseVersion()).isFalse();
		assertThat(v.isSnapshotVersion()).isTrue();
		assertThat(v).hasToString("1.4.5-SNAPSHOT");
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(
			strings = { "12.1.3.0_special_74723", "9.4-1205-jdbc42", "13.3.1.jre8-preview", "11.1.0-SNAPSHOT.jre8-preview",
					"1.5.1-native-mt", "0.0.0.1.3.0-HATEOAS-1417-SNAPSHOT.1", "0.1.0.20091028042923", "1.0" })
	void parsesComplexVersionsWithRoundTripToString(String version) {

		assertThat(SemanticArtifactVersion.of(version)).hasToString(version);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "1.4.5.M1", "1.0.0-alpha-1", "2.1.0-alpha0", "2.0.64-beta", "1.0.0-beta.11" })
	void parsesMilestone(String version) {

		SemanticArtifactVersion v = SemanticArtifactVersion.of(version);
		assertThat(v.isReleaseVersion()).isFalse();
		assertThat(v.isMilestoneVersion()).isTrue();
		assertThat(v).hasToString(version);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "1.15.0-rc1", "1.15.0-rc", "1.15.0-RC1" })
	void parsesReleaseCandidate(String version) {

		SemanticArtifactVersion v = SemanticArtifactVersion.of(version);
		assertThat(v.isReleaseVersion()).isFalse();
		assertThat(v.isMilestoneVersion()).isFalse();
		assertThat(v.isReleaseCandidateVersion()).isTrue();
		assertThat(v).hasToString(version);
	}

	@Test
	void odersBetasAndRcAndReleases() {

		assertThat(SemanticArtifactVersion.of("1.0.0-beta.11")).isGreaterThan(SemanticArtifactVersion.of("1.0.0-beta.2"));

		assertThat(SemanticArtifactVersion.of("1.9.0.RELEASE")).isLessThan(SemanticArtifactVersion.of("1.10.0.RELEASE"));
		assertThat(SemanticArtifactVersion.of("1.9.25.1.RELEASE"))
				.isLessThan(SemanticArtifactVersion.of("1.9.25.2.RELEASE"));
		assertThat(SemanticArtifactVersion.of("1.9.10.RELEASE")).isLessThan(SemanticArtifactVersion.of("1.9.11.RELEASE"))
				.isGreaterThan(SemanticArtifactVersion.of("1.9.2.RELEASE"));
		assertThat(SemanticArtifactVersion.of("1.9.0-M2")).isLessThan(SemanticArtifactVersion.of("1.9.0-M3"))
				.isGreaterThan(SemanticArtifactVersion.of("1.9.0-M1"));
		assertThat(SemanticArtifactVersion.of("1.9.0-M2")).isGreaterThan(SemanticArtifactVersion.of("1.9.0-SNAPSHOT"))
				.isLessThan(SemanticArtifactVersion.of("1.9.0-RC1")).isLessThan(SemanticArtifactVersion.of("1.9.0"));

		assertThat(SemanticArtifactVersion.of("1.9.0.M1")).isLessThan(SemanticArtifactVersion.of("1.9.0"))
				.isLessThan(SemanticArtifactVersion.of("1.9.0.RELEASE")).isLessThan(SemanticArtifactVersion.of("1.9.0-SR1"));
		assertThat(SemanticArtifactVersion.of("1.9.0.RELEASE")).isGreaterThan(SemanticArtifactVersion.of("1.9.0.M1"));
	}

	@ParameterizedTest
	@CsvSource({ "1.0.0, 1.0.1-SNAPSHOT", "1.0.0-M1, 1.0.0-SNAPSHOT", "1.0.1, 1.0.2-SNAPSHOT" })
	void nextBugfixVersion(String current, String expected) {

		SemanticArtifactVersion v = SemanticArtifactVersion.of(current);
		assertThat(v.getNextBugfixVersion()).isEqualTo(SemanticArtifactVersion.of(expected));
	}

	@Test
	void nextDevelopmentVersionForGa() {

		SemanticArtifactVersion v = SemanticArtifactVersion.of("1.5.0");
		assertThat(v.getNextDevelopmentVersion().isMilestoneVersion()).isFalse();
		assertThat(v.getNextDevelopmentVersion().isReleaseVersion()).isFalse();
		assertThat(v.getNextDevelopmentVersion()).isEqualTo(SemanticArtifactVersion.of("1.6.0-SNAPSHOT"));
	}

}
