/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.assistant;

import java.util.List;
import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.fixtures.TestReleases;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ReleaseProposals}.
 *
 * @author Mark Paluch
 */
class ReleaseProposalsUnitTests {

	private static final Releases SEMVER = TestReleases.from(
			"6.1.0-RC1", "6.1.0-M1",
			"6.0.3", "6.0.0",
			"5.5.4", "5.5.0",
			"5.4.9", "5.4.0",
			"5.3.8",
			"5.2.7",
			"5.1.6",
			"5.0.5", "5.0.0",
			"4.5.9", "4.5.3", "4.5.0", "4.5.0-RC1", "4.5.0-M2",
			"4.4.9", "4.4.0",
			"3.5.12", "3.5.0", "3.5.0-RC2", "3.5.0-M1",
			"3.4.7", "3.4.0",
			"2.7.12", "2.7.0");

	@Test
	void windowShowsNewestLinesAcrossRecentMajors() {

		assertThat(proposals(SEMVER, null, null))
				.containsExactly("6.1.0-RC1", "6.0.3", "5.5.4", "5.4.9", "5.3.8", "4.5.9", "4.4.9", "3.5.12", "3.4.7");
	}

	@Test
	void capsStableLinesPerMajor() {

		assertThat(proposals(SEMVER, null, null))
				.contains("4.5.9", "3.5.12")
				.doesNotContain("5.2.7", "5.1.6", "5.0.5");
	}

	@Test
	void corridorKeepsCurrentLineAndNewer() {

		assertThat(proposals(SEMVER, version("4.5.3"), null))
				.containsExactly("6.1.0-RC1", "6.0.3", "5.5.4", "5.4.9", "5.3.8", "4.5.9", "4.5.3");
	}

	@Test
	void corridorBypassesMajorCapForCurrentLine() {

		assertThat(proposals(SEMVER, version("5.1.6"), null))
				.containsExactly("6.1.0-RC1", "6.0.3", "5.5.4", "5.4.9", "5.3.8", "5.1.6");
	}

	@Test
	void corridorKeepsAncientCurrentLineBeyondWindow() {

		assertThat(proposals(SEMVER, version("2.7.5"), null))
				.containsExactly("6.1.0-RC1", "6.0.3", "5.5.4", "5.4.9", "5.3.8", "4.5.9", "4.4.9", "3.5.12", "3.4.7",
						"2.7.12");
	}

	@Test
	void stemAddsMatchingLinesBeyondTheMajorCap() {

		assertThat(proposals(SEMVER, null, "5."))
				.contains("5.5.4", "5.4.9", "5.3.8", "5.2.7", "5.1.6", "5.0.5")
				.doesNotContain("5.5.0", "5.4.0", "5.0.0");
	}

	@Test
	void stemAddsMatchingLinesBelowTheCorridor() {

		assertThat(proposals(SEMVER, version("4.5.3"), "3."))
				.contains("3.5.12", "3.4.7", "4.5.9", "4.5.3")
				.doesNotContain("2.7.12");
	}

	@Test
	void stemPinningSingleLineListsItsMembers() {

		assertThat(proposals(SEMVER, null, "2.7"))
				.contains("2.7.12", "2.7.0");
	}

	@Test
	void suffixIntentSurfacesPreviewsOfTheBaseVersion() {

		assertThat(proposals(SEMVER, null, "3.5.0-"))
				.contains("3.5.0-RC2", "3.5.0-M1")
				.doesNotContain("3.5.0");
	}

	@Test
	void capsPreReleaseOnlyLines() {

		Releases releases = TestReleases.from("7.2.0-M1", "7.1.0-M1", "7.0.0-M1", "6.9.0");

		assertThat(proposals(releases, null, null)).containsExactly("7.2.0-M1", "7.1.0-M1", "6.9.0");
	}

	@Test
	void unmatchedStemLeavesWindowSelection() {

		assertThat(proposals(SEMVER, null, "x%"))
				.containsExactly("6.1.0-RC1", "6.0.3", "5.5.4", "5.4.9", "5.3.8", "4.5.9", "4.4.9", "3.5.12", "3.4.7");
	}

	@Test
	void corridorUnwrapsWrappedCurrentVersion() {

		Releases releases = TestReleases.from("v4.2.0", "v4.1.7", "v4.1.0", "v3.6.0");
		ArtifactVersion current = GitVersion.of("0123456789012345678901234567890123456789",
				ArtifactVersion.of("v4.1.0"));

		assertThat(proposals(releases, current, null))
				.containsExactly("v4.2.0", "v4.1.7", "v4.1.0")
				.doesNotContain("v3.6.0");
	}

	@Test
	void opaqueCurrentVersionFallsBackToWindow() {

		Releases releases = TestReleases.from("v4.2.0", "v4.1.7", "v3.6.0");

		assertThat(proposals(releases, new GitRef("main"), null))
				.containsExactly("v4.2.0", "v4.1.7", "v3.6.0");
	}

	@Test
	void currentVersionAheadOfHistoryFallsBackToWindow() {

		assertThat(proposals(SEMVER, version("7.0.0-SNAPSHOT"), null))
				.containsExactly("6.1.0-RC1", "6.0.3", "5.5.4", "5.4.9", "5.3.8", "4.5.9", "4.4.9", "3.5.12", "3.4.7");
	}

	@Test
	void selectsLatestPerTrainAcrossEras() {

		Releases releases = TestReleases.from("2021.0.5", "2021.0.4", "2020.0.6", "Hoxton.SR12", "Hoxton.RELEASE",
				"Greenwich.SR6");

		assertThat(proposals(releases, version("Hoxton.SR11"), null))
				.containsExactly("2021.0.5", "2020.0.6", "Hoxton.SR12");
	}

	@Test
	void withAddsReleaseInCanonicalOrder() {

		Release release = SEMVER.getRelease(version("5.1.6"));
		assertThat(release).isNotNull();

		assertThat(ReleaseProposals.select(SEMVER, null, null).with(release))
				.map(Release::version).map(Objects::toString)
				.containsExactly("6.1.0-RC1", "6.0.3", "5.5.4", "5.4.9", "5.3.8", "5.1.6", "4.5.9", "4.4.9", "3.5.12",
						"3.4.7");
	}

	@Test
	void withReturnsSameInstanceForIncludedRelease() {

		ReleaseProposals proposals = ReleaseProposals.select(SEMVER, null, null);
		Release release = SEMVER.getRelease(version("6.0.3"));
		assertThat(release).isNotNull();

		assertThat(proposals.with(release)).isSameAs(proposals);
	}

	private static List<String> proposals(Releases releases, @Nullable ArtifactVersion currentVersion,
			@Nullable String prefix) {
		return ReleaseProposals.select(releases, currentVersion, VersionStem.from(prefix)).stream()
				.map(release -> release.version().toString())
				.toList();
	}

	private static ArtifactVersion version(String version) {
		return ArtifactVersion.of(version);
	}

}
