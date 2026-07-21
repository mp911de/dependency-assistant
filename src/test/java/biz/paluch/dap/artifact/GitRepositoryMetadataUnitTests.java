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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.*;

/**
 * Unit tests for {@link GitRepositoryMetadata}.
 *
 * @author Mark Paluch
 */
class GitRepositoryMetadataUnitTests {

	@Test
	void flatTakesFirstTwoSegments() {

		GitRepositoryMetadata metadata = flat("https://github.com/octocat/hello-world");

		assertThat(metadata).isEqualTo(new GitRepositoryMetadata("github.com", "octocat", "hello-world"));
	}

	@Test
	void flatIgnoresWebPathDebris() {

		GitRepositoryMetadata metadata = flat("https://github.com/mojohaus/flatten-maven-plugin/tree/master");

		assertThat(metadata).isEqualTo(new GitRepositoryMetadata("github.com", "mojohaus", "flatten-maven-plugin"));
	}

	@Test
	void flatStripsInnerDotGitFromRepository() {

		GitRepositoryMetadata metadata = flat("https://github.com/assertj/assertj.git/assertj-parent/assertj-core");

		assertThat(metadata).isEqualTo(new GitRepositoryMetadata("github.com", "assertj", "assertj"));
	}

	@Test
	void flatRequiresOwnerAndRepositorySegments() {
		assertThat(flat("https://github.com/octocat")).isNull();
	}

	@Test
	void rendersCanonicalKey() {

		GitRepositoryMetadata metadata = new GitRepositoryMetadata("github.com", "octocat", "hello-world");

		assertThat(metadata.key()).isEqualTo("github.com/octocat/hello-world");
	}

	private static GitRepositoryMetadata flat(String url) {

		RemoteUrl remoteUrl = RemoteUrl.parse(url);
		assertThat(remoteUrl).isNotNull();
		return GitRepositoryMetadata.flat(remoteUrl);
	}

}
