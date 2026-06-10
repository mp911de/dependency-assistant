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

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VersioningScheme} classification via
 * {@link ArtifactVersion#scheme()}.
 *
 * @author Mark Paluch
 */
class VersioningSchemeUnitTests {

	@Test
	void classifiesNumericVersions() {

		assertThat(ArtifactVersion.of("1.4.7").scheme()).isEqualTo(VersioningScheme.NUMERIC);
		assertThat(ArtifactVersion.of("2025.0.6").scheme()).isEqualTo(VersioningScheme.NUMERIC);
		assertThat(ArtifactVersion.of("3.1.2-SNAPSHOT").scheme()).isEqualTo(VersioningScheme.NUMERIC);
	}

	@Test
	void classifiesReleaseTrainVersions() {

		assertThat(ArtifactVersion.of("Bismuth-SR1").scheme()).isEqualTo(VersioningScheme.RELEASE_TRAIN);
		assertThat(ArtifactVersion.of("Dysprosium-SR25").scheme()).isEqualTo(VersioningScheme.RELEASE_TRAIN);
	}

	@Test
	void wrappersDelegateToWrappedScheme() {

		assertThat(ArtifactVersion.of("v1.2.3").scheme()).isEqualTo(VersioningScheme.NUMERIC);
		assertThat(GitVersion.of("0123456789012345678901234567890123456789", ArtifactVersion.of("1.2.3")).scheme())
				.isEqualTo(VersioningScheme.NUMERIC);
	}

	@Test
	void classifiesGitRefAsOpaque() {
		assertThat(new GitRef("main").scheme()).isEqualTo(VersioningScheme.OPAQUE);
	}

	@Test
	void canCompareRequiresSameScheme() {

		assertThat(ArtifactVersion.of("2025.0.6").canCompare(ArtifactVersion.of("2020.0.0"))).isTrue();
		assertThat(ArtifactVersion.of("Bismuth-SR1").canCompare(ArtifactVersion.of("Dysprosium-SR25"))).isTrue();

		assertThat(ArtifactVersion.of("2025.0.6").canCompare(ArtifactVersion.of("Dysprosium-SR25"))).isFalse();
		assertThat(ArtifactVersion.of("Dysprosium-SR25").canCompare(ArtifactVersion.of("2025.0.6"))).isFalse();
	}

	@Test
	void opaqueComparesToNothing() {

		GitRef ref = new GitRef("main");

		assertThat(ref.canCompare(ArtifactVersion.of("1.2.3"))).isFalse();
		assertThat(ref.canCompare(new GitRef("main"))).isFalse();
	}

}
