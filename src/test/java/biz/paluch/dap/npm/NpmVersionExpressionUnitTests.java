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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.npm.NpmVersionExpression.Exact;
import biz.paluch.dap.npm.NpmVersionExpression.Git;
import com.intellij.openapi.util.TextRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link NpmVersionExpression}.
 *
 * @author Mark Paluch
 */
class NpmVersionExpressionUnitTests {

	@Test
	void exactWithoutModifierReplacesEntireValue() {

		NpmVersionExpression.Exact expression = new NpmVersionExpression.Exact("", "1.6.8");

		assertThat(expression.replaceableRange("1.6.8")).isEqualTo(TextRange.from(0, 5));
		assertThat(expression.isUpdatable()).isTrue();
		assertThat(expression.renderUpdate(ArtifactVersion.of("1.0"))).isEqualTo("1.0");
	}

	@Test
	void exactWithCaretModifierExcludesModifier() {
		NpmVersionExpression.Exact expression = new NpmVersionExpression.Exact("^", "3.1.2");
		assertThat(expression.replaceableRange("^3.1.2")).isEqualTo(TextRange.from(1, 5));
		assertThat(expression.renderUpdate(ArtifactVersion.of("1.0"))).isEqualTo("^1.0");
	}

	@Test
	void rangeReplacesNestedUpperBoundOnly() {
		NpmVersionExpression.Exact lower = new NpmVersionExpression.Exact(">=", "1.0.2");
		NpmVersionExpression.Exact upper = new NpmVersionExpression.Exact("<", "2.1.2");
		NpmVersionExpression.Range expression = new NpmVersionExpression.Range(lower, upper);

		assertThat(expression.replaceableRange(">=1.0.2 <2.1.2")).isEqualTo(TextRange.from(9, 5));
		assertThat(expression.isUpdatable()).isTrue();
		assertThat(expression.renderUpdate(ArtifactVersion.of("1.0"))).isEqualTo(">=1.0.2 <1.0");
	}

	@Test
	void simpleRangeReplacesUpperBoundOnly() {
		NpmVersionExpression.SimpleRange expression = new NpmVersionExpression.SimpleRange("1.0.0", " - ",
				"2.9999.9999");

		assertThat(expression.replaceableRange("1.0.0 - 2.9999.9999")).isEqualTo(TextRange.from(8, 11));
		assertThat(expression.renderUpdate(ArtifactVersion.of("1.0"))).isEqualTo("1.0.0 - 1.0");
	}

	@Test
	void prefixCoversFullText() {
		NpmVersionExpression.Prefix expression = new NpmVersionExpression.Prefix("2.x");
		assertThat(expression.replaceableRange("2.x")).isEqualTo(TextRange.from(0, 3));
		assertThat(expression.isUpdatable()).isFalse();
		assertThat(expression.renderUpdate(ArtifactVersion.of("1.0"))).isEqualTo("1.0");
	}

	@Test
	void aliasOffsetsAccountForNpmPrefix() {

		NpmVersionExpression.Exact inner = new NpmVersionExpression.Exact("^", "3.0.2");
		NpmVersionExpression.Alias alias = new NpmVersionExpression.Alias("@ankurk91/bootstrap-vue", inner);
		// "npm:@ankurk91/bootstrap-vue@^3.0.2" → caret at offset 27, version at 28

		int expectedStart = "npm:".length() + "@ankurk91/bootstrap-vue".length() + "@".length() + "^".length();
		assertThat(alias.replaceableRange("npm:@ankurk91/bootstrap-vue@^3.0.2"))
				.isEqualTo(TextRange.from(expectedStart, 5));
		assertThat(alias.isUpdatable()).isTrue();
		assertThat(alias.renderUpdate(ArtifactVersion.of("1.0"))).isEqualTo("npm:@ankurk91/bootstrap-vue@^1.0");
	}

	@Test
	void aliasRejectsNestedAlias() {

		NpmVersionExpression.Alias inner = new NpmVersionExpression.Alias("inner",
				new NpmVersionExpression.Exact("", "1.0.0"));

		assertThatIllegalArgumentException()
				.isThrownBy(() -> new NpmVersionExpression.Alias("outer", inner))
				.withMessageContaining("Nested npm: aliases");
	}

	@Test
	void gitReplaceableRangeMatchesCommittish() {

		NpmGitRef ref = new NpmGitRef("git+ssh://git@github.com:npm/cli.git#",
				new GitRepositoryMetadata("github.com", "npm", "cli"), new Exact("", "v1.0.27"));
		NpmVersionExpression.Git git = new NpmVersionExpression.Git(ref);

		// "git+ssh://git@github.com:npm/cli.git#v1.0.27" → committish at offset 37
		assertThat(git.replaceableRange("git+ssh://git@github.com:npm/cli.git#v1.0.27"))
				.isEqualTo(TextRange.from(37, 7));
		assertThat(git.isUpdatable()).isTrue();
		assertThat(git.renderUpdate(ArtifactVersion.of("1.0"))).isEqualTo("git+ssh://git@github.com:npm/cli.git#1.0");
	}

