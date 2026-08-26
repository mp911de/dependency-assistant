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

package biz.paluch.dap.npm;

import java.io.IOException;

import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.util.Sequence;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link NpmRegistry}.
 *
 * @author Mark Paluch
 */
class NpmRegistryTests {

	private NpmRegistry SOURCE = NpmRegistry.NPM_REGISTRY;

	@Test
	void parsesAxiosLikePayload() throws IOException {

		String body = """
				{
				  "name": "axios",
				  "versions": {
				    "1.6.7": { "gitHead": "1111111"},
				    "1.6.8": { "gitHead": "2222222"},
				    "1.7.0": { "gitHead": "3333333"}
				  },
				  "time": {
				    "1.6.7": "2024-02-01T10:00:00Z",
				    "1.6.8": "2024-03-01T10:00:00Z",
				    "1.7.0": "2024-04-01T10:00:00Z"
				  }
				}
				""";

		Sequence<Release> releases = SOURCE.parseReleases(body);

		for (Release release : releases) {
			assertThat(release.version()).isInstanceOf(GitVersion.class);
		}
		assertThat(releases).extracting(r -> r.version().toString())
				.containsExactlyInAnyOrder("1.6.7", "1.6.8", "1.7.0");
		assertThat(releases).allSatisfy(r -> assertThat(r.releaseDate()).isNotNull());
	}

	@Test
	void parsesScopedPackagePayload() throws IOException {

		String body = """
				{
				  "name": "@vitejs/plugin-vue",
				  "versions": {
				    "3.1.0": {},
				    "3.1.2": {}
				  },
				  "time": {
				    "3.1.0": "2023-09-01T10:00:00Z",
				    "3.1.2": "2023-09-15T10:00:00Z"
				  }
				}
				""";

		Sequence<Release> releases = SOURCE.parseReleases(body);

		assertThat(releases).extracting(r -> r.version().toString())
				.containsExactlyInAnyOrder("3.1.0", "3.1.2");
	}

	@Test
	void preservesPreReleaseVersions() throws IOException {

		String body = """
				{
				  "versions": {
				    "1.0.0": {},
				    "1.1.0-rc.1": {},
				    "1.1.0-beta": {},
				    "1.1.0-next.0": {}
				  },
				  "time": {
				    "1.0.0": "2024-01-01T10:00:00Z",
				    "1.1.0-rc.1": "2024-02-01T10:00:00Z",
				    "1.1.0-beta": "2024-01-20T10:00:00Z",
				    "1.1.0-next.0": "2024-01-25T10:00:00Z"
				  }
				}
				""";

		Sequence<Release> releases = SOURCE.parseReleases(body);

		assertThat(releases).extracting(r -> r.version().toString())
				.containsExactlyInAnyOrder("1.0.0", "1.1.0-rc.1", "1.1.0-beta", "1.1.0-next.0");
	}

	@Test
	void encodesScopedPackageName() {
		assertThat(NpmRegistry.encodePackageName("axios")).isEqualTo("axios");
		assertThat(NpmRegistry.encodePackageName("@vitejs/plugin-vue"))
				.isEqualTo("%40vitejs/plugin-vue");
	}

	@Test
	void parsesPayloadWithoutTimeMap() throws IOException {

		String body = """
				{
				  "versions": {
				    "1.0.0": {},
				    "2.0.0": {}
				  }
				}
				""";

		Sequence<Release> releases = SOURCE.parseReleases(body);

		assertThat(releases).hasSize(2);
		assertThat(releases).allSatisfy(r -> assertThat(r.releaseDate()).isNull());
	}

	@Test
	void capturesRepositoryAndBugsFromLatestVersionDocument() throws IOException {

		CachedMetadata metadata = parseMetadata("""
				{
				  "dist-tags": { "latest": "3.4.0" },
				  "versions": {
				    "3.4.0": {
				      "repository": { "type": "git", "url": "git+https://github.com/vuejs/core.git" },
				      "bugs": { "url": "https://github.com/vuejs/core/issues" }
				    }
				  }
				}
				""");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("git+https://github.com/vuejs/core.git");
		assertThat(metadata.getIssueTrackerUrl()).isEqualTo("https://github.com/vuejs/core/issues");
	}

	@Test
	void capturesMonorepoRepositoryUrlIgnoringDirectory() throws IOException {

		CachedMetadata metadata = parseMetadata("""
				{
				  "dist-tags": { "latest": "19.0.0" },
				  "versions": {
				    "19.0.0": {
				      "repository": {
				        "type": "git",
				        "url": "git+https://github.com/facebook/react.git",
				        "directory": "packages/react"
				      }
				    }
				  }
				}
				""");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("git+https://github.com/facebook/react.git");
	}

	@Test
	void capturesGitLabNestedGroupRepository() throws IOException {

		CachedMetadata metadata = parseMetadata("""
				{
				  "dist-tags": { "latest": "1.0.0" },
				  "versions": {
				    "1.0.0": {
				      "repository": {
				        "type": "git",
				        "url": "git+https://gitlab.com/gitlab-org/gitlab-services/design.gitlab.com.git",
				        "directory": "packages/gitlab-ui"
				      }
				    }
				  }
				}
				""");

		assertThat(metadata.getRepositoryUrl())
				.isEqualTo("git+https://gitlab.com/gitlab-org/gitlab-services/design.gitlab.com.git");
	}

	@Test
	void capturesLegacyPathKeyRepository() throws IOException {

		CachedMetadata metadata = parseMetadata("""
				{
				  "dist-tags": { "latest": "2.3.3" },
				  "versions": {
				    "2.3.3": {
				      "repository": { "type": "git", "path": "git://github.com/astro/node-expat.git" }
				    }
				  }
				}
				""");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("git://github.com/astro/node-expat.git");
	}

