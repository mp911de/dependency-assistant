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
import com.intellij.openapi.util.TextRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link NpmVersionExpression}.
 *
 * @author Mark Paluch
 */
class NpmVersionExpressionTests {

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
	void rangeUpperReplacesUpperBoundOnly() {
		NpmVersionExpression.RangeUpper expression = new NpmVersionExpression.RangeUpper(">=1.0.2 <", "2.1.2");
		assertThat(expression.replaceableRange(">=1.0.2 <2.1.2")).isEqualTo(TextRange.from(9, 5));
		assertThat(expression.isUpdatable()).isTrue();
		assertThat(expression.renderUpdate(ArtifactVersion.of("1.0"))).isEqualTo(">=1.0.2 <1.0");
	}

	@Test
	void rangeUpperHyphenRange() {
		NpmVersionExpression.RangeUpper expression = new NpmVersionExpression.RangeUpper("1.0.0 - ", "2.9999.9999");
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

}