	@Test
	void gitWithoutCommittishIsNotUpdatable() {

		NpmGitRef ref = new NpmGitRef("", new GitRepositoryMetadata("github.com", "npm", "cli"), new Exact("", ""));
		NpmVersionExpression.Git git = new NpmVersionExpression.Git(ref);

		assertThat(git.isUpdatable()).isFalse();
	}

	@Test
	void gitReplaceableRangeAccountsForSemverPrefix() {

		NpmGitRef ref = new NpmGitRef("git+https://github.com/owner/repo.git#semver:",
				new GitRepositoryMetadata("github.com", "owner", "repo"), new Exact("^", "5.0"));
		NpmVersionExpression.Git git = new NpmVersionExpression.Git(ref);

		String raw = "git+https://github.com/owner/repo.git#semver:^5.0";

		assertThat(git.replaceableRange(raw)).isEqualTo(TextRange.from(45, 4));
		assertThat(git.renderUpdate(ArtifactVersion.of("1.0")))
				.isEqualTo("git+https://github.com/owner/repo.git#semver:^1.0");
	}

	@Test
	void parsesGitSshUrlWithCommittish() {
		NpmVersionExpression.Git git = Git.parse("git+ssh://git@github.com:npm/cli.git#v1.0.27");
		assertThat(git).isNotNull();
		assertThat(git.ref().repository().host()).isEqualTo("github.com");
		assertThat(git.ref().repository().owner()).isEqualTo("npm");
		assertThat(git.ref().repository().repository()).isEqualTo("cli");
		assertThat(git.ref().committish().text()).isEqualTo("v1.0.27");
	}

	@Test
	void parsesGitHttpsUrl() {
		NpmVersionExpression.Git git = Git.parse("git+https://github.com/owner/repo.git#abc1234");
		assertThat(git).isNotNull();
		assertThat(git.ref().repository().owner()).isEqualTo("owner");
		assertThat(git.ref().repository().repository()).isEqualTo("repo");
		assertThat(git.ref().committish().text()).isEqualTo("abc1234");
	}

	@Test
	void parsesGitProtocolUrl() {
		NpmVersionExpression.Git git = Git.parse("git://github.com/owner/repo.git#main");
		assertThat(git).isNotNull();
		assertThat(git.ref().repository().host()).isEqualTo("github.com");
	}

	@Test
	void parsesShorthand() {
		NpmVersionExpression.Git git = Git.parse("owner/repo#v1.2.3");
		assertThat(git).isNotNull();
		assertThat(git.ref().repository().owner()).isEqualTo("owner");
		assertThat(git.ref().committish().text()).isEqualTo("v1.2.3");
	}

	@Test
	void parsesShorthandWithoutRef() {
		NpmVersionExpression.Git git = Git.parse("owner/repo");
		assertThat(git).isNotNull();
		assertThat(git.ref().committish().text()).isEmpty();
	}

	@Test
	void stripsSemverPrefix() {
		NpmVersionExpression.Git git = Git.parse("git+https://github.com/owner/repo.git#semver:^5.0");
		assertThat(git).isNotNull();
		assertThat(git.ref().committish().text()).isEqualTo("5.0");
	}

	@Test
	void rejectsMalformedRemote() {
		assertThat(Git.parse("git+ssh://garbage")).isNull();
	}

	@Test
	void rejectsHttpUrl() {
		assertThat(Git.parse("https://example.com/foo/bar.tgz")).isNull();
	}

	@Test
	void rejectsTarballUrl() {
		assertThat(Git.parse("tarball://example.com/foo.tgz")).isNull();
	}

	@Test
	void rejectsSemverPrefixWithEmptyInner() {
		assertThat(Git.parse("git+https://github.com/owner/repo.git#semver:")).isNull();
	}

	@Test
	void rejectsSemverPrefixWithHyphenRange() {
		assertThat(Git.parse("git+https://github.com/owner/repo.git#semver:1.0.0 - 2.0.0")).isNotNull();
	}

	@Test
	void rejectsSemverPrefixWithPrefixRange() {
		assertThat(Git.parse("git+https://github.com/owner/repo.git#semver:2.x")).isNotNull();
	}

	@Test
	void replaceableRangePointsAfterHash() {
		String raw = "git+ssh://git@github.com:npm/cli.git#v1.0.27";
		NpmVersionExpression.Git git = Git.parse(raw);
		assertThat(git).isNotNull();
		TextRange range = git.replaceableRange(raw);
		assertThat(raw.substring(range.getStartOffset(), range.getEndOffset())).isEqualTo("v1.0.27");
	}

