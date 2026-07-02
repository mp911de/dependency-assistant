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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link ReleaseTrainArtifactVersion}.
 *
 * @author Mark Paluch
 */
class ReleaseTrainArtifactVersionUnitTests {

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"Hoxton.SR12", "Greenwich.SR6", "Hoxton.RELEASE", "Hoxton.RC1", "Hoxton.M1"})
	void parsesDotSeparatedTrains(String version) {

		assertThat(ArtifactVersion.of(version).scheme()).isEqualTo(VersioningScheme.RELEASE_TRAIN);
		assertThatVersion(version).hasToString(version);
	}

	@Test
	void parsesDotSeparatedBuildSnapshot() {
		assertThatVersion("Hoxton.BUILD-SNAPSHOT").isSnapshot().hasToString("Hoxton.BUILD-SNAPSHOT");
	}

	@Test
	void ordersDotSeparatedTrainSuffixes() {

		assertThatVersion("Hoxton.SR12").isGreaterThan("Hoxton.SR11");
		assertThatVersion("Hoxton.SR1").isGreaterThan("Hoxton.RELEASE");
		assertThatVersion("Hoxton.RELEASE").isGreaterThan("Hoxton.RC1");
		assertThatVersion("Hoxton.RC1").isGreaterThan("Hoxton.M1");
	}

	@Test
	void separatorDoesNotAffectEquality() {

		assertThat(ArtifactVersion.of("Hoxton.SR12")).isEqualTo(ArtifactVersion.of("Hoxton-SR12"));
		assertThat(ArtifactVersion.of("Hoxton.SR12").hashCode())
				.isEqualTo(ArtifactVersion.of("Hoxton-SR12").hashCode());
		assertThat(ArtifactVersion.of("Hoxton.SR12").hasSameMajorMinor(ArtifactVersion.of("Hoxton-SR1"))).isTrue();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"some.property.name", "maven.compiler.source", "Hoxton.Special", "Hoxton.sr12"})
	void rejectsDotSeparatedNonTrainQualifiers(String candidate) {
		assertThat(ReleaseTrainArtifactVersion.isReleaseTrainVersion(candidate)).isFalse();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"Bismuth-SR1", "Aluminium-M1", "Dysprosium-SR25"})
	void keepsHyphenSeparatedTrains(String version) {

		assertThat(ArtifactVersion.of(version).scheme()).isEqualTo(VersioningScheme.RELEASE_TRAIN);
		assertThatVersion(version).hasToString(version);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"Aluminium-BETA1", "Aluminium-alpha1", "Aluminium-M1"})
	void classifiesTrainPreReleases(String version) {
		assertThatVersion(version).isPreview().isNotRelease();
	}

	@Test
	void numericEraSupersedesTrainEra() {

		assertThatVersion("2020.0.1").isGreaterThan("Hoxton.SR12");
		assertThatVersion("Hoxton.SR12").isLessThan("2020.0.1");
		assertThatVersion("1.0.0").isGreaterThan("Neumann-SR1");
	}

	@Test
	void sortsMixedEraHistoryWithNumericEraFirst() {

		List<ArtifactVersion> history = new ArrayList<>(Stream
				.of("Hoxton.SR12", "2021.0.5", "Greenwich.SR6", "2020.0.6", "Hoxton.RELEASE")
				.map(ArtifactVersion::of).toList());
		history.sort(Comparator.reverseOrder());

		assertThat(history.stream().map(Object::toString).toList())
				.containsExactly("2021.0.5", "2020.0.6", "Hoxton.SR12", "Hoxton.RELEASE", "Greenwich.SR6");
	}

}