	@Test
	void capturesRepositoryUrlWithCommitIshFragment() throws IOException {

		CachedMetadata metadata = parseMetadata("""
				{
				  "dist-tags": { "latest": "11.14.0" },
				  "versions": {
				    "11.14.0": {
				      "repository": { "type": "git", "url": "git+https://github.com/emotion-js/emotion.git#main" }
				    }
				  }
				}
				""");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("git+https://github.com/emotion-js/emotion.git#main");
	}

	@Test
	void dropsDoubledSchemeTrackerUrl() throws IOException {

		CachedMetadata metadata = parseMetadata("""
				{
				  "dist-tags": { "latest": "0.1.20" },
				  "versions": {
				    "0.1.20": {
				      "bugs": { "url": "http://http://github.com/tmpvar/jsdom/issues", "email": "tmpvar@gmail.com" }
				    }
				  }
				}
				""");

		assertThat(metadata.getRepositoryUrl()).isNull();
		assertThat(metadata.getIssueTrackerUrl()).isNull();
	}

	@Test
	void emailOnlyBugsCountsAsAbsent() throws IOException {

		CachedMetadata metadata = parseMetadata("""
				{
				  "dist-tags": { "latest": "1.0.0" },
				  "versions": {
				    "1.0.0": {
				      "bugs": { "email": "bugs@example.com" }
				    }
				  }
				}
				""");

		assertThat(metadata.getIssueTrackerUrl()).isNull();
	}

	@Test
	void fallsBackToTopLevelWhenLatestVersionLacksMetadata() throws IOException {

		CachedMetadata metadata = parseMetadata("""
				{
				  "dist-tags": { "latest": "1.0.0" },
				  "repository": "https://github.com/owner/repo",
				  "bugs": "https://github.com/owner/repo/issues",
				  "versions": {
				    "1.0.0": {}
				  }
				}
				""");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("https://github.com/owner/repo");
		assertThat(metadata.getIssueTrackerUrl()).isEqualTo("https://github.com/owner/repo/issues");
	}

	@Test
	void prefersLatestVersionDocumentOverTopLevelCopy() throws IOException {

		CachedMetadata metadata = parseMetadata("""
				{
				  "dist-tags": { "latest": "2.0.0" },
				  "repository": { "type": "git", "url": "git+https://github.com/owner/old-repo.git" },
				  "versions": {
				    "2.0.0": {
				      "repository": { "type": "git", "url": "git+https://github.com/owner/new-repo.git" }
				    }
				  }
				}
				""");

		assertThat(metadata.getRepositoryUrl()).isEqualTo("git+https://github.com/owner/new-repo.git");
	}

	@Test
	void ignoresBulkFieldsSurroundingTheConsumedOnes() throws IOException {

		String body = """
				{
				  "_id": "demo",
				  "readme": "# demo\\n\\nlots of prose",
				  "users": { "someone": true },
				  "versions": {
				    "1.0.0": {
				      "dist": { "tarball": "https://registry.npmjs.org/demo/-/demo-1.0.0.tgz", "shasum": "abc" },
				      "scripts": { "build": "tsc" },
				      "maintainers": [ { "name": "someone" } ],
				      "gitHead": "1111111",
				      "bugs": { "url": "https://github.com/owner/repo/issues" }
				    }
				  },
				  "dist-tags": { "latest": "1.0.0" },
				  "maintainers": [ { "name": "someone" } ],
				  "time": { "created": "2024-01-01T10:00:00Z", "1.0.0": "2024-02-01T10:00:00Z" },
				  "repository": "https://github.com/owner/repo"
				}
				""";

		NpmReleases releases = (NpmReleases) SOURCE.parseReleases(body);

		assertThat(releases).singleElement().satisfies(release -> {
			assertThat(release.version().toString()).isEqualTo("1.0.0");
			assertThat(release.releaseDate()).isNotNull();
		});
		assertThat(releases.getProjectMetadata().getRepositoryUrl()).isEqualTo("https://github.com/owner/repo");
		assertThat(releases.getProjectMetadata().getIssueTrackerUrl())
				.isEqualTo("https://github.com/owner/repo/issues");
	}

	@Test
	void readsFieldsRegardlessOfDocumentOrder() throws IOException {

		String versionsFirst = """
				{
				  "versions": { "1.0.0": { "repository": "https://github.com/owner/repo" } },
				  "dist-tags": { "latest": "1.0.0" }
				}
				""";

		assertThat(parseMetadata(versionsFirst).getRepositoryUrl()).isEqualTo("https://github.com/owner/repo");
	}

	@Test
	void toleratesNonObjectVersionDocuments() throws IOException {

		String body = """
				{
				  "versions": { "1.0.0": null, "2.0.0": [], "3.0.0": {} }
				}
				""";

		Sequence<Release> releases = SOURCE.parseReleases(body);

		assertThat(releases).extracting(r -> r.version().toString())
				.containsExactlyInAnyOrder("1.0.0", "2.0.0", "3.0.0");
	}

	@Test
	void emptySequenceWhenVersionsIsNotAnObject() throws IOException {
		assertThat(SOURCE.parseReleases("{ \"versions\": null }")).isEmpty();
		assertThat(SOURCE.parseReleases("{ \"name\": \"demo\" }")).isEmpty();
		assertThat(SOURCE.parseReleases("[]")).isEmpty();
	}

	private CachedMetadata parseMetadata(String body) throws IOException {

		NpmReleases releases = (NpmReleases) SOURCE.parseReleases(body);
		return releases.getProjectMetadata();
	}

}
