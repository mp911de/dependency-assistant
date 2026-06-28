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

package biz.paluch.dap.support;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitRef;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ArtifactVersionChange} crossing predicates.
 *
 * @author Mark Paluch
 */
class ArtifactVersionChangeUnitTests {

	private static final ArtifactId ARTIFACT = ArtifactId.of("org.springframework", "spring-core");

	@Test
	void patchBumpCrossesNothing() {

		ArtifactVersionChange change = ArtifactVersionChange.of(ARTIFACT, version("1.4.5"), version("1.4.7"));

		assertThat(change.crossesMajor()).isFalse();
		assertThat(change.crossesMinor()).isFalse();
	}

	@Test
	void minorBumpCrossesMinorOnly() {

		ArtifactVersionChange change = ArtifactVersionChange.of(ARTIFACT, version("1.4.5"), version("1.5.0"));

		assertThat(change.crossesMajor()).isFalse();
		assertThat(change.crossesMinor()).isTrue();
	}

	@Test
	void majorBumpCrossesMajorAndMinor() {

		ArtifactVersionChange change = ArtifactVersionChange.of(ARTIFACT, version("1.4.5"), version("2.0.0"));

		assertThat(change.crossesMajor()).isTrue();
		assertThat(change.crossesMinor()).isTrue();
	}

	@Test
	void versioningSchemeChangeCountsAsMajorSwitch() {

		ArtifactVersionChange change = ArtifactVersionChange.of(ARTIFACT, version("5.3.0"), version("Bismuth-SR1"));

		assertThat(change.crossesMajor()).isTrue();
		assertThat(change.crossesMinor()).isTrue();
	}

	@Test
	void opaqueTargetCrossesMajorConservatively() {

		ArtifactVersionChange change = ArtifactVersionChange.of(ARTIFACT, version("1.4.5"), new GitRef("main"));

		assertThat(change.crossesMajor()).isTrue();
		assertThat(change.crossesMinor()).isTrue();
	}

	@Test
	void unknownSourceVersionReportsNoCrossing() {

		ArtifactVersionChange change = ArtifactVersionChange.of(ARTIFACT, version("2.0.0"));

		assertThat(change.crossesMajor()).isFalse();
		assertThat(change.crossesMinor()).isFalse();
	}

	private static ArtifactVersion version(String version) {
		return ArtifactVersion.of(version);
	}

}
