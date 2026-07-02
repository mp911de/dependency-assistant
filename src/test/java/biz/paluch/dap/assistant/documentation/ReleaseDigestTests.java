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

package biz.paluch.dap.assistant.documentation;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.assistant.documentation.DependencyDocumentationRenderer.ReleaseDigest;
import biz.paluch.dap.fixtures.TestReleases;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyDocumentationRenderer.ReleaseDigest}.
 *
 * @author Mark Paluch
 */
class ReleaseDigestTests {

	@Test
	void newerPreviewsFillTopSectionUpToLimit() {

		ReleaseDigest digest = digest("3.9.4", "4.0.0-alpha.13", "4.0.0-alpha.12", "4.0.0-alpha.11",
				"4.0.0-alpha.10", "3.9.4", "3.9.3");

		assertThat(versions(digest.previewRows())).containsExactly("4.0.0-alpha.13", "4.0.0-alpha.12");
		assertThat(digest.morePreviews()).isEqualTo(2);
		assertThat(versions(digest.releaseRows())).containsExactly("3.9.4", "3.9.3");
		assertThat(digest.moreReleases()).isZero();
	}

	@Test
	void stableReleasesAreNotLimitedToNewerThanCurrent() {

		ReleaseDigest digest = digest("6.2.0", "6.3.0", "6.2.1", "6.2.0", "6.1.0", "6.0.0");

		assertThat(digest.previewRows()).isEmpty();
		assertThat(versions(digest.releaseRows())).containsExactly("6.3.0", "6.2.1", "6.2.0", "6.1.0", "6.0.0");
		assertThat(digest.moreReleases()).isZero();
	}

	@Test
	void stableRowsBeyondLimitAreCounted() {

		ReleaseDigest digest = ReleaseDigest.of(TestReleases.from("6.5.0", "6.4.0", "6.3.0", "6.2.0", "6.1.0"),
				ArtifactVersion.of("6.4.0"), 2, 3);

		assertThat(versions(digest.releaseRows())).containsExactly("6.5.0", "6.4.0", "6.3.0");
		assertThat(digest.moreReleases()).isEqualTo(2);
	}

	@Test
	void previewsAtOrBelowCurrentFoldIntoMoreReleases() {

		ReleaseDigest digest = digest("6.2.0", "6.3.0", "6.2.0", "6.1.0-RC1", "6.1.0");

		assertThat(digest.previewRows()).isEmpty();
		assertThat(digest.morePreviews()).isZero();
		assertThat(versions(digest.releaseRows())).containsExactly("6.3.0", "6.2.0", "6.1.0");
		assertThat(digest.moreReleases()).isEqualTo(1);
	}

	@Test
	void currentPreviewAnchorsTheReleaseSection() {

		ReleaseDigest digest = ReleaseDigest.of(
				TestReleases.from("4.0.0-alpha.13", "4.0.0-alpha.12", "4.0.0-alpha.11", "4.0.0-alpha.10", "3.9.4"),
				ArtifactVersion.of("4.0.0-alpha.10"), 2, 10);

		assertThat(versions(digest.previewRows())).containsExactly("4.0.0-alpha.13", "4.0.0-alpha.12");
		assertThat(digest.morePreviews()).isEqualTo(1);
		assertThat(versions(digest.releaseRows())).containsExactly("4.0.0-alpha.10", "3.9.4");
	}

	@Test
	void withoutCurrentVersionEveryPreviewIsNewer() {

		ReleaseDigest digest = digest(null, "4.0.0-alpha.3", "4.0.0-alpha.2", "4.0.0-alpha.1", "3.9.4", "3.9.3");

		assertThat(versions(digest.previewRows())).containsExactly("4.0.0-alpha.3", "4.0.0-alpha.2");
		assertThat(digest.morePreviews()).isEqualTo(1);
		assertThat(versions(digest.releaseRows())).containsExactly("3.9.4", "3.9.3");
	}

	private static ReleaseDigest digest(@Nullable String currentVersion, String... versions) {
		return ReleaseDigest.of(TestReleases.from(versions),
				currentVersion == null ? null : ArtifactVersion.of(currentVersion), 2, 10);
	}

	private static List<String> versions(List<Release> rows) {
		return rows.stream().map(release -> release.version().toString()).toList();
	}

}
