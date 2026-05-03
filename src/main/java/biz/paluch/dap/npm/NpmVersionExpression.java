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
 * Contract for the NPM dependency version expressions that Dependency Assistant
 * can reason about.
 *
 * <p>This type intentionally models a smaller set than the full npm semver
 * grammar. A {@code package.json} value is represented only when the framework
 * can derive a single version-bearing segment for lookup, highlighting, and
 * safe replacement. Unsupported shapes such as {@code latest}, {@code *},
 * disjunctions using {@code ||}, malformed aliases, or unsupported URL schemes
 * return {@code null} from {@link #parse(String)}; there is no sentinel
 * expression for skipped inputs.
 *
 * <p>Each variant owns three related contracts:
 * <ul>
 * <li>{@link #text()} exposes the version-bearing text used for version
 * resolution and {@link VersionSource} creation.</li>
 * <li>{@link #replaceableRange(String)} identifies the substring in the raw
 * JSON string value that the IDE may highlight and, when {@link #isUpdatable()}
 * is {@code true}, rewrite.</li>
 * <li>{@link #renderUpdate(ArtifactVersion)} renders the expression as it would
 * look after applying a target version while preserving variant-specific syntax
 * such as modifiers, aliases, range separators, or Git URL prefixes.</li>
 * </ul>
 *
 * <p>Ranges and aliases expose the version-bearing part of their nested or
 * upper-bound expression. Git dependencies resolve their repository through
 * {@link GitRepositoryMetadata} and model the committish as another
 * {@code NpmVersionExpression}, allowing semantic Git refs, SHA refs, branches,
 * and {@code #semver:} refs to share the same replacement contract.
 *
 * @author Mark Paluch
 * @see NpmDependency
 */
sealed interface NpmVersionExpression
		permits NpmVersionExpression.Exact, NpmVersionExpression.Range,
		NpmVersionExpression.SimpleRange, NpmVersionExpression.Prefix,
		NpmVersionExpression.Alias, NpmVersionExpression.Git {

	/**
	 * Pattern for concrete NPM version texts accepted by this classifier.
	 *
	 * <pre class="code">
	 * &lt;number&gt;[.&lt;number&gt;][.&lt;number&gt;][-&lt;pre-release&gt; | +&lt;build&gt;]
	 *
	 * Examples: 1, 1.2, 1.2.3, 1.2.3-beta.1, 1.2.3+build.5
	 * </pre>
	 *
	 * <p>This pattern is intentionally update-oriented and not a complete npm
	 * semver validator.
	 */
	Pattern SEMVER = Pattern
			.compile("\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?");

	/**
	 * Pattern for NPM wildcard/prefix ranges recognized by this classifier.
	 *
	 * <pre class="code">
	 * &lt;wildcard&gt;
	 * &lt;number&gt;[.&lt;number&gt;]*.&lt;wildcard&gt;[.&lt;wildcard&gt;]*
	 *
	 * Examples: x, 2.x, 3.4.x, 1.*, 1.2.*
	 * </pre>
	 *
	 * <p>The parser still excludes bare {@code *}, since it provides no baseline
	 * version for lookup.
	 */
	Pattern PREFIX_RANGE = Pattern
			.compile("\\d+(?:\\.\\d+)*\\.(?:x|X|\\*)(?:\\.(?:x|X|\\*))*|\\d+\\.(?:x|X|\\*)|(?:x|X|\\*)");

	/**
	 * Pattern for NPM hyphen ranges whose upper bound can be updated.
	 *
	 * <pre class="code">
	 * &lt;version&gt; - &lt;version&gt;
	 *
	 * Examples: 1.0.0 - 2.9999.9999, 1.2 - 2.0.0, 1.0.0-beta - 2.0.0
	 * </pre>
	 */
	Pattern HYPHEN_RANGE = Pattern
			.compile(
					"(\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?)\\s+-\\s+(\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?)");

	/**
	 * Pattern for comparator-pair ranges whose right-hand side can be updated.
	 *
	 * <pre class="code">
	 * [&lt;comparator&gt;] &lt;version&gt; &lt;whitespace&gt; [&lt;comparator&gt;]&lt;version&gt;
	 *
	 * Examples: &gt;=1.0.2 &lt;2.1.2, &gt;=1.0.2 &lt;=2.1.2, 1.0.0 &lt;2.0.0
	 * </pre>
	 */
	Pattern COMPARATOR_PAIR = Pattern.compile(
			"^((?:>=|>|<=|<|=)?\\s*\\d+(?:\\.\\d+)*(?:[-+][0-9A-Za-z.-]+)?)\\s+((?:>=|>|<=|<|=)?\\d+(?:\\.\\d+)*(?:[-+][0-9A-Za-z.-]+)?)$");

	/**
	 * Pattern for NPM package aliases with an inner expression handled by this
	 * classifier.
	 *
	 * <pre class="code">
	 * npm:[@]&lt;name&gt;[/&lt;name&gt;]@&lt;version-expression&gt;
	 *
	 * Examples: npm:react@18.2.0, npm:@scope/name@^1.2.3, npm:fork@~2.0.0
	 * </pre>
	 */
	Pattern ALIAS = Pattern
			.compile("npm:(@?[a-z0-9][a-z0-9._-]*(?:/[a-z0-9][a-z0-9._-]*)?)@(.+)$");

	/**
	 * Create an exact expression without an npm range modifier.
	 * @param value the concrete version text; must not be empty or {@literal null}.
	 * @return the created exact expression.
	 */
	public static Exact exact(String value) {
		Assert.hasText(value, "Exact value must not be empty or null");
		return new Exact("", value);
	}

	/**
	 * Create an exact expression with the given npm range modifier.
	 * @param prefix the literal modifier to preserve when rendering updates.
	 * @param value the concrete version text; must not be empty or {@literal null}.
	 * @return the created exact expression.
	 */
	public static Exact exact(String prefix, String value) {
		Assert.hasText(value, "Exact value must not be empty or null");
		return new Exact(prefix, value);
	}

	/**
	 * Create a comparator-pair range from the given lower and upper expressions.
	 * @param lower the lower-bound expression text; must not be empty or
	 * {@literal null}.
	 * @param upper the upper-bound expression text; must not be empty or
	 * {@literal null}.
	 * @return the created range expression.
	 */
	public static Range range(String lower, String upper) {
		Assert.hasText(lower, "Lower value must not be empty or null");
		Assert.hasText(upper, "upper value must not be empty or null");
		return new Range(parse(lower), parse(upper));
	}

	/**
	 * Classify the raw value of a {@code package.json} dependency entry.
	 * <p>The input must be the JSON string value without surrounding quote
	 * characters. Returning {@literal null} means the value is intentionally out of
	 * scope for this dependency model, not necessarily invalid npm syntax.
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
	 * expressed as offsets into the raw value text.
	 * <p>The range never includes the surrounding JSON quote characters. Callers
	 * that operate on PSI text must apply the literal's own offset. A range may be
	 * zero-length for an otherwise recognized but unpinned Git dependency; callers
	 * should consult {@link #isUpdatable()} before rewriting.
	 * @param rawDeclared the original raw value as written in the file; must not be
	 * {@literal null}.
	 * @return the replaceable text range; guaranteed to be not {@literal null}.
	 */
	TextRange replaceableRange(String rawDeclared);

	/**
	 * Return whether this expression carries a rewritable version-bearing segment.
	 * <p>{@link Prefix} expressions are used for lookup and highlighting but are
	 * not rewritten. {@link Git} expressions without a committish are likewise not
	 * rewritten because there is no user-selected ref to preserve.
	 * @return {@literal true} if this expression accepts a target version;
	 * {@literal false} otherwise.
	 */
	boolean isUpdatable();

	/**
	 * Return the verbatim version text declared in the file, used as the
	 * {@link VersionSource} payload.
	 * <p>For composite expressions, this method returns the single text segment
	 * used for version lookup: the upper bound for ranges, the inner expression for
	 * aliases, and the committish for Git dependencies.
	 * @return the declared version text; guaranteed to be not {@literal null}.
	 */
	String text();

	/**
	 * Render this expression with the given target version.
	 * <p>The returned value represents the complete expression text for this
	 * variant, preserving syntax owned by the variant. Updaters that replace only
	 * {@link #replaceableRange(String)} may use variant-specific tails instead of
	 * this complete rendering.
	 * @param version the target artifact version; must not be {@literal null}.
	 * @return the rendered expression text; guaranteed to be not {@literal null}.
	 */
	String renderUpdate(ArtifactVersion version);

	/**
	 * Return the parsed artifact version represented by {@link #text()}, if the
	 * text can be parsed by the shared artifact version model.
	 * @return the parsed artifact version, or an empty {@link Optional}.
	 */
	default Optional<ArtifactVersion> artifactVersion() {
		return ArtifactVersion.from(text());
	}

	/**
	 * Return the source descriptor for the version-bearing text represented by this
	 * expression.
	 * @return the version source to register for dependency analysis.
	 */
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
		}

		if (tail.isEmpty()) {
			return null;
		}

		if (ArtifactVersion.from(tail).isPresent()) {
			return exact(modifier, tail);
		}

		return null;
	}

	/**
	 * Exact version or single comparator expression.
	 *
	 * <p>Examples: {@code 1.6.8} (no modifier), {@code ^3.1.2}, {@code ~1.2.3},
	 * {@code =1.0.0}, {@code <2.0.0}, {@code <=2.0.0}, {@code v2.0.0-beta.1}. The
	 * {@code modifier} captures the literal operator (one of {@code ""},
	 * {@code "^"}, {@code "~"}, {@code "="}, {@code "<"}, {@code "<="},
	 * {@code ">"}, {@code ">="}). A leading {@code v} remains part of the
	 * {@code version} text so the user's declaration style remains visible to
	 * lookup and rendering code.
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
	 * <p>The lower expression establishes the left boundary and is preserved when
	 * rendering an update. The upper expression is the version-bearing segment used
	 * for lookup, replacement, and {@link VersionSource} creation. This model keeps
	 * comparator ranges in the update flow while avoiding attempts to reason about
	 * arbitrary multi-clause npm ranges.
	 *
	 * @param lower the lower range boundary.
	 * @param upper the upper range boundary.
	 */
	record Range(NpmVersionExpression lower, NpmVersionExpression upper) implements NpmVersionExpression {

		@Override
		public TextRange replaceableRange(String rawDeclared) {
			int upperStart = upperStartIn(rawDeclared);
			String upperRaw = rawDeclared.substring(upperStart);
			TextRange upperRange = upper.replaceableRange(upperRaw);
			return TextRange.from(upperStart + upperRange.getStartOffset(), upperRange.getLength());
		}

		@Override
		public String renderUpdate(ArtifactVersion version) {
			return lower + " " + upper.renderUpdate(version);
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

		/**
		 * Locate the upper-bound expression in the raw declared text. The bounds may be
		 * separated by any non-empty whitespace run (per {@link #COMPARATOR_PAIR}), so
		 * the upper position is found by searching from the end of the lower expression
		 * rather than assuming a fixed separator length.
		 */
		private int upperStartIn(String rawDeclared) {
			String upperText = upper.toString();
			int searchFrom = Math.min(lower.toString().length(), rawDeclared.length());
			int found = rawDeclared.indexOf(upperText, searchFrom);
			return found >= 0 ? found : Math.min(searchFrom + 1, rawDeclared.length());
		}

	}

	/**
	 * Hyphen range such as {@code "1.0.0 - 2.9999.9999"}.
	 *
	 * <p>The {@code separator} stores the verbatim whitespace and hyphen between
	 * the lower and upper bounds so rendering preserves the original spacing. The
	 * upper bound is the version-bearing segment used for lookup and replacement.
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
	 * {@code 1.2.*}.
	 *
	 * <p>Prefix ranges provide enough information to infer a baseline version and
	 * show suggestions, but they do not identify a concrete version segment that
	 * can be safely rewritten. Consequently {@link #isUpdatable()} returns
	 * {@literal false}.
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

		/**
		 * Return the numeric prefix used as a baseline for version lookup.
		 * @return the prefix text before {@code .x}, or the full text if no {@code .x}
		 * marker is present.
		 */
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
	 * nested aliases are rejected by the compact constructor. Alias expressions
	 * preserve the target package name while delegating lookup, replacement, and
	 * update rendering to the inner expression.
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
	 * Git dependency whose URL resolves through
	 * {@link GitRepositoryMetadata#parseGitUrl(String)} to a GitHub repository.
	 *
	 * <p>The repository metadata determines the release source used for update
	 * lookup. The committish after {@code #} is modeled as another
	 * {@code NpmVersionExpression}: semantic tags keep their npm modifier shape,
	 * {@code #semver:} refs keep the {@code semver:} prefix in the surrounding
	 * {@link NpmGitRef}, and non-semver refs such as SHAs or branch names fall back
	 * to a raw {@link Exact} expression.
	 *
	 * <p>The replaceable range always covers only the committish text that should
	 * be rewritten. For {@code #semver:} refs, the range starts after the
	 * {@code semver:} marker so the marker itself is preserved.
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
		 * Return a {@link GitArtifactId} that keeps the declared NPM package
		 * coordinates while routing release lookup to the Git repository referenced by
		 * this expression.
		 * @param artifactId the declared NPM package coordinates.
		 * @return a Git-backed artifact identity for release lookup.
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
		 * Return whether the given value looks like an NPM Git dependency value handled
		 * by this variant.
		 *
		 * <ul>
		 * <li>{@code git+ssh://git@<host>[:/]<owner>/<repo>(.git)?(#<ref>)?}</li>
		 * <li>{@code git+https://<host>/<owner>/<repo>(.git)?(#<ref>)?}</li>
		 * <li>{@code git+http://<host>/<owner>/<repo>(.git)?(#<ref>)?}</li>
		 * <li>{@code git://<host>/<owner>/<repo>(.git)?(#<ref>)?}</li>
		 * <li>shorthand {@code <owner>/<repo>(#<ref>)?}</li>
		 * </ul>
		 * @param value the dependency value to inspect; can be {@literal null}.
		 * @return {@literal true} if the value should be parsed as a Git dependency;
		 * {@literal false} otherwise.
		 */
		public static boolean isGitUrl(@Nullable String value) {
			return StringUtils.hasText(value) && (isUrlForm(value) || looksLikeShorthand(value));
		}

		/**
		 * Parse the given raw value as an NPM Git dependency reference.
		 * @param raw the dependency value; must not be empty or {@literal null}.
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
