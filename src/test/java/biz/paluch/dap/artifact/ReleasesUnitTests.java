/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.paluch.dap.artifact;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Releases}.
 *
 * @author Mark Paluch
 */
class ReleasesUnitTests {

	@Test
	void ordersSupersededSchemeBelowSuccessorScheme() {

		Releases releases = Releases.of(
				Release.from("Dysprosium-SR25", "2021-11-09"),
				Release.from("2025.0.6", "2026-06-08"),
				Release.from("2020.0.0", "2020-10-26"),
				Release.from("Dysprosium-RELEASE", "2019-09-24"),
				Release.from("2024.0.18", "2026-06-08"));

		assertThat(releases.toList()).extracting(release -> release.version().toString())
				.containsExactly("2025.0.6", "2024.0.18", "2020.0.0", "Dysprosium-SR25", "Dysprosium-RELEASE");
	}

	@Test
	void switchingBackToFormerSchemeMakesItSuccessorAgain() {

		Releases releases = Releases.of(
				Release.from("Aluminium-RELEASE", "2017-02-22"),
				Release.from("2020.0.0", "2020-10-26"),
				Release.from("Zirconium-RELEASE", "2026-01-01"));

		assertThat(releases.successorScheme()).isEqualTo(VersioningScheme.RELEASE_TRAIN);
		assertThat(releases.toList()).extracting(release -> release.version().toString())
				.containsExactly("Zirconium-RELEASE", "Aluminium-RELEASE", "2020.0.0");
	}

	@Test
	void inSchemeReturnsNewestFirstPartition() {

		Releases releases = Releases.of(
				Release.from("2020.0.0", "2020-10-26"),
				Release.from("Dysprosium-SR25", "2021-11-09"),
				Release.from("2025.0.6", "2026-06-08"));

		assertThat(releases.inScheme(VersioningScheme.NUMERIC)).extracting(release -> release.version().toString())
				.containsExactly("2025.0.6", "2020.0.0");
		assertThat(releases.inScheme(VersioningScheme.OPAQUE)).isEmpty();
	}

	@Test
	void singleSchemeWithoutDatesSortsByVersion() {

		Releases releases = Releases.of(
				Release.of("3.9.6"),
				Release.of("3.10.0"),
				Release.of("3.9.9"));

		assertThat(releases.successorScheme()).isEqualTo(VersioningScheme.NUMERIC);
		assertThat(releases.toList()).extracting(release -> release.version().toString())
				.containsExactly("3.10.0", "3.9.9", "3.9.6");
	}

	@Test
	void emptyReleasesHaveNoSuccessorScheme() {

		Releases releases = Releases.of(List.of());

		assertThat(releases.successorScheme()).isNull();
		assertThat(releases.toList()).isEmpty();
	}

}
