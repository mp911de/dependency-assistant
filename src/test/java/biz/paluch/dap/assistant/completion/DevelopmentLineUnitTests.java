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

package biz.paluch.dap.assistant.completion;

import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.fixtures.TestReleases;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DevelopmentLines}.
 *
 * @author Mark Paluch
 */
class DevelopmentLineUnitTests {

	@Test
	void groupsByMajorMinor() {

		DevelopmentLines lines = DevelopmentLines.of(
				TestReleases.from("5.1.0-RC2", "5.0.9", "5.0.8", "4.5.9", "4.5.0-RC1"));

		assertThat(lines).map(DevelopmentLine::getLatest).map(Objects::toString)
				.containsExactly("5.1.0-RC2", "5.0.9", "4.5.9");
		assertThat(lines).map(DevelopmentLine::getLatestStable)
				.containsExactly(null, ArtifactVersion.of("5.0.9"), ArtifactVersion.of("4.5.9"));
	}

	@Test
	void groupsTrainsByTrainName() {

		DevelopmentLines lines = DevelopmentLines.of(
				TestReleases.from("Neumann-RC1", "Moore-SR12", "Moore-RELEASE", "Lovelace-SR9"));

		assertThat(lines).map(DevelopmentLine::getLatest).map(Objects::toString)
				.containsExactly("Neumann-RC1", "Moore-SR12", "Lovelace-SR9");
	}

	@Test
	void groupsMixedErasSeparately() {

		DevelopmentLines lines = DevelopmentLines.of(
				TestReleases.from("2021.0.5", "2020.0.6", "Hoxton.SR12", "Hoxton.RELEASE"));

		assertThat(lines).map(DevelopmentLine::getLatest).map(Objects::toString)
				.containsExactly("2021.0.5", "2020.0.6", "Hoxton.SR12");
		assertThat(lines.getLines().getLast()).map(Objects::toString)
				.containsExactly("Hoxton.SR12", "Hoxton.RELEASE");
	}

	@Test
	void skipsDuplicateVersions() {

		DevelopmentLines lines = DevelopmentLines.of(TestReleases.from("5.0.9", "5.0.9", "5.0.8"));

		assertThat(lines).hasSize(1);
		assertThat(lines.getLines().getFirst()).map(Objects::toString).containsExactly("5.0.9", "5.0.8");
	}

	@Test
	void answersLineMembershipAndAge() {

		DevelopmentLine line = DevelopmentLines.of(TestReleases.from("4.5.9", "4.5.0")).getLines().getFirst();

		assertThat(line.contains(ArtifactVersion.of("4.5.3"))).isTrue();
		assertThat(line.contains(ArtifactVersion.of("4.4.9"))).isFalse();
		assertThat(line.isOlderThan(ArtifactVersion.of("5.0.0"))).isTrue();
		assertThat(line.isOlderThan(ArtifactVersion.of("4.5.3"))).isFalse();
	}

}
