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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link SemanticArtifactVersion} and v-prefixed
 * {@link ArtifactVersion} wrapping.
 *
 * @author Mark Paluch
 */
class SemanticArtifactVersionParseUnitTests {

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"7.2.4.Final", "1.4.5", "1.0.0", "1.0.0.RELEASE"})
	void parsesRelease(String version) {
		assertThat(SemanticArtifactVersion.isVersion(version)).isTrue();
		assertThat(version(version)).isRelease().isNotPreview().hasToString(version);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"1.4.5-SNAPSHOT", "1.0.0-SNAPSHOT"})
	void parsesSnapshot(String version) {
		assertThat(version(version)).isSnapshot().isNotRelease().hasToString(version);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"5.0.0-B02", "1.4.5.M1", "1.0.0-alpha-1", "2.1.0-alpha0", "2.0.64-beta",
			"1.0.0-beta.11", "1.0.0-BETA2", "7.4.0.BETA1", "7.4.0.ALPHA1", "1.0.0-dev", "1.0.0-dev.5",
			"1.0.0-nightly.20260426", "1.0.0-canary", "1.0.0-pre",
			"1.0.0-pre.1", "1.0.0-next", "1.0.0-next.20260426", "1.0.0-preview", "1.0.0-preview.3",
			"0.0.0-experimental", "1.0.0-0", "1.0.0-1", "1.0.0-0.3.7", "1.0.0-alpha.0.3", "1.0.0-alpha.20260426.1"})
	void parsesMilestone(String version) {
		assertThat(version(version)).isMilestone().isNotReleaseCandidate().isPreview().isNotRelease()
				.hasToString(version);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"1.15.0-rc1", "1.15.0-rc", "1.15.0-RC1", "1.0.0-rc.1", "1.0.0-rc.1.2"})
	void parsesReleaseCandidate(String version) {
		assertThat(version(version)).isReleaseCandidate().isNotMilestone().isPreview().isNotRelease()
				.hasToString(version);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"12.1.3.0_special_74723", "9.4-1205-jdbc42", "13.3.1.jre8-preview",
			"11.1.0-SNAPSHOT.jre8-preview", "1.5.1-native-mt", "0.0.0.1.3.0-HATEOAS-1417-SNAPSHOT.1",
			"0.1.0.20091028042923", "1.0"})
	void parsesComplexVersions(String version) {
		assertThat(version(version)).hasToString(version);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"1.0.0-M99999999999", "1.0.0-RC99999999999", "1.0.0-SR99999999999",
			"1.0.0.M99999999999"})
	void parsesLargeCounters(String version) {
		assertThat(SemanticArtifactVersion.isVersion(version)).isTrue();
		assertThat(version(version)).hasToString(version);
	}

	@Test
	void parsesCanonicalSuffix() {
		assertThat(version("5.0.0-B02").getSuffix()).isEqualTo("B2");
	}

	@Test
	void shouldSortSuffixesIntoTotalOrder() {

		List<SemanticArtifactVersion> ascending = versions("1.0.0-SNAPSHOT",
				"1.0.0-dev", "1.0.0-nightly", "1.0.0-canary", "1.0.0-experimental",
				"1.0.0-alpha", "1.0.0-beta", "1.0.0-pre", "1.0.0-preview", "1.0.0-M1",
				"1.0.0-next", "1.0.0-rc.1", "1.0.0-cr1", "1.0.0-g_", "1.0.0", "1.0.0-SR1");

		assertThat(ascending).isSorted();

		// sorting the reversed copy reconstructs the canonical order only for a
		// consistent total order
		List<SemanticArtifactVersion> scrambled = new ArrayList<>(ascending);
		Collections.reverse(scrambled);
		Collections.sort(scrambled);

		assertThat(scrambled).containsExactlyElementsOf(ascending);
	}

	@Test
	void shouldOrderByNumericComponents() {
		assertThatVersion("1.9.0").isLessThan("1.10.0");
		assertThatVersion("1.9.10").isGreaterThan("1.9.2").isLessThan("1.9.11");
		assertThatVersion("1.9.25.1").isLessThan("1.9.25.2");
	}

	@Test
	void shouldOrderByCounter() {
		assertThatVersion("5.0.0-B02").isGreaterThan("5.0.0-B01").isLessThan("5.0.0-B03").isLessThan("5.0.0-RC1");
		assertThatVersion("1.9.0-M2").isGreaterThan("1.9.0-M1").isLessThan("1.9.0-M3");
		assertThatVersion("1.0.0-M10").isGreaterThan("1.0.0-M9").isLessThan("1.0.0-M99999999999");
		assertThatVersion("1.0.0-beta.11").isGreaterThan("1.0.0-beta.2");
	}

	@Test
	void shouldOrderNumericPreReleases() {
		assertThatVersion("1.0.0-1").isGreaterThan("1.0.0-0").isLessThan("1.0.0");
		assertThatVersion("1.0.0-0.3.8").isGreaterThan("1.0.0-0.3.7").isLessThan("1.0.0");
	}

	@Test
	void shouldOrderMultiSegmentPreReleases() {
		assertThatVersion("1.0.0-alpha.0.3").isLessThan("1.0.0-alpha.1.0");
		assertThatVersion("1.0.0-rc.2.0").isGreaterThan("1.0.0-rc.1.2").isLessThan("1.0.0");
	}

	@Test
	void shouldOrderGenericSuffixes() {
		assertThatVersion("1.0.0-gA").isNotEqualByComparingTo("1.0.0-ga");
		assertThatVersion("1.0.0-ga").isLessThan("1.0.0-gb");
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"1.0.0-alpha.1+sha.abc1234, 1.0.0-alpha.1, true", "1.0.0+build.42, 1.0.0, false",
			"1.0.0-beta.2+20260426, 1.0.0-beta.2, true"})
	void shouldIgnoreBuildMetadata(String source, String equivalent, boolean preview) {

		SemanticArtifactVersion version = version(source);

		assertThat(version).hasToString(source);
		assertThat(version.isPreview()).isEqualTo(preview);
		assertThat(version).isEqualByComparingTo(version(equivalent)).isEqualTo(version(equivalent))
				.hasSameHashCodeAs(version(equivalent));
	}

	@Test
	void shouldDistinguishReleaseSpellings() {

		assertThatVersion("1.0.0").isEqualByComparingTo("1.0.0.RELEASE").isEqualByComparingTo("1.0.0.Final");
		assertThatVersion("1.0.0.RELEASE").isEqualByComparingTo("1.0.0.Final");

		assertThat(version("1.0.0")).isNotEqualTo(version("1.0.0.RELEASE")).isNotEqualTo(version("1.0.0.Final"));
		assertThat(version("1.0.0.RELEASE")).isNotEqualTo(version("1.0.0.Final"));
	}

	@Test
	void shouldConsiderEquality() {

		assertThat(version("1.0.0")).isEqualTo(version("1.0.0")).hasSameHashCodeAs(version("1.0.0"));
		assertThat(version("1.0.0.RELEASE")).isEqualTo(version("1.0.0.RELEASE"))
				.hasSameHashCodeAs(version("1.0.0.RELEASE"));
		assertThat(version("1.0.0-M1")).isEqualTo(version("1.0.0-M1")).hasSameHashCodeAs(version("1.0.0-M1"));

		Set<SemanticArtifactVersion> set = new HashSet<>(versions("1.0.0", "1.0.0.RELEASE", "1.0.0.Final"));

		assertThat(set).hasSize(3).contains(version("1.0.0"),
				version("1.0.0.RELEASE"), version("1.0.0.Final"))
				.doesNotContain(version("1.0.1"));
	}

	@Test
	void shouldMatchBaseVersionIgnoringSuffix() {

		assertThat(version("3.9.6").hasSameBaseVersion(version("3.9.6-SNAPSHOT"))).isTrue();
		assertThat(version("3.9.6").hasSameBaseVersion(version("3.9.6-M1"))).isTrue();
		assertThat(version("3.9.6-SNAPSHOT").hasSameBaseVersion(version("3.9.6-M1"))).isTrue();
		assertThat(version("3.9.6").hasSameBaseVersion(version("3.9.6.0"))).isTrue();

		assertThat(version("3.9.6").hasSameBaseVersion(version("3.9.9"))).isFalse();
		assertThat(version("3.9.6").hasSameBaseVersion(version("3.10.6"))).isFalse();
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({"1.0.0, 1.0.1-SNAPSHOT", "1.0.0-M1, 1.0.0-SNAPSHOT", "1.0.1, 1.0.2-SNAPSHOT"})
	void shouldDeriveNextBugfixVersion(String current, String expected) {
		assertThat(version(current).getNextBugfixVersion()).isEqualTo(version(expected));
	}

	@Test
	void shouldDeriveNextDevelopmentVersion() {
		assertThat(version("1.5.0").getNextDevelopmentVersion()).isEqualTo(version("1.6.0-SNAPSHOT")).isNotMilestone()
				.isNotRelease();
		assertThat(version("1.4.5").getNextDevelopmentVersion()).isEqualTo(version("1.4.6-SNAPSHOT"));
	}

	@Test
	void shouldConsiderIsMinorCorrectly() {
		assertThat(version("1.1.0").isNewerMinor(version("1.2.0"))).isTrue();
		assertThat(version("1.1.0").isNewerMinor(version("1.2.0"))).isTrue();
		assertThat(version("1.1.0").isNewerMinor(version("1.2.5"))).isTrue();
		assertThat(version("1.2.0").isNewerMinor(version("1.1.0"))).isFalse();
		assertThat(version("1.2.0").isNewerMinor(version("2.0.0"))).isFalse();
		assertThat(version("1.2.0").isNewerMinor(version("1.2.0"))).isFalse();
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"v1.2.3, true, false", "v1.0.0-beta.1, false, true", "v2026.4.0-rc.1, false, true"})
	void shouldNormalizeVPrefix(String source, boolean release, boolean preview) {

		SemanticArtifactVersion version = version(source);

		assertThat(version).hasToString(source);
		assertThat(version.isReleaseVersion()).isEqualTo(release);
		assertThat(version.isPreview()).isEqualTo(preview);
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource({"v1.2.3, 1.2.3, true, false", "v1.0.0-beta.1, 1.0.0-beta.1, false, true",
			"v2026.4.0-rc.1, 2026.4.0-rc.1, false, true"})
	void shouldWrapVPrefixedVersions(String source, String inner, boolean release, boolean preview) {

		ArtifactVersion version = ArtifactVersion.of(source);

		assertThat(version.isWrapped()).isTrue();
		assertThat(version).hasToString(source);
		assertThat(version.getVersion()).hasToString(inner);
		assertThat(version.isReleaseVersion()).isEqualTo(release);
		assertThat(version.isPreview()).isEqualTo(preview);
	}

	@Test
	void shouldNotWrapPlainVersions() {

		ArtifactVersion version = ArtifactVersion.of("1.2.3");

		assertThat(version.isWrapped()).isFalse();
		assertThat(version.getVersion()).isSameAs(version);
	}

	@Test
	void shouldComparePrefixedAndUnprefixed() {
		assertThatVersion("v1.2.3").isEqualByComparingTo("1.2.3")
				.isLessThan("2.0.0").isLessThan("v2.0.0");
		assertThatVersion("1.2.3").isEqualByComparingTo("v1.2.3");
		assertThatVersion("v2.0.0").isGreaterThan("v1.2.3");
		assertThatVersion("2.0.0").isGreaterThan("v1.2.3");
	}

	@Test
	void shouldTreatPrefixedAsEqualToUnprefixed() {

		ArtifactVersion prefixed = ArtifactVersion.of("v1.2.3");
		ArtifactVersion plain = ArtifactVersion.of("1.2.3");

		assertThat(prefixed).isEqualTo(plain).hasSameHashCodeAs(plain);
		assertThat(plain).isEqualTo(prefixed);
		assertThat(prefixed.canCompare(plain)).isTrue();
		assertThat(plain.canCompare(prefixed)).isTrue();
	}

	static SemanticArtifactVersion version(String source) {
		return SemanticArtifactVersion.of(source);
	}

	static List<SemanticArtifactVersion> versions(String... sources) {
		return Arrays.stream(sources).map(SemanticArtifactVersionParseUnitTests::version).toList();
	}

}