	@Test
	void replaceableRangePointsAfterSemverPrefix() {
		String raw = "git+https://github.com/owner/repo.git#semver:^5.0";
		NpmVersionExpression.Git git = Git.parse(raw);
		assertThat(git).isNotNull();
		TextRange range = git.replaceableRange(raw);
		assertThat(raw.substring(range.getStartOffset(), range.getEndOffset())).isEqualTo("^5.0");
	}

	@Test
	void parsesPlainSemver() {
		NpmVersionExpression expression = NpmVersionExpression.parse("1.6.8");
		assertThat(expression).isEqualTo(new NpmVersionExpression.Exact("", "1.6.8"));
	}

	@Test
	void parsesCaretRange() {
		assertThat(NpmVersionExpression.parse("^3.1.2"))
				.isEqualTo(new NpmVersionExpression.Exact("^", "3.1.2"));
	}

	@Test
	void parsesTildeRange() {
		assertThat(NpmVersionExpression.parse("~1.2.3"))
				.isEqualTo(new NpmVersionExpression.Exact("~", "1.2.3"));
	}

	@Test
	void parsesEqualsExact() {
		assertThat(NpmVersionExpression.parse("=1.0.0"))
				.isEqualTo(new NpmVersionExpression.Exact("=", "1.0.0"));
	}

	@Test
	void parsesVPrefixedVersion() {
		assertThat(NpmVersionExpression.parse("v2.0.0-beta.1"))
				.isEqualTo(new NpmVersionExpression.Exact("", "v2.0.0-beta.1"));
	}

	@Test
	void parsesPrefixRange() {
		assertThat(NpmVersionExpression.parse("2.x"))
				.isEqualTo(new NpmVersionExpression.Prefix("2.x"));
		assertThat(NpmVersionExpression.parse("3.4.x"))
				.isEqualTo(new NpmVersionExpression.Prefix("3.4.x"));
		assertThat(NpmVersionExpression.parse("1.*"))
				.isEqualTo(new NpmVersionExpression.Prefix("1.*"));
	}

	@Test
	void parsesHyphenRange() {
		NpmVersionExpression expression = NpmVersionExpression.parse("1.0.0 - 2.9999.9999");
		assertThat(expression).isEqualTo(new NpmVersionExpression.SimpleRange("1.0.0", " - ", "2.9999.9999"));
	}

	@Test
	void parsesComparatorPair() {
		NpmVersionExpression expression = NpmVersionExpression.parse(">=1.0.2 <2.1.2");
		NpmVersionExpression.Exact lower = new NpmVersionExpression.Exact(">=", "1.0.2");
		NpmVersionExpression.Exact upper = new NpmVersionExpression.Exact("<", "2.1.2");
		assertThat(expression).isEqualTo(new NpmVersionExpression.Range(lower, upper));
	}

	@Test
	void parsesComparatorPairWithLessThanOrEqualUpper() {
		NpmVersionExpression expression = NpmVersionExpression.parse(">=1.0.2 <=2.1.2");
		NpmVersionExpression.Exact lower = new NpmVersionExpression.Exact(">=", "1.0.2");
		NpmVersionExpression.Exact upper = new NpmVersionExpression.Exact("<=", "2.1.2");
		assertThat(expression).isEqualTo(new NpmVersionExpression.Range(lower, upper));
	}

	@Test
	void parsesAlias() {
		NpmVersionExpression expression = NpmVersionExpression.parse("npm:@ankurk91/bootstrap-vue@^3.0.2");
		NpmVersionExpression.Exact inner = new NpmVersionExpression.Exact("^", "3.0.2");
		assertThat(expression).isEqualTo(new NpmVersionExpression.Alias("@ankurk91/bootstrap-vue", inner));
	}

	@Test
	void rejectsAliasWithoutInnerVersion() {
		assertThat(NpmVersionExpression.parse("npm:@scope/name@")).isNull();
		assertThat(NpmVersionExpression.parse("npm:@scope/name")).isNull();
	}

	@Test
	void rejectsNestedAlias() {
		assertThat(NpmVersionExpression.parse("npm:a@npm:b@1.0.0")).isNull();
	}

	@Test
	void considersRange() {
		assertThat(NpmVersionExpression.parse(">=1.0.0 <2.x")).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "*", "latest", "LATEST", "<1.0.0 || >=2.3.1"})
	void rejectsOutOfScopeShapes(String input) {
		assertThat(NpmVersionExpression.parse(input)).isNull();
	}

	@Test
	void rejectsNullInput() {
		assertThat(NpmVersionExpression.parse(null)).isNull();
	}

	@Test
	void considersSuffixedVersion() {
		assertThat(NpmVersionExpression.parse("1.6.8.RELEASE")).isNull();
	}

}
