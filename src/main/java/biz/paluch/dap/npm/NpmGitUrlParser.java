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

import java.util.regex.Pattern;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.npm.NpmVersionExpression.Exact;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Classifier for the Git URL forms accepted as NPM dependency values.
 *
 * <p>Accepts the following raw forms and dispatches owner/repository extraction
 * through {@link GitRepositoryMetadata#parseGitUrl(String)} so the existing
 * {@code [A-Za-z0-9._-]+} allowlist remains the single point of validation:
 *
 * <ul>
 * <li>{@code git+ssh://git@<host>[:/]<owner>/<repo>(.git)?(#<ref>)?}</li>
 * <li>{@code git+https://<host>/<owner>/<repo>(.git)?(#<ref>)?}</li>
 * <li>{@code git://<host>/<owner>/<repo>(.git)?(#<ref>)?}</li>
 * <li>shorthand {@code <owner>/<repo>(#<ref>)?}</li>
 * </ul>
 *
 * <p>A {@code #semver:} committish whose inner expression is empty or a hyphen
 * range / prefix range produces no Git variant; the entry is skipped because no
 * single concrete version can be derived for update purposes.
 *
 * @author Mark Paluch
 */
final class NpmGitUrlParser {

	private static final Pattern SHORTHAND = Pattern
			.compile("^(?<owner>[A-Za-z0-9._-]+)/(?<repo>[A-Za-z0-9._-]+?)(?:#(?<ref>.*))?$");

	private static final String SEMVER_PREFIX = "semver:";

	private NpmGitUrlParser() {
	}

	static boolean isGitUrl(@Nullable String value) {
		return StringUtils.hasText(value) && (isUrlForm(value) || looksLikeShorthand(value));
	}

	/**
	 * Parse the given raw value as an NPM Git dependency reference.
	 * @param raw the dependency value; can be {@literal null}.
	 * @return the parsed Git variant, or {@literal null} if the input is not a
	 * recognized Git form or does not resolve to a GitHub repository.
	 */
	static NpmVersionExpression.@Nullable Git parse(String raw) {

		Assert.hasText(raw, "Raw value must not be null or empty");

		if (isUrlForm(raw)) {
			return parseUrl(raw);
		}

		if (looksLikeShorthand(raw)) {
			return parseShorthand(raw);
		}

		return null;
	}

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
			committish = new Exact("", "");
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
	 * Strip a leading {@code semver:} marker and reject inner shapes that cannot be
	 * rendered as a concrete committish. {@literal null} return signals the entry
	 * should be skipped; an unprefixed committish is returned unchanged.
	 */
	private static @Nullable NpmVersionExpression stripSemverPrefix(String committish) {

		if (committish.contains(SEMVER_PREFIX)) {
			String inner = committish.substring(SEMVER_PREFIX.length());
			if (inner.isEmpty()) {
				return null;
			}

			return NpmVersionExpressionParser.parse(inner);
		}

		return NpmVersionExpressionParser.parse(committish);
	}

}
