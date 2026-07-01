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

package biz.paluch.dap.gradle;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GradleDistributionService}.
 *
 * @author Mark Paluch
 */
class GradleDistributionServiceTests {

	@Test
	void returnsStableReleasesWithReleaseDateAndSha() throws IOException {

		GradleDistributionService source = new GradleDistributionService(uri -> """
				[
				  {
				    "version": "8.14.3",
				    "buildTime": "20250704101213+0000",
				    "checksum": "4f36f2",
				    "snapshot": false,
				    "nightly": false,
				    "broken": false,
				    "rcFor": "",
				    "milestoneFor": ""
				  }
				]
				""");

		List<Release> releases = source.getReleases(GradleDistributionService.GRADLE_DISTRIBUTION,
				new AbstractProgressIndicatorBase());

		assertThat(releases).singleElement().satisfies(release -> {
			assertThat(release.version()).isEqualTo(GitVersion.of("4f36f2", ArtifactVersion.of("8.14.3")));
			assertThat(release.releaseDate()).isEqualTo(LocalDateTime.parse("2025-07-04T10:12:13"));
		});
	}

	@Test
	void dropsSnapshotNightlyBrokenRcAndMilestoneEntries() throws IOException {

		GradleDistributionService source = new GradleDistributionService(uri -> """
				[
				  { "version": "8.14.3", "checksum": "stable", "snapshot": false, "nightly": false, "broken": false },
				  { "version": "8.15.0-20260201000000+0000", "snapshot": true },
				  { "version": "8.15-20260201000000+0000", "nightly": true },
				  { "version": "8.14.4", "broken": true },
				  { "version": "9.0.0-rc-1", "rcFor": "9.0.0" },
				  { "version": "9.0.0-milestone-1", "milestoneFor": "9.0.0" }
				]
				""");

		List<Release> releases = source.getReleases(GradleDistributionService.GRADLE_DISTRIBUTION,
				new AbstractProgressIndicatorBase());

		assertThat(releases).extracting(release -> release.version().toString()).containsExactly("8.14.3");
	}

	@Test
	void returnsEmptyForNonGradleDistributionArtifact() throws IOException {

		GradleDistributionService source = new GradleDistributionService(uri -> {
			throw new AssertionError("Should not fetch versions for non-Gradle artifacts");
		});

		List<Release> releases = source.getReleases(ArtifactId.of("org.springframework", "spring-core"),
				new AbstractProgressIndicatorBase());

		assertThat(releases).isEmpty();
	}

	@Test
	void honorsCancellationBeforeFetching() {

		GradleDistributionService source = new GradleDistributionService(uri -> {
			throw new AssertionError("Should not fetch after cancellation");
		});
		assertThatExceptionOfType(ProcessCanceledException.class)
				.isThrownBy(() -> source.getReleases(GradleDistributionService.GRADLE_DISTRIBUTION,
						new CancelingProgressIndicator()));
	}

	@Test
	void fetchesGradleVersionsAllEndpoint() throws IOException {

		GradleDistributionService source = new GradleDistributionService(uri -> {
			assertThat(uri).isEqualTo(URI.create("https://services.gradle.org/versions/all"));
			return "[]";
		});

		assertThat(source.getReleases(GradleDistributionService.GRADLE_DISTRIBUTION,
				new AbstractProgressIndicatorBase())).isEmpty();
	}

	private static class CancelingProgressIndicator extends AbstractProgressIndicatorBase {

		@Override
		public void checkCanceled() {
			throw new ProcessCanceledException();
		}

	}

}
