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

import biz.paluch.dap.artifact.ArtifactId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitHubAction}.
 *
 * @author Mark Paluch
 */
class GitHubActionUnitTests {

	@Test
	void parsesSimpleReference() {
		assertThat(GitHubAction.from("foo/bar@version")).isEqualTo(ArtifactId.of("foo", "bar"));
		assertThat(GitHubAction.from("foo/bar/some/path@version")).isEqualTo(ArtifactId.of("foo", "bar"));
	}

	@Test
	void considersVersion() {
		assertThat(GitHubAction.from("foo/bar@version").version()).isEqualTo("version");
		assertThat(GitHubAction.from("foo/bar@1.2.3").version()).isEqualTo("1.2.3");
		assertThat(GitHubAction.from("foo/bar@version # foo bar").version()).isEqualTo("version");
		assertThat(GitHubAction.from("foo/bar@version ").version()).isEqualTo("version");
	}

	@Test
	void parsesReferenceWithComment() {
		assertThat(GitHubAction.from("foo/bar@version # 1.2.3")).isEqualTo(ArtifactId.of("foo", "bar"));
		assertThat(GitHubAction.from("foo/bar/some/path@1.2.3 ")).isEqualTo(ArtifactId.of("foo", "bar"));
	}

	@Test
	void rejectsLocalRefs() {
		assertThat(GitHubAction.isValidUsage("./foo/bar@version")).isFalse();
	}

	@Test
	void rejectsEmptyRepo() {
		assertThat(GitHubAction.isValidUsage("foo/@version")).isFalse();
		assertThat(GitHubAction.isValidUsage("foo//@version")).isFalse();
		assertThat(GitHubAction.isValidUsage("/bar@version")).isFalse();
	}

	@Test
	void parsesRepositoryWithDotsAndUnderscores() {
		assertThat(GitHubAction.from("foo/my.repo@1.2.3")).isEqualTo(ArtifactId.of("foo", "my.repo"));
		assertThat(GitHubAction.from("foo/my_repo@1.2.3")).isEqualTo(ArtifactId.of("foo", "my_repo"));
		assertThat(GitHubAction.from("foo/my.dotted_repo-name@1.2.3"))
				.isEqualTo(ArtifactId.of("foo", "my.dotted_repo-name"));
	}

}
