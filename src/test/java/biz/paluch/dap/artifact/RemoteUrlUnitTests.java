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

package biz.paluch.dap.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RemoteUrl}.
 *
 * @author Mark Paluch
 */
class RemoteUrlUnitTests {

	@Test
	void parsesHttpsUrl() {

		RemoteUrl remoteUrl = RemoteUrl.parse("https://github.com/octocat/hello-world.git");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.host()).isEqualTo("github.com");
		assertThat(remoteUrl.pathSegments()).containsExactly("octocat", "hello-world");
	}

	@Test
	void parsesScpLikeUrl() {

		RemoteUrl remoteUrl = RemoteUrl.parse("git@github.example.com:owner/repo.git");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.host()).isEqualTo("github.example.com");
		assertThat(remoteUrl.pathSegments()).containsExactly("owner", "repo");
	}

	@Test
	void parsesSshUrlWithSchemeAndUserInfo() {

		RemoteUrl remoteUrl = RemoteUrl.parse("ssh://git@github.com/owner/repo.git");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.host()).isEqualTo("github.com");
		assertThat(remoteUrl.pathSegments()).containsExactly("owner", "repo");
	}

	@Test
	void parsesScpColonAfterScheme() {

		RemoteUrl remoteUrl = RemoteUrl.parse("git+ssh://git@github.com:npm/cli.git");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.host()).isEqualTo("github.com");
		assertThat(remoteUrl.pathSegments()).containsExactly("npm", "cli");
	}

	@Test
	void toleratesUserInfoInHttpsUrl() {

		RemoteUrl remoteUrl = RemoteUrl.parse("git+https://isaacs@github.com/npm/cli.git");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.host()).isEqualTo("github.com");
		assertThat(remoteUrl.pathSegments()).containsExactly("npm", "cli");
	}

	@Test
	void keepsNumericPortIntact() {

		RemoteUrl remoteUrl = RemoteUrl.parse("ssh://git@github.com:22/owner/repo.git");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.host()).isEqualTo("github.com");
		assertThat(remoteUrl.pathSegments()).containsExactly("owner", "repo");
	}

	@Test
	void retainsCustomHttpsPort() {

		RemoteUrl remoteUrl = RemoteUrl.parse("https://gitlab.example.com:8443/group/repo.git");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.host()).isEqualTo("gitlab.example.com:8443");
		assertThat(remoteUrl.pathSegments()).containsExactly("group", "repo");
	}

	@Test
	void dropsDefaultHttpsPort() {

		RemoteUrl remoteUrl = RemoteUrl.parse("https://gitlab.example.com:443/group/repo.git");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.host()).isEqualTo("gitlab.example.com");
	}

	@Test
	void dropsSshTransportPort() {

		RemoteUrl remoteUrl = RemoteUrl.parse("ssh://git@gitlab.example.com:2222/group/repo.git");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.host()).isEqualTo("gitlab.example.com");
		assertThat(remoteUrl.pathSegments()).containsExactly("group", "repo");
	}

	@Test
	void preservesNestedPathSegments() {

		RemoteUrl remoteUrl = RemoteUrl.parse("https://gitlab.com/gitlab-org/security-products/analyzers/semgrep");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.pathSegments())
				.containsExactly("gitlab-org", "security-products", "analyzers", "semgrep");
	}

	@Test
	void preservesWebPathSegments() {

		RemoteUrl remoteUrl = RemoteUrl.parse("https://github.com/mojohaus/flatten-maven-plugin/tree/master");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.pathSegments()).containsExactly("mojohaus", "flatten-maven-plugin", "tree", "master");
	}

	@Test
	void ignoresCommitIshFragment() {

		RemoteUrl remoteUrl = RemoteUrl.parse("https://github.com/owner/repo.git#v1.0.0");

		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.pathSegments()).containsExactly("owner", "repo");
	}

	@Test
	void rejectsQueryUrls() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> RemoteUrl.parse("https://gitbox.apache.org/repos/asf?p=commons-dbcp.git"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "not a url at all", "file:///local/path"})
	void rejectsNonRemoteValues(String url) {
		assertThatIllegalArgumentException().isThrownBy(() -> RemoteUrl.parse(url));
	}

}
