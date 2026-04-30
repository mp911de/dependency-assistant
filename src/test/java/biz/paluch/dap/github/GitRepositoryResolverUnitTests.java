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

import biz.paluch.dap.github.GitRepositoryResolver.GitRepositoryMetadata;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.*;

/**
 * Unit tests for {@link GitRepositoryResolver}.
 *
 * @author Mark Paluch
 */
class GitRepositoryResolverUnitTests {

	@Test
	void parsesHttpsGitHubUrl() {

		GitRepositoryMetadata parsed = GitRepositoryResolver.parseGitUrl("https://github.com/octocat/hello-world.git");

		assertThat(parsed).isNotNull();
		assertThat(parsed.host()).isEqualTo("github.com");
		assertThat(parsed.owner()).isEqualTo("octocat");
		assertThat(parsed.repository()).isEqualTo("hello-world");
	}

	@Test
	void parsesSshGitHubEnterpriseUrl() {

		GitRepositoryMetadata parsed = GitRepositoryResolver.parseGitUrl("git@github.example.com:owner/repo.git");

		assertThat(parsed).isNotNull();
		assertThat(parsed.host()).isEqualTo("github.example.com");
		assertThat(parsed.owner()).isEqualTo("owner");
		assertThat(parsed.repository()).isEqualTo("repo");
	}

	@Test
	void parsesSshUrlWithSchemeAndUserInfo() {

		GitRepositoryMetadata parsed = GitRepositoryResolver.parseGitUrl("ssh://git@github.com/owner/repo.git");

		assertThat(parsed).isNotNull();
		assertThat(parsed.host()).isEqualTo("github.com");
		assertThat(parsed.owner()).isEqualTo("owner");
		assertThat(parsed.repository()).isEqualTo("repo");
	}

	@Test
	void rejectsBlankOrMalformedInput() {

		assertThat(GitRepositoryResolver.parseGitUrl(null)).isNull();
		assertThat(GitRepositoryResolver.parseGitUrl("")).isNull();
		assertThat(GitRepositoryResolver.parseGitUrl("not-a-url")).isNull();
		assertThat(GitRepositoryResolver.parseGitUrl("https://github.com/owner")).isNull();
	}

	@Test
	void parseGitUrlExtractsOwnerAndRepository() {

		GitRepositoryMetadata parsed = GitRepositoryResolver.parseGitUrl("git@github.example.com:acme/example.git");

		assertThat(parsed).isNotNull();
		assertThat(parsed.owner()).isEqualTo("acme");
		assertThat(parsed.repository()).isEqualTo("example");
	}

}
