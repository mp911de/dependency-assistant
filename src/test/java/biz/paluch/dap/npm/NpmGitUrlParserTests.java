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

package biz.paluch.dap.npm;

import com.intellij.openapi.util.TextRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link NpmGitUrlParser}.
 *
 * @author Mark Paluch
 */
class NpmGitUrlParserTests {

	@Test
	void parsesGitSshUrlWithCommittish() {
		NpmVersionExpression.Git git = NpmGitUrlParser.parse("git+ssh://git@github.com:npm/cli.git#v1.0.27");
		assertThat(git).isNotNull();
		assertThat(git.ref().repository().host()).isEqualTo("github.com");
		assertThat(git.ref().repository().owner()).isEqualTo("npm");
		assertThat(git.ref().repository().repository()).isEqualTo("cli");
		assertThat(git.ref().committish()).isEqualTo("v1.0.27");
	}

	@Test
	void parsesGitHttpsUrl() {
		NpmVersionExpression.Git git = NpmGitUrlParser.parse("git+https://github.com/owner/repo.git#abc1234");
		assertThat(git).isNotNull();
		assertThat(git.ref().repository().owner()).isEqualTo("owner");
		assertThat(git.ref().repository().repository()).isEqualTo("repo");
		assertThat(git.ref().committish()).isEqualTo("abc1234");
	}

	@Test
	void parsesGitProtocolUrl() {
		NpmVersionExpression.Git git = NpmGitUrlParser.parse("git://github.com/owner/repo.git#main");
		assertThat(git).isNotNull();
		assertThat(git.ref().repository().host()).isEqualTo("github.com");
	}

	@Test
	void parsesShorthand() {
		NpmVersionExpression.Git git = NpmGitUrlParser.parse("owner/repo#v1.2.3");
		assertThat(git).isNotNull();
		assertThat(git.ref().repository().owner()).isEqualTo("owner");
		assertThat(git.ref().committish()).isEqualTo("v1.2.3");
	}

	@Test
	void parsesShorthandWithoutRef() {
		NpmVersionExpression.Git git = NpmGitUrlParser.parse("owner/repo");
		assertThat(git).isNotNull();
		assertThat(git.ref().committish()).isEmpty();
	}

	@Test
	void stripsSemverPrefix() {
		NpmVersionExpression.Git git = NpmGitUrlParser.parse("git+https://github.com/owner/repo.git#semver:^5.0");
		assertThat(git).isNotNull();
		assertThat(git.ref().committish()).isEqualTo("^5.0");
	}

	@Test
	void rejectsMalformedRemote() {
		assertThat(NpmGitUrlParser.parse("git+ssh://garbage")).isNull();
	}

	@Test
	void rejectsHttpUrl() {
		assertThat(NpmGitUrlParser.parse("https://example.com/foo/bar.tgz")).isNull();
	}

	@Test
	void rejectsTarballUrl() {
		assertThat(NpmGitUrlParser.parse("tarball://example.com/foo.tgz")).isNull();
	}

	@Test
	void rejectsSemverPrefixWithEmptyInner() {
		assertThat(NpmGitUrlParser.parse("git+https://github.com/owner/repo.git#semver:")).isNull();
	}

	@Test
	void rejectsSemverPrefixWithHyphenRange() {
		assertThat(NpmGitUrlParser.parse("git+https://github.com/owner/repo.git#semver:1.0.0 - 2.0.0")).isNull();
	}

	@Test
	void rejectsSemverPrefixWithPrefixRange() {
		assertThat(NpmGitUrlParser.parse("git+https://github.com/owner/repo.git#semver:2.x")).isNull();
	}

	@Test
	void replaceableRangePointsAfterHash() {
		String raw = "git+ssh://git@github.com:npm/cli.git#v1.0.27";
		NpmVersionExpression.Git git = NpmGitUrlParser.parse(raw);
		assertThat(git).isNotNull();
		TextRange range = git.replaceableRange(raw);
		assertThat(raw.substring(range.getStartOffset(), range.getEndOffset())).isEqualTo("v1.0.27");
	}

	@Test
	void replaceableRangePointsAfterSemverPrefix() {
		String raw = "git+https://github.com/owner/repo.git#semver:^5.0";
		NpmVersionExpression.Git git = NpmGitUrlParser.parse(raw);
		assertThat(git).isNotNull();
		TextRange range = git.replaceableRange(raw);
		assertThat(raw.substring(range.getStartOffset(), range.getEndOffset())).isEqualTo("^5.0");
	}

}
