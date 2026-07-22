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

package biz.paluch.dap.metadata;

import java.io.IOException;
import java.util.List;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.RemoteUrl;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link GitLabReleases}.
 *
 * @author Mark Paluch
 */
class GitLabReleasesUnitTests {

	private final GitLabReleases source = source("https://gitlab.com/gitlab-org/gitlab-runner");

	@Test
	void parsesTagShas() throws IOException {

		String body = """
				[
				  { "name": "v17.1.0", "commit": { "id": "aaaaaaa" } },
				  { "name": "v17.0.0", "commit": { "id": "bbbbbbb" } },
				  { "name": "no-commit" },
				  { "commit": { "id": "ccccccc" } }
				]
				""";

		assertThat(source.parseTagShas(body)).containsExactly(
				entry("v17.1.0", "aaaaaaa"),
				entry("v17.0.0", "bbbbbbb"));
	}

	@Test
	void mergesReleasesWithTagShas() throws IOException {

		String releases = """
				[
				  { "tag_name": "v17.1.0", "released_at": "2024-06-20T09:15:00.000Z" },
				  { "tag_name": "v17.0.0", "released_at": "2024-05-16T12:00:00.000Z" }
				]
				""";
		String tags = """
				[
				  { "name": "v17.1.0", "commit": { "id": "aaaaaaa" } },
				  { "name": "v17.0.0", "commit": { "id": "bbbbbbb" } }
				]
				""";

		List<Release> result = source.mergeReleases(releases, tags);

		assertThat(result).extracting(release -> release.version().toString())
				.containsExactly("v17.1.0", "v17.0.0");
		assertThat(result.getFirst()).hasReleaseDate("2024-06-20T09:15").hasSha("aaaaaaa");
	}

	@Test
	void tagWithoutReleaseContributesShaOnlyVersion() throws IOException {

		String releases = """
				[
				  { "tag_name": "v17.1.0", "released_at": "2024-06-20T09:15:00.000Z" }
				]
				""";
		String tags = """
				[
				  { "name": "v17.1.0", "commit": { "id": "aaaaaaa" } },
				  { "name": "v17.0.0", "commit": { "id": "bbbbbbb" } }
				]
				""";

		List<Release> result = source.mergeReleases(releases, tags);

		assertThat(result).extracting(release -> release.version().toString())
				.containsExactly("v17.1.0", "v17.0.0");
		assertThat(result.getLast()).hasNoReleaseDate().hasSha("bbbbbbb");
	}

	@Test
	void releaseBeyondTagPageFallsBackToReleaseCommit() throws IOException {

		String releases = """
				[
				  { "tag_name": "v1.0.0", "released_at": "2020-01-03T01:56:19.539Z",
				    "commit": { "id": "ddddddd" } }
				]
				""";

		List<Release> result = source.mergeReleases(releases, "[]");

		assertThat(result).hasSize(1);
		assertThat(result.getFirst()).hasSha("ddddddd");
	}

	@Test
	void skipsUpcomingReleasesAndUnnamedEntries() throws IOException {

		String releases = """
				[
				  { "tag_name": "v18.0.0", "released_at": "2027-01-01T00:00:00Z", "upcoming_release": true },
				  { "released_at": "2024-06-20T09:15:00.000Z" },
				  { "tag_name": "v17.1.0", "released_at": "2024-06-20T09:15:00.000Z" }
				]
				""";
		String tags = """
				[
				  { "name": "v18.0.0", "commit": { "id": "eeeeeee" } }
				]
				""";

		// the upcoming tag is seen and must not resurface through the tag fallback
		assertThat(source.mergeReleases(releases, tags))
				.extracting(release -> release.version().toString())
				.containsExactly("v17.1.0");
	}

	@Test
	void toleratesUnknownResponseFields() throws IOException {

		String releases = """
				[
				  { "tag_name": "v17.1.0", "released_at": "2024-06-20T09:15:00.000Z",
				    "name": "GitLab Runner 17.1", "description": "notes", "_links": { "self": "https://x" } }
				]
				""";

		assertThat(source.mergeReleases(releases, "[]"))
				.extracting(release -> release.version().toString())
				.containsExactly("v17.1.0");
	}

	@Test
	void encodesNestedGroupProjectId() {

		GitLabReleases nested = source("https://gitlab.com/gitlab-org/security-products/analyzers/semgrep");

		assertThat(nested.encodedProjectId()).isEqualTo("gitlab-org%2Fsecurity-products%2Fanalyzers%2Fsemgrep");
		assertThat(nested.tagsUri()).hasToString(
				"https://gitlab.com/api/v4/projects/gitlab-org%2Fsecurity-products%2Fanalyzers%2Fsemgrep/repository/tags?per_page=100");
		assertThat(nested.releasesUri()).hasToString(
				"https://gitlab.com/api/v4/projects/gitlab-org%2Fsecurity-products%2Fanalyzers%2Fsemgrep/releases?per_page=100");
	}

	@Test
	void targetsSelfHostedInstanceHost() {

		GitLabReleases selfHosted = source("https://gitlab.freedesktop.org/mesa/mesa");

		assertThat(selfHosted.getId()).isEqualTo("GitLab[gitlab.freedesktop.org]");
		assertThat(selfHosted.tagsUri()).hasToString(
				"https://gitlab.freedesktop.org/api/v4/projects/mesa%2Fmesa/repository/tags?per_page=100");
	}

	private static GitLabReleases source(String url) {

		RemoteUrl remoteUrl = RemoteUrl.parse(url);
		assertThat(remoteUrl).isNotNull();

		List<String> segments = remoteUrl.pathSegments();
		return new GitLabReleases(new GitRepositoryMetadata(remoteUrl.host(),
				String.join("/", segments.subList(0, segments.size() - 1)), segments.getLast()));
	}

}
