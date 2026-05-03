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

import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.util.TextRange;

/**
 * Classification of an accepted NPM version value declared inside
 * {@code dependencies} or {@code devDependencies}.
 *
 * <p>A {@code package.json} value that does not match one of the variants is
 * skipped at the parser and produces no {@code NpmVersionExpression}; there is
 * no sentinel for "skipped" or "or-range" inputs. The replaceable range of each
 * variant is computed from the variant's own fields rather than stored
 * alongside them. The {@link Git} variant additionally needs the raw declared
 * text to locate the committish, since {@link NpmGitRef} does not retain the
 * surrounding URL.
 *
 * @author Mark Paluch
 * @see NpmDependency
 */
sealed interface NpmVersionExpression
		permits NpmVersionExpression.Exact, NpmVersionExpression.RangeUpper,
		NpmVersionExpression.Prefix, NpmVersionExpression.Alias,
		NpmVersionExpression.Git {

	/**
	 * Return the substring of the raw declared value that updaters may rewrite,
	 * expressed as offsets into the raw value text. The range never includes the
	 * surrounding JSON quote characters.
	 * @param rawDeclared the original raw value as written in the file; must not be
	 * {@literal null}.
	 * @return the replaceable text range; guaranteed to be not {@literal null}.
	 */
	TextRange replaceableRange(String rawDeclared);

	/**
	 * Return whether this expression carries a concrete version that updaters can
	 * rewrite. {@link Prefix} returns {@literal false}; suggestions are still shown
	 * but the file is left unchanged.
	 * @return {@literal true} if this expression accepts a target version;
	 * {@literal false} otherwise.
	 */
	boolean isUpdatable();

	/**
	 * Return the verbatim version text declared in the file, used as the
	 * {@link biz.paluch.dap.artifact.VersionSource VersionSource} payload.
	 * {@link Alias} unwraps to its inner expression and {@link Git} returns the
	 * committish.
	 * @return the declared version text; guaranteed to be not {@literal null}.
	 */
	String text();

	String renderUpdate(ArtifactVersion version);

	default Optional<ArtifactVersion> artifactVersion() {
		return ArtifactVersion.from(text());
	}

	default VersionSource versionSource() {
		return StringUtils.hasText(text()) ? VersionSource.declared(text()) : VersionSource.none();
	}


	/**
	 * Exact version with an optional comparator or {@code v} prefix.
	 *
	 * <p>Examples: {@code 1.6.8} (no modifier), {@code ^3.1.2}, {@code ~1.2.3},
	 * {@code =1.0.0}, {@code v2.0.0-beta.1}. The {@code modifier} captures the
	 * literal prefix (one of {@code ""}, {@code "^"}, {@code "~"}, {@code "="},
	 * {@code "v"}).
	 *
	 * @param modifier the literal modifier prefix; possibly empty.
	 * @param version the version tail without the modifier.
	 */
	record Exact(String modifier, String version) implements NpmVersionExpression {

		@Override
		public TextRange replaceableRange(String rawDeclared) {
			int start = modifier.length();
			return TextRange.from(start, version.length());
		}

		@Override
		public String renderUpdate(ArtifactVersion version) {
			return modifier + version;
		}

		@Override
		public boolean isUpdatable() {
			return true;
		}

		@Override
		public String text() {
			return version;
		}

	}

	/**
	 * Hyphen range or comparator-pair range.
	 *
	 * <p>Examples: {@code "1.0.0 - 2.9999.9999"}, {@code ">=1.0.2 <2.1.2"}. The
	 * {@code prefix} is the verbatim text up to and including the comparator that
	 * introduces the upper bound (for hyphen ranges, the literal {@code " - "}; for
	 * comparator pairs, the leading lower bound, separator, and the upper-bound
	 * comparator). The concrete version is the upper bound.
	 *
	 * @param prefix the leading text preserved verbatim during updates.
	 * @param upper the upper-bound version that participates in updates.
	 */
	record RangeUpper(String prefix, String upper) implements NpmVersionExpression {

		@Override
		public TextRange replaceableRange(String rawDeclared) {
			int start = prefix.length();
			return TextRange.from(start, upper.length());
		}

		@Override
		public String renderUpdate(ArtifactVersion version) {
			return prefix + version;
		}

		@Override
		public boolean isUpdatable() {
			return true;
		}

		@Override
		public String text() {
			return upper;
		}

	}

	/**
	 * Prefix range such as {@code 2.x}, {@code 3.4.x}, {@code 1.*}, or
	 * {@code 1.2.*}. Prefix ranges are highlighted so that suggestions can be shown
	 * but never rewritten by the updater.
	 *
	 * @param text the verbatim prefix-range text.
	 */
	record Prefix(String text) implements NpmVersionExpression {

		@Override
		public TextRange replaceableRange(String rawDeclared) {
			return TextRange.from(0, text.length());
		}

		@Override
		public String renderUpdate(ArtifactVersion version) {
			return version.toString();
		}

		@Override
		public boolean isUpdatable() {
			return false;
		}

		@Override
		public String text() {
			return text;
		}

		@Override
		public VersionSource versionSource() {
			return VersionSource.prefix(text());
		}

		public String getBaseVersion() {
			int index = text.indexOf(".x");
			return index != -1 ? text.substring(0, index) : text;
		}

	}

	/**
	 * Aliased dependency declared as {@code npm:<packageName>@<inner>}.
	 *
	 * <p>The {@code inner} expression must parse as one of the non-alias variants;
	 * nested aliases are rejected by the compact constructor.
	 *
	 * @param packageName the aliased package name written between {@code npm:} and
	 * {@code @}.
	 * @param inner the inner version expression that drives updates.
	 */
	record Alias(String packageName, NpmVersionExpression inner) implements NpmVersionExpression {

		public Alias {
			if (inner instanceof Alias) {
				throw new IllegalArgumentException("Nested npm: aliases are not supported");
			}
		}

		@Override
		public TextRange replaceableRange(String rawDeclared) {
			int aliasPrefixLength = "npm:".length() + packageName.length() + "@".length();
			String innerRaw = rawDeclared.length() >= aliasPrefixLength ? rawDeclared.substring(aliasPrefixLength)
					: "";
			TextRange innerRange = inner.replaceableRange(innerRaw);
			return TextRange.from(aliasPrefixLength + innerRange.getStartOffset(), innerRange.getLength());
		}

		@Override
		public String renderUpdate(ArtifactVersion version) {
			return "npm:" + packageName + "@" + inner.renderUpdate(version);
		}

		@Override
		public boolean isUpdatable() {
			return inner.isUpdatable();
		}

		@Override
		public String text() {
			return inner.text();
		}

	}

	/**
	 * Git URL that resolves through
	 * {@link biz.paluch.dap.artifact.GitRepositoryMetadata#parseGitUrl(String)} to
	 * a GitHub repository.
	 *
	 * <p>The replaceable range covers the committish substring written after
	 * {@code #} (or after {@code #semver:}) in the raw URL. The range is recomputed
	 * from the raw declared value rather than stored alongside the
	 * {@link NpmGitRef}, in keeping with the records-store-state-not-derivations
	 * rule.
	 *
	 * @param ref the resolved Git reference metadata.
	 */
	record Git(NpmGitRef ref) implements NpmVersionExpression {

		@Override
		public TextRange replaceableRange(String rawDeclared) {

			int hash = rawDeclared.indexOf('#');
			if (hash < 0) {
				return TextRange.from(rawDeclared.length(), 0);
			}

			int committishStart = NpmGitUrlParser.committishStart(rawDeclared, hash);
			return TextRange.from(committishStart, rawDeclared.length() - committishStart);
		}

		@Override
		public String renderUpdate(ArtifactVersion version) {
			return ref.renderUpdate(version);
		}

		@Override
		public boolean isUpdatable() {
			return StringUtils.hasText(text());
		}

		@Override
		public String text() {
			return ref.committish().text();
		}

		/**
		 * Create a new {@link GitArtifactId} using {@link ArtifactId} as artifact
		 * identifier and NpmGitRef as version source.
		 * @param artifactId the artifact identifier.
		 * @return a GitArtifactId with the given artifact identifier and the Git
		 * reference as version source.
		 */
		public GitArtifactId toArtifactId(ArtifactId artifactId) {
			return ref.repository().toArtifactId(artifactId);
		}

	}

}
