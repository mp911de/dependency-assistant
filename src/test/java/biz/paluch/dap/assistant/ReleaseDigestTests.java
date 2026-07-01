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

package biz.paluch.dap.assistant;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.DocumentationContext.ReleaseDigest;
import biz.paluch.dap.fixtures.TestReleases;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DocumentationContext.ReleaseDigest}.
 *
 * @author Mark Paluch
 */
class ReleaseDigestTests {

	@Test
	void previewTrainCollapsesToNewestPreview() {

		ReleaseDigest digest = digest("3.9.4", "4.0.0-alpha.13", "4.0.0-alpha.12", "4.0.0-alpha.11",
				"4.0.0-alpha.10", "3.9.4", "3.9.3");

		assertThat(versions(digest)).containsExactly("4.0.0-alpha.13", "3.9.4");
		assertThat(digest.hiddenPreviews()).isEqualTo(3);
		assertThat(digest.hiddenReleases()).isEqualTo(1);
	}

	@Test
	void stableReleasesNewerThanCurrentPrecedeCurrentAnchor() {

		ReleaseDigest digest = digest("6.2.0", "6.3.0", "6.2.1", "6.2.0", "6.1.0");

		assertThat(versions(digest)).containsExactly("6.3.0", "6.2.1", "6.2.0");
		assertThat(digest.hiddenPreviews()).isZero();
		assertThat(digest.hiddenReleases()).isEqualTo(1);
	}

	@Test
	void stableRowsBeyondLimitAreCounted() {

		ReleaseDigest digest = ReleaseDigest.of(
				TestReleases.from("6.5.0", "6.4.0", "6.3.0", "6.2.1", "6.2.0"), ArtifactVersion.of("6.2.0"), 2);

		assertThat(versions(digest)).containsExactly("6.5.0", "6.4.0", "6.2.0");
		assertThat(digest.hiddenReleases()).isEqualTo(2);
	}

	@Test
	void withoutCurrentVersionListsStableAndNewestPreview() {

		ReleaseDigest digest = digest(null, "4.0.0-alpha.2", "4.0.0-alpha.1", "3.9.4", "3.9.3");

		assertThat(versions(digest)).containsExactly("4.0.0-alpha.2", "3.9.4", "3.9.3");
		assertThat(digest.hiddenPreviews()).isEqualTo(1);
		assertThat(digest.hiddenReleases()).isZero();
	}

	@Test
	void currentPreviewAnchorsBesideNewerPreview() {

		ReleaseDigest digest = digest("4.0.0-alpha.10", "4.0.0-alpha.13", "4.0.0-alpha.10", "3.9.4");

		assertThat(versions(digest)).containsExactly("4.0.0-alpha.13", "4.0.0-alpha.10");
		assertThat(digest.hiddenPreviews()).isZero();
		assertThat(digest.hiddenReleases()).isEqualTo(1);
	}

	@Test
	void upToDateDependencyShowsOnlyCurrentAnchor() {

		ReleaseDigest digest = digest("6.3.0", "6.3.0", "6.2.0", "6.1.0");

		assertThat(versions(digest)).containsExactly("6.3.0");
		assertThat(digest.hiddenReleases()).isEqualTo(2);
	}

	private static ReleaseDigest digest(@Nullable String currentVersion, String... versions) {
		return ReleaseDigest.of(TestReleases.from(versions),
				currentVersion == null ? null : ArtifactVersion.of(currentVersion), 10);
	}

	private static List<String> versions(ReleaseDigest digest) {
		return digest.rows().stream().map(release -> release.version().toString()).toList();
	}

}
