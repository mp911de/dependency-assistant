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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Release}.
 *
 * @author Mark Paluch
 */
class ReleaseUnitTests {

	private static final LocalDateTime DATE = LocalDateTime.parse("2024-05-01T12:00:00");

	@Test
	void shouldCompareVersions() {

		Release train = new Release(ArtifactVersion.of("Aluminium-SR1"),
				LocalDateTime.parse("2017-01-01T00:00:00"));
		Release v1 = new Release(ArtifactVersion.of("2020.0.0"), LocalDateTime.parse("2019-01-01T00:00:00"));
		Release v2 = new Release(ArtifactVersion.of("2020.0.1"), LocalDateTime.parse("2019-01-01T00:00:00"));

		assertThat(train).isLessThan(v1);
		assertThat(v1).isGreaterThan(train).isLessThan(v2);
	}

	@Test
	void crossSchemeReleasesWithEqualDatesRemainDistinct() {

		// reactor-bom published 2020.0.13 and Dysprosium-SR25 on the same day
		Release numeric = Release.from("2020.0.13", "2021-11-09");
		Release train = Release.from("Dysprosium-SR25", "2021-11-09");

		assertThat(numeric.compareTo(train)).isNotZero();
		assertThat(new TreeSet<>(List.of(numeric, train))).hasSize(2);
	}

	@Test
	void tryFromValidVersionWithDateAndSha() {

		Optional<Release> release = Release.tryFrom("1.2.3", DATE, "abc123");

		assertThat(release).isPresent();
		assertThat(release.get().releaseDate()).isEqualTo(DATE);
		assertThat(release.get().version()).isInstanceOfSatisfying(GitVersion.class,
				git -> assertThat(git.getSha()).isEqualTo("abc123"));
	}

	@Test
	void tryFromValidVersionWithShaWrapsInGitVersion() {

		Optional<Release> release = Release.tryFrom("1.2.3", null, "abc123");

		assertThat(release).isPresent();
		assertThat(release.get().releaseDate()).isNull();
		assertThat(release.get().version()).isInstanceOfSatisfying(GitVersion.class,
				git -> assertThat(git.getSha()).isEqualTo("abc123"));
	}

	@Test
	void tryFromValidVersionWithDateOnly() {

		Optional<Release> release = Release.tryFrom("1.2.3", DATE, null);

		assertThat(release).isPresent();
		assertThat(release.get().releaseDate()).isEqualTo(DATE);
		assertThat(release.get().version()).isEqualTo(ArtifactVersion.of("1.2.3"));
		assertThat(release.get().version()).isNotInstanceOf(GitVersion.class);
	}

	@Test
	void tryFromValidBareVersion() {

		Optional<Release> release = Release.tryFrom("1.2.3", null, null);

		assertThat(release).isPresent();
		assertThat(release.get().releaseDate()).isNull();
		assertThat(release.get().version()).isEqualTo(ArtifactVersion.of("1.2.3"));
		assertThat(release.get().version()).isNotInstanceOf(GitVersion.class);
	}

	@Test
	void tryFromBlankVersionReturnsEmpty() {
		assertThat(Release.tryFrom("", DATE, "abc123")).isEmpty();
	}

	@Test
	void tryFromWhitespaceVersionReturnsEmpty() {
		assertThat(Release.tryFrom("   ", DATE, "abc123")).isEmpty();
	}

	@Test
	void tryFromUnparseableVersionReturnsEmpty() {
		assertThat(Release.tryFrom("not a version", null, null)).isEmpty();
	}

	@Test
	void tryFromBlankShaTreatedAsAbsent() {

		Optional<Release> release = Release.tryFrom("1.2.3", null, "   ");

		assertThat(release).isPresent();
		assertThat(release.get().version()).isNotInstanceOf(GitVersion.class);
	}

}
