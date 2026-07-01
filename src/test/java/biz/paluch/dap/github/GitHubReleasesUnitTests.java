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

package biz.paluch.dap.github;

import java.io.IOException;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitHubReleases}.
 *
 * @author Mark Paluch
 */
class GitHubReleasesUnitTests {

	private static final ArtifactId ARTIFACT_ID = ArtifactId.of("actions", "checkout");

	@Test
	void degradesWhenTagFetchFailsWithIoException() throws IOException {

		GitHubReleases releases = new GitHubReleases(GithubServerPath.DEFAULT_SERVER,
				new TestGitHubApiClient(true, false), 100);

		assertThat(releases.fetchAllReleases(ARTIFACT_ID, null)).hasSize(1);
	}

	@Test
	void degradesWhenReleaseFetchFailsWithIoException() throws IOException {

		GitHubReleases releases = new GitHubReleases(GithubServerPath.DEFAULT_SERVER,
				new TestGitHubApiClient(false, true), 100);

		assertThat(releases.fetchAllReleases(ARTIFACT_ID, null)).hasSize(1);
	}

	private static class TestGitHubApiClient implements GitHubReleases.GitHubApiClient {

		private final boolean failTags;

		private final boolean failReleases;

		TestGitHubApiClient(boolean failTags, boolean failReleases) {
			this.failTags = failTags;
			this.failReleases = failReleases;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> List<T> loadAll(ProgressIndicator indicator, GithubApiPagesLoader.Request<T> request)
				throws IOException {

			if (this.failReleases) {
				throw new IOException("connection refused");
			}
			return (List<T>) List.of(
					new GitHubReleases.GitHubReleaseDto("v1.0.0", "2026-01-01T00:00:00Z", false));
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T loadOne(ProgressIndicator indicator, GithubApiRequest<T> request) throws IOException {

			if (this.failTags) {
				throw new IOException("connection refused");
			}
			return (T) new GithubResponsePage<>(
					List.of(new GitHubReleases.GitHubTagDto("v1.2.3", new GitHubReleases.GitHubCommitRefDto("sha"))),
					null, null, null, null);
		}

	}

}
