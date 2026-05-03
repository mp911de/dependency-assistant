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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

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
		permits NpmVersionExpression.Exact, NpmVersionExpression.Range,
		NpmVersionExpression.SimpleRange, NpmVersionExpression.Prefix,
		NpmVersionExpression.Alias, NpmVersionExpression.Git {

	/**
	 * Pattern for supported concrete NPM versions. <pre class="code">
	 * &lt;number&gt;[.&lt;number&gt;][.&lt;number&gt;][-&lt;pre-release&gt; | +&lt;build&gt;]
	 *
	 * Examples: 1, 1.2, 1.2.3, 1.2.3-beta.1, 1.2.3+build.5
	 * </pre>
	 */
	Pattern SEMVER = Pattern
			.compile("\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?");

	/**
	 * Pattern for NPM wildcard/prefix ranges. <pre class="code">
	 * &lt;wildcard&gt;
	 * &lt;number&gt;[.&lt;number&gt;]*.&lt;wildcard&gt;[.&lt;wildcard&gt;]*
	 *
	 * Examples: *, x, 2.x, 3.4.x, 1.*, 1.2.*
	 * </pre>
	 */
	Pattern PREFIX_RANGE = Pattern
			.compile("\\d+(?:\\.\\d+)*\\.(?:x|X|\\*)(?:\\.(?:x|X|\\*))*|\\d+\\.(?:x|X|\\*)|(?:x|X|\\*)");

	/**
	 * Pattern for NPM hyphen ranges. <pre class="code">
	 * &lt;version&gt; - &lt;version&gt;
	 *
	 * Examples: 1.0.0 - 2.9999.9999, 1.2 - 2.0.0, 1.0.0-beta - 2.0.0
	 * </pre>
	 */
	Pattern HYPHEN_RANGE = Pattern
			.compile(
					"(\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?)\\s+-\\s+(\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?)");

	/**
	 * Pattern for NPM comparator-pair ranges. <pre class="code">
	 * [&lt;comparator&gt;] &lt;version&gt; &lt;whitespace&gt; [&lt;comparator&gt;]&lt;version&gt;
	 *
	 * Examples: &gt;=1.0.2 &lt;2.1.2, &gt;=1.0.2 &lt;=2.1.2, 1.0.0 &lt;2.0.0
	 * </pre>
	 */
	Pattern COMPARATOR_PAIR = Pattern.compile(
			"^((?:>=|>|<=|<|=)?\\s*\\d+(?:\\.\\d+)*(?:[-+][0-9A-Za-z.-]+)?)\\s+((?:>=|>|<=|<|=)?\\d+(?:\\.\\d+)*(?:[-+][0-9A-Za-z.-]+)?)$");

	/**
	 * Pattern for NPM package aliases. <pre class="code">
	 * npm:[@]&lt;name&gt;[/&lt;name&gt;]@&lt;version-expression&gt;
	 *
	 * Examples: npm:react@18.2.0, npm:@scope/name@^1.2.3, npm:fork@~2.0.0
	 * </pre>
	 */
	Pattern ALIAS = Pattern
			.compile("npm:(@?[a-z0-9][a-z0-9._-]*(?:/[a-z0-9][a-z0-9._-]*)?)@(.+)$");

	public static Exact exact(String value) {
		Assert.hasText(value, "Exact value must not be empty or null");
		return new Exact("", value);
	}


	public static Exact exact(String prefix, String value) {
		Assert.hasText(value, "Exact value must not be empty or null");
		return new Exact(prefix, value);
	}

	public static Range range(String lower, String upper) {
		Assert.hasText(lower, "Lower value must not be empty or null");
		Assert.hasText(upper, "upper value must not be empty or null");
		return new Range(parse(lower), parse(upper));
	}

	/**
	 * Classify the raw value of a {@code package.json} dependency entry.
	 * @param value the dependency value text without surrounding quotes; can be
	 * {@literal null}.
	 * @return the parsed expression, or {@literal null} if the value is out of
	 * scope or not classifiable.
	 */
	@Contract("null -> null")
	public static @Nullable NpmVersionExpression parse(@Nullable String value) {

		if (StringUtils.isEmpty(value) || value.contains("||")) {
			return null;
		}

		value = value.trim();

		if (Git.isGitUrl(value)) {
			return Git.parse(value);
		}

		if (value.startsWith("npm:")) {
			Matcher aliasMatcher = ALIAS.matcher(value);
			if (aliasMatcher.matches()) {
				NpmVersionExpression inner = parse(aliasMatcher.group(2));
				if (inner == null || inner instanceof Alias) {
					return null;
				}
				return new Alias(aliasMatcher.group(1), inner);
			}
			return null;
		}

		String trimmed = value.trim();
		if (trimmed.equals("*") || trimmed.equalsIgnoreCase("latest")) {
			return null;
		}

		if (PREFIX_RANGE.matcher(trimmed).matches()) {
			return new Prefix(trimmed);
		}

		Matcher hyphen = HYPHEN_RANGE.matcher(trimmed);
		if (hyphen.matches()) {
			String lower = hyphen.group(1);
			String upper = hyphen.group(2);
			String separator = trimmed.substring(hyphen.end(1), hyphen.start(2));
			return new SimpleRange(lower, separator, upper);
		}

		Matcher pair = COMPARATOR_PAIR.matcher(trimmed);
		if (pair.matches()) {
			NpmVersionExpression lower = parse(pair.group(1));
			NpmVersionExpression upper = parse(pair.group(2));
			if (lower == null || upper == null) {
				return null;
			}
			return new Range(lower, upper);
		}

		return parseExact(trimmed);
	}

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


	private static @Nullable NpmVersionExpression parseExact(String value) {

		if (value.isEmpty()) {
			return null;
		}

		String modifier = "";
		String tail = value;
		char first = value.charAt(0);
		if (value.startsWith(">=") || value.startsWith("<=")) {
			modifier = value.substring(0, 2);
			tail = value.substring(2);
		} else if (first == '^' || first == '~' || first == '=' || first == '>' || first == '<') {
			modifier = String.valueOf(first);
			tail = value.substring(1);
		} else if (first == 'v' && value.length() > 1 && Character.isDigit(value.charAt(1))) {
			tail = value;
		}

		if (tail.isEmpty()) {
			return null;
		}

		String version = tail;
		if (version.length() > 1 && version.charAt(0) == 'v' && Character.isDigit(version.charAt(1))) {
			version = version.substring(1);
		}
		if (!SEMVER.matcher(version).matches()) {
			return null;
		}

		return exact(modifier, tail);
	}

	/**
	 * Exact version with an optional comparator.
	 *
	 * <p>Examples: {@code 1.6.8} (no modifier), {@code ^3.1.2}, {@code ~1.2.3},
	 * {@code =1.0.0}, {@code <2.0.0}, {@code <=2.0.0}, {@code v2.0.0-beta.1}. The
	 * {@code modifier} captures the literal operator (one of {@code ""},
	 * {@code "^"}, {@code "~"}, {@code "="}, {@code "<"}, {@code "<="},
	 * {@code ">"}, {@code ">="}). A leading {@code v} remains part of the
	 * {@code version} text.
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

		@Override
		public String toString() {
			return modifier + version;
		}

	}

	/**
	 * Comparator-pair range whose right-hand side is modeled as its own expression.
	 *
	 * <p>Example: {@code ">=1.0.2 <2.1.2"} is represented as
	 * {@code Range(">=1.0.2 ", Exact("<", "2.1.2"))}. The {@code prefix} is the
	 * verbatim lower-bound expression and separator. The nested expression
	 * participates in replacement, rendering, and version-source lookup.
	 *
	 * @param lower the lower range boundary.
	 * @param upper the upper range boundary.
	 */
	record Range(NpmVersionExpression lower, NpmVersionExpression upper) implements NpmVersionExpression {

		@Override
		public TextRange replaceableRange(String rawDeclared) {
			String expressionRaw = rawDeclared.length() >= lower.text().length()
					? rawDeclared.substring(lower.text().length())
					: "";
			TextRange expressionRange = upper.replaceableRange(expressionRaw);
			return TextRange.from(lower.text().length() + expressionRange.getStartOffset(),
					expressionRange.getLength());
		}

		@Override
		public String renderUpdate(ArtifactVersion version) {
			return lower() + upper.renderUpdate(version);
		}

		@Override
		public boolean isUpdatable() {
			return upper.isUpdatable();
		}

		@Override
		public String text() {
			return upper.text();
		}

		@Override
		public String toString() {
			return lower + " " + upper.toString();
		}

	}

	/**
	 * Hyphen range such as {@code "1.0.0 - 2.9999.9999"}.
	 *
	 * <p>The {@code separator} stores the verbatim whitespace and hyphen between
	 * the lower and upper bounds so rendering preserves the original spacing. The
	 * concrete version is the upper bound.
	 *
	 * @param lower the lower-bound version text.
	 * @param separator the verbatim text between lower and upper bounds.
	 * @param upper the upper-bound version that participates in updates.
	 */
	record SimpleRange(String lower, String separator, String upper) implements NpmVersionExpression {

		@Override
		public TextRange replaceableRange(String rawDeclared) {
			return TextRange.from(lower.length() + separator.length(), upper.length());
		}

		@Override
		public String renderUpdate(ArtifactVersion version) {
			return lower + separator + version;
		}

		@Override
		public boolean isUpdatable() {
			return true;
		}

		@Override
		public String text() {
			return upper;
		}

		@Override
		public String toString() {
			return lower + separator + upper;
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

		@Override
		public String toString() {
			return text();
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

		@Override
		public String toString() {
			return packageName() + inner();
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
	 *
	 * @param ref the resolved Git reference metadata.
	 */
	record Git(NpmGitRef ref) implements NpmVersionExpression {

		static final Pattern SHORTHAND = Pattern
				.compile("^(?<owner>[A-Za-z0-9._-]+)/(?<repo>[A-Za-z0-9._-]+?)(?:#(?<ref>.*))?$");

		static final String SEMVER_PREFIX = "semver:";

		/**
		 * Return the offset of the first committish character within {@code raw},
		 * skipping past the leading {@code semver:} marker when present.
		 * @param raw the raw declared value containing a {@code #} at
		 * {@code hashIndex}.
		 * @param hashIndex the index of the {@code #} that introduces the committish.
		 * @return the offset of the first committish character.
		 */
		static int committishStart(String raw, int hashIndex) {
			int start = hashIndex + 1;
			return raw.startsWith(SEMVER_PREFIX, start) ? start + SEMVER_PREFIX.length() : start;
		}

		@Override
		public TextRange replaceableRange(String rawDeclared) {

			int hash = rawDeclared.indexOf('#');
			if (hash < 0) {
				return TextRange.from(rawDeclared.length(), 0);
			}

			int committishStart = committishStart(rawDeclared, hash);
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

		@Override
		public String toString() {
			return ref.toString();
		}

		private static boolean isUrlForm(String value) {
			return value.startsWith("git+ssh://") || value.startsWith("git+https://")
					|| value.startsWith("git://") || value.startsWith("git+http://");
		}

		private static boolean looksLikeShorthand(String value) {

			if (value.contains("://") || value.startsWith("@") || value.startsWith("/")) {
				return false;
			}
			int slash = value.indexOf('/');
			int hash = value.indexOf('#');
			int colon = value.indexOf(':');
			// Reject schemed values such as npm:, file:, link:, workspace: which carry a
			// colon before any slash and are not Git shorthand.
			if (colon >= 0 && (slash < 0 || colon < slash)) {
				return false;
			}
			return slash > 0 && (hash < 0 || hash > slash);
		}

		/**
		 * <ul>
		 * <li>{@code git+ssh://git@<host>[:/]<owner>/<repo>(.git)?(#<ref>)?}</li>
		 * <li>{@code git+https://<host>/<owner>/<repo>(.git)?(#<ref>)?}</li>
		 * <li>{@code git://<host>/<owner>/<repo>(.git)?(#<ref>)?}</li>
		 * <li>shorthand {@code <owner>/<repo>(#<ref>)?}</li>
		 * </ul>
		 */
		public static boolean isGitUrl(@Nullable String value) {
			return StringUtils.hasText(value) && (isUrlForm(value) || looksLikeShorthand(value));
		}

		/**
		 * Parse the given raw value as an NPM Git dependency reference.
		 * @param raw the dependency value; can be {@literal null}.
		 * @return the parsed Git variant, or {@literal null} if the input is not a
		 * recognized Git form or does not resolve to a GitHub repository.
		 */
		public static NpmVersionExpression.@Nullable Git parse(String raw) {

			Assert.hasText(raw, "Raw value must not be null or empty");

			if (isUrlForm(raw)) {
				return parseUrl(raw);
			}

			if (looksLikeShorthand(raw)) {
				return parseShorthand(raw);
			}

			return null;
		}

		private static NpmVersionExpression.@Nullable Git parseUrl(String value) {

			int hash = value.indexOf('#');
			String urlPart = hash < 0 ? value : value.substring(0, hash);
			String committishRaw = hash < 0 ? "" : value.substring(hash + 1);

			String normalized = normalizeForResolver(urlPart);
			GitRepositoryMetadata repository = GitRepositoryMetadata.parseGitUrl(normalized);
			if (repository == null) {
				return null;
			}

			NpmVersionExpression committish = stripSemverPrefix(committishRaw);
			if (committish == null) {
				return null;
			}

			return new NpmVersionExpression.Git(new NpmGitRef(urlPart + "#", repository, committish));
		}

		private static NpmVersionExpression.@Nullable Git parseShorthand(String value) {

			java.util.regex.Matcher matcher = SHORTHAND.matcher(value);
			if (!matcher.matches()) {
				return null;
			}

			String owner = matcher.group("owner");
			String repo = matcher.group("repo");
			String ref = matcher.group("ref");

			String url = "https://github.com/%s/%s.git".formatted(owner, repo);
			GitRepositoryMetadata repository = GitRepositoryMetadata.parseGitUrl(url);
			if (repository == null) {
				return null;
			}

			String committishRaw = ref != null ? ref : "";
			NpmVersionExpression committish = stripSemverPrefix(committishRaw);
			if (committish == null) {
				return null;
			}
			String prefix = value.replace(committishRaw, "");

			return new NpmVersionExpression.Git(new NpmGitRef(prefix, repository, committish));
		}

		private static String normalizeForResolver(String urlPart) {

			// "git+ssh://git@github.com:owner/repo.git" → "git@github.com:owner/repo.git"
			if (urlPart.startsWith("git+ssh://")) {
				return urlPart.substring("git+ssh://".length());
			}
			if (urlPart.startsWith("git+https://")) {
				return urlPart.substring("git+".length());
			}
			if (urlPart.startsWith("git+http://")) {
				return urlPart.substring("git+".length());
			}
			return urlPart;
		}

		/**
		 * Strip a leading {@code semver:} marker and parse the inner expression through
		 * the NPM version classifier. {@literal null} return signals the entry should
		 * be skipped; an unprefixed committish falls back to a raw Git ref when it is
		 * not package-version syntax.
		 */
		private static @Nullable NpmVersionExpression stripSemverPrefix(String committish) {

			if (committish.startsWith(SEMVER_PREFIX)) {
				String inner = committish.substring(SEMVER_PREFIX.length());
				if (inner.isEmpty()) {
					return null;
				}

				return NpmVersionExpression.parse(inner);
			}

			if (committish.isEmpty()) {
				return new Exact("", "");
			}

			NpmVersionExpression parsed = NpmVersionExpression.parse(committish);
			return parsed != null ? parsed : new Exact("", committish);
		}

	}

}
