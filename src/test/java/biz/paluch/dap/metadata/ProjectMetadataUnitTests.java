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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.fixtures.TestProjects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ProjectMetadata}.
 *
 * @author Mark Paluch
 */
class ProjectMetadataUnitTests {

	@Test
	void findsReleaseNotesUrlForPrefixedTag() {

		ProjectMetadata metadata = releaseNotes("scm:git:https://gitlab.com/assertj/assertj.git",
				"assertj-build-3.27.7", "assertj-build-3.27.6", "assertj-build-4.0.0-M1");

		assertThat(metadata.findReleaseNotesUrl(ArtifactVersion.of("3.27.7"))).hasToString(
				"https://gitlab.com/assertj/assertj/-/releases/assertj-build-3.27.7");
		assertThat(metadata.findReleaseNotesUrl(ArtifactVersion.of("4.0.0-M1"))).hasToString(
				"https://gitlab.com/assertj/assertj/-/releases/assertj-build-4.0.0-M1");
	}

	@Test
	void findsReleaseNotesUrlForPlainAndVPrefixedTags() {

		ProjectMetadata metadata = releaseNotes("scm:git:https://gitlab.com/micrometer-metrics/micrometer.git",
				"v1.14.2", "1.3.3-RELEASE");

		assertThat(metadata.findReleaseNotesUrl(ArtifactVersion.of("1.14.2"))).hasToString(
				"https://gitlab.com/micrometer-metrics/micrometer/-/releases/v1.14.2");
		assertThat(metadata.findReleaseNotesUrl(ArtifactVersion.of("1.3.3-RELEASE"))).hasToString(
				"https://gitlab.com/micrometer-metrics/micrometer/-/releases/1.3.3-RELEASE");
	}

	@Test
	void returnsNullForUnknownVersionOrMissingRepository() {

		ProjectMetadata withTags = releaseNotes("scm:git:https://gitlab.com/assertj/assertj.git",
				"assertj-build-3.27.7");

		assertThat(withTags.findReleaseNotesUrl(ArtifactVersion.of("9.9.9"))).isNull();
		assertThat(ProjectMetadata.absent().findReleaseNotesUrl(ArtifactVersion.of("3.27.7"))).isNull();
	}

	@Test
	void firstTagPerVersionWins() {

		ProjectMetadata metadata = releaseNotes("scm:git:https://gitlab.com/assertj/assertj.git",
				"assertj-build-2.0.0", "assertj-swing-2.0.0");

		assertThat(metadata.findReleaseNotesUrl(ArtifactVersion.of("2.0.0"))).hasToString(
				"https://gitlab.com/assertj/assertj/-/releases/assertj-build-2.0.0");
	}

	private static ProjectMetadata releaseNotes(String repositoryUrl, String... tags) {

		RepositoryUrl parsed = RepositoryUrl.parse(repositoryUrl);
		assertThat(parsed).isNotNull();
		RepositoryConnection connection = new GitLabPlatform().detect(parsed, null);
		assertThat(connection).isNotNull();
		return ProjectMetadata.from(null, connection, connection.createRepository(TestProjects.PROJECT), null,
				List.of(tags));
	}

}
