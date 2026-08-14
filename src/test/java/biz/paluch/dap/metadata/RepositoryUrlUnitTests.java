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

package biz.paluch.dap.metadata;

import java.util.stream.Stream;

import biz.paluch.dap.artifact.RemoteUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RepositoryUrl}.
 *
 * @author Mark Paluch
 */
class RepositoryUrlUnitTests {

	@ParameterizedTest
	@MethodSource("scmWrappedUrls")
	void peelsMavenScmWrapping(String declared, RepositoryType type, String url) {
		assertParsed(declared, type, url);
	}

	static Stream<Arguments> scmWrappedUrls() {
		return Stream.of(
				Arguments.of("scm:git:https://github.com/o/r.git", RepositoryType.GIT, "https://github.com/o/r"),
				Arguments.of("scm:git|https://server/repo", RepositoryType.GIT, "https://server/repo"),
				Arguments.of("scm:git:git://github.com/path/repo", RepositoryType.GIT, "git://github.com/path/repo"),
				Arguments.of("scm:git:git@github.com:owner/repo.git", RepositoryType.GIT, "git@github.com:owner/repo"),
				Arguments.of("scm:svn:https://svn.apache.org/svn/root/module", RepositoryType.SVN,
						"https://svn.apache.org/svn/root/module"),
				Arguments.of("scm:hg:http://host/v3", RepositoryType.UNKNOWN, "http://host/v3"));
	}

	@Test
	void takesFetchUrlFromDualUrlDeclaration() {
		assertParsed("scm:git:[fetch=]http://a/r[push=]ssh://b/r.git", RepositoryType.GIT, "http://a/r");
	}

	@ParameterizedTest
	@MethodSource("npmGitUrls")
	void normalizesNpmGitUrls(String declared, String url) {
		assertParsed(declared, RepositoryType.GIT, url);
	}

	static Stream<Arguments> npmGitUrls() {
		return Stream.of(
				Arguments.of("git+https://github.com/npm/cli.git", "https://github.com/npm/cli"),
				Arguments.of("git+ssh://git@github.com:npm/cli.git", "ssh://git@github.com:npm/cli"),
				Arguments.of("git://github.com/npm/cli.git#v1.0.27", "git://github.com/npm/cli"),
				Arguments.of("git+ssh://git@github.com:npm/cli#semver:^5.0", "ssh://git@github.com:npm/cli"),
				Arguments.of("git+https://github.com/emotion-js/emotion.git#main",
						"https://github.com/emotion-js/emotion"));
	}

	@ParameterizedTest
	@MethodSource("shorthands")
	void expandsNpmShorthand(String declared, String url) {
		assertParsed(declared, RepositoryType.GIT, url);
	}

	static Stream<Arguments> shorthands() {
		return Stream.of(
				Arguments.of("npm/example", "https://github.com/npm/example"),
				Arguments.of("owner/repo#v1.2.3", "https://github.com/owner/repo"),
				Arguments.of("github:npm/example", "https://github.com/npm/example"),
				Arguments.of("gitlab:user/repo", "https://gitlab.com/user/repo"),
				Arguments.of("bitbucket:user/repo", "https://bitbucket.org/user/repo"),
				Arguments.of("gist:11081aaa281", "https://gist.github.com/11081aaa281"));
	}

	@Test
	void doesNotExpandShorthandOutsideGitHubNamingRules() {

		assertThat(RepositoryUrl.parse("-owner/repo")).isNull();
		assertThat(RepositoryUrl.parse("owner-/repo")).isNull();
		assertParsed("own--er/repo", RepositoryType.UNKNOWN, "own--er/repo");
		assertParsed("owner/..", RepositoryType.UNKNOWN, "owner/..");
	}

	@Test
	void keepsPlainUrlWithUnknownType() {
		assertParsed("https://gitlab.com/gitlab-org/gitlab-runner/", RepositoryType.UNKNOWN,
				"https://gitlab.com/gitlab-org/gitlab-runner");
	}

	@Test
	void rejectsQueryUrl() {
		assertThat(RepositoryUrl.parse("https://gitbox.apache.org/repos/asf?p=commons-dbcp.git")).isNull();
	}

	@Test
	void rejectsScmWrappedQueryUrl() {
		assertThat(RepositoryUrl.parse("scm:git:https://gitbox.apache.org/repos/asf?p=commons-dbcp.git")).isNull();
	}

	@Test
	void doesNotExpandScmDelegateAsShorthand() {
		assertParsed("scm:git:some/path", RepositoryType.GIT, "some/path");
	}

	@Test
	void rejectsBlankAndUrlLessInput() {

		assertThat(RepositoryUrl.parse(null)).isNull();
		assertThat(RepositoryUrl.parse("  ")).isNull();
		assertThat(RepositoryUrl.parse("scm:git")).isNull();
		assertThat(RepositoryUrl.parse("scm:git:")).isNull();
		assertThat(RepositoryUrl.parse("#main")).isNull();
	}

	@Test
	void normalizedUrlCarriesRemoteUrl() {

		RepositoryUrl parsed = RepositoryUrl.parse("scm:git:git@github.com:spring-projects/spring-framework.git");

		assertThat(parsed).isNotNull();
		RemoteUrl remoteUrl = parsed.getRemote();
		assertThat(remoteUrl).isNotNull();
		assertThat(remoteUrl.host()).isEqualTo("github.com");
		assertThat(remoteUrl.pathSegments()).containsExactly("spring-projects", "spring-framework");
	}

	private static void assertParsed(String declared, RepositoryType type, String url) {

		assertThat(RepositoryUrl.parse(declared)).isNotNull()
				.extracting(RepositoryUrl::getType, RepositoryUrl::getUrl)
				.containsExactly(type, url);
	}

}
