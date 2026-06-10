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

import biz.paluch.dap.fixtures.Releases;
import biz.paluch.dap.state.CachedArtifact;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradeStrategy}.
 *
 * @author Mark Paluch
 */
class UpgradeStrategyUnitTests {

	// -------------------------------------------------------------------------
	// PATCH
	// -------------------------------------------------------------------------

	@Test
	void patchSelectsNewestBugfixWithinSameMajorMinor() {

		Release release = select(UpgradeStrategy.PATCH, "3.9.6", Releases.APACHE_MAVEN);

		assertThat(release).isNotNull();
		assertThat(release.version()).hasToString("3.9.9");
	}

	@Test
	void patchReturnsNullWhenAlreadyNewestInLine() {

		Release release = select(UpgradeStrategy.PATCH, "3.9.9", Releases.APACHE_MAVEN);

		assertThat(release).isNull();
	}

	@Test
	void patchSkipsHigherMinorVersions() {

		Release release = select(UpgradeStrategy.PATCH, "2.0.4", Releases.SPRING_MODULITH_BOM);

		assertThat(release.version()).hasToString("2.0.5");
	}

	// -------------------------------------------------------------------------
	// MINOR
	// -------------------------------------------------------------------------

	@Test
	void minorSelectsHigherMinorWithinSameMajor() {

		Release release = select(UpgradeStrategy.MINOR, "3.9.6", Releases.APACHE_MAVEN);

		assertThat(release.version()).hasToString("3.10.0");
	}

	@Test
	void minorSkipsPreviewAndReturnsNullWhenOnlyPreviewMinorExists() {

		Release release = select(UpgradeStrategy.MINOR, "2.0.4", Releases.SPRING_MODULITH_BOM);

		assertThat(release).isNull();
	}

	// -------------------------------------------------------------------------
	// MAJOR
	// -------------------------------------------------------------------------

	@Test
	void majorSelectsHigherMajor() {

		Release release = select(UpgradeStrategy.MAJOR, "4.0.31", Releases.GROOVY);

		assertThat(release.version()).hasToString("5.0.5");
	}

	@Test
	void majorReturnsNullWhenNoHigherMajorExists() {

		Release release = select(UpgradeStrategy.MAJOR, "3.9.6", Releases.APACHE_MAVEN);

		assertThat(release).isNull();
	}

	// -------------------------------------------------------------------------
	// LATEST
	// -------------------------------------------------------------------------

	@Test
	void latestSelectsNewestStableRegardlessOfCurrent() {

		Release release = select(UpgradeStrategy.LATEST, "3.9.5", Releases.APACHE_MAVEN);

		assertThat(release.version()).hasToString("3.10.0");
	}

	@Test
	void latestSkipsPreviewAtTheTopOfTheList() {

		Release release = select(UpgradeStrategy.LATEST, "2.2.21", Releases.KOTLIN_REFLECT);

		assertThat(release.version()).hasToString("2.3.20");
	}

	// -------------------------------------------------------------------------
	// PREVIEW
	// -------------------------------------------------------------------------

	@Test
	void previewSelectsNewestPreviewNewerThanCurrent() {

		Release release = select(UpgradeStrategy.PREVIEW, "6.0.3", Releases.JUNIT_BOM);

		assertThat(release.version()).hasToString("6.1.0-M1");
	}

	@Test
	void previewForSnapshotCurrentSelectsMilestoneInSameLine() {

		Release release = select(UpgradeStrategy.PREVIEW, "6.0.0-SNAPSHOT", Releases.JUNIT_BOM);

		assertThat(release.version()).hasToString("6.0.0-M1");
	}

	@Test
	void previewReturnsNullWhenNoPreviewAvailable() {

		Release release = select(UpgradeStrategy.PREVIEW, "3.9.6", Releases.APACHE_MAVEN);

		assertThat(release).isNull();
	}

	// -------------------------------------------------------------------------
	// RELEASE
	// -------------------------------------------------------------------------

	@Test
	void releaseSelectsNewerStableReleaseFromSnapshot() {

		Release release = select(UpgradeStrategy.RELEASE, "3.9.6-SNAPSHOT", Releases.APACHE_MAVEN);

		assertThat(release.version()).hasToString("3.9.6");
	}

	@Test
	void releaseSelectsNewerStableReleaseFromMilestone() {

		Release release = select(UpgradeStrategy.RELEASE, "3.9.6-M1", Releases.APACHE_MAVEN);

		assertThat(release.version()).hasToString("3.9.6");
	}

	@Test
	void releaseSelectsLatestBugfixWhenExactGaMissing() {

		Release release = select(UpgradeStrategy.RELEASE, "3.9.7-SNAPSHOT", Releases.APACHE_MAVEN);

		assertThat(release.version()).hasToString("3.9.9");
	}

	@Test
	void releaseReturnsNullWhenNoNewerReleaseInSameLine() {

		Release release = select(UpgradeStrategy.RELEASE, "3.10.0", Releases.APACHE_MAVEN);

		assertThat(release).isNull();
	}

	@Test
	void majorDoesNotSuggestReleaseTrainForCalendarVersion() {

		Release release = select(UpgradeStrategy.MAJOR, "2025.0.6", Releases.REACTOR_BOM);

		assertThat(release).isNull();
	}

	@Test
	void latestForReleaseTrainCurrentStaysWithinTrains() {

		Release release = select(UpgradeStrategy.LATEST, "Aluminium-RELEASE", Releases.REACTOR_BOM);

		assertThat(release.version()).hasToString("Dysprosium-SR25");
	}

	@Test
	void selectReturnsNullForOpaqueCurrent() {

		biz.paluch.dap.artifact.Releases history = biz.paluch.dap.artifact.Releases
				.of(Releases.REACTOR_BOM.getVersionOptions());

		Release release = UpgradeStrategy.LATEST.select(new GitRef("main"), history);

		assertThat(release).isNull();
	}

	private static Release select(UpgradeStrategy strategy, String currentVersion, CachedArtifact artifact) {
		return strategy.select(ArtifactVersion.of(currentVersion),
				biz.paluch.dap.artifact.Releases.of(artifact.getVersionOptions()));
	}

}
