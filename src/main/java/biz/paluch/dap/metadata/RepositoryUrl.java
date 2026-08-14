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

import java.util.Objects;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.RemoteUrl;
import org.jspecify.annotations.Nullable;

/**
 * Normalized repository URL peeled from a declared SCM value.
 *
 * <p>Declared repository values arrive in ecosystem-specific wrappings. This
 * value type removes the wrapping and yields the delegate URL plus the declared
 * {@link RepositoryType}:
 *
 * <ul>
 * <li>Maven {@code scm:<provider>:} prefixes with colon or pipe ({@code |})
 * delimiters, including {@code [fetch=]}/{@code [push=]} dual-URL declarations
 * where the fetch URL is taken.</li>
 * <li>npm Git URL forms: {@code git+} scheme prefix, {@code #commit-ish} and
 * {@code #semver:} fragments, and shorthand expansion ({@code owner/repo},
 * {@code github:}, {@code gitlab:}, {@code bitbucket:}, {@code gist:}).</li>
 * <li>Trailing slashes, and trailing {@code .git} suffixes when the URL carries
 * no query string; a {@code .git} tail inside a query (e.g. gitweb
 * {@code ?p=repo.git}) is preserved intact.</li>
 * </ul>
 *
 * @author Mark Paluch
 * @see RepositoryType
 */
public class RepositoryUrl {

	private static final String SCM_PREFIX = "scm:";

	private static final String FETCH_MARKER = "[fetch=]";

	private static final String PUSH_MARKER = "[push=]";

	private static final String GIT_PLUS_PREFIX = "git+";

	/**
	 * npm {@code owner/repo} shorthand expands to github.com, so both segments
	 * follow GitHub naming rules: owners are alphanumerics with single inner
	 * hyphens up to 39 characters, repository names exclude the reserved {@code .}
	 * and {@code ..}.
	 */
	private static final Pattern OWNER_REPO_SHORTHAND = Pattern
			.compile("[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}/(?!\\.\\.?$)[A-Za-z0-9._-]{1,100}");

	private final RepositoryType type;

	private final String url;

	private final RemoteUrl remoteUrl;

	private RepositoryUrl(RepositoryType type, String url) {
		this.type = type;
		this.url = url;
		this.remoteUrl = RemoteUrl.parse(url);
	}

	/**
	 * Parse a declared repository value into its normalized form.
	 * <p>A {@code RepositoryUrl} exists only for values whose delegate URL parses
	 * as a {@link RemoteUrl}: values that are not URL-shaped or carry a query
	 * string (e.g. gitweb {@code ?p=repo.git} views) yield {@literal null}.
	 * @param declared the raw value from a Maven {@code scm} element or an npm
	 * {@code repository} field; can be {@literal null}.
	 * @return the normalized repository URL, or {@literal null} if the value is
	 * {@literal null}, blank, carries no delegate URL after unwrapping, or the
	 * delegate URL is not a remote URL.
	 */
	public static @Nullable RepositoryUrl parse(@Nullable String declared) {

		if (declared == null || declared.isBlank()) {
			return null;
		}

		String candidate = declared.trim();
		RepositoryType type = RepositoryType.UNKNOWN;
		boolean scmWrapped = candidate.regionMatches(true, 0, SCM_PREFIX, 0, SCM_PREFIX.length());

		if (scmWrapped) {

			String wrapped = candidate.substring(SCM_PREFIX.length());
			int delimiter = providerDelimiter(wrapped);
			if (delimiter == -1) {
				return null;
			}

			type = RepositoryType.of(wrapped.substring(0, delimiter));
			candidate = fetchUrl(wrapped.substring(delimiter + 1)).trim();
		}

		int fragment = candidate.indexOf('#');
		if (fragment != -1) {
			candidate = candidate.substring(0, fragment).trim();
		}

		if (!scmWrapped) {

			String expanded = expandShorthand(candidate);
			if (expanded != null) {
				candidate = expanded;
				type = RepositoryType.GIT;
			}
		}

		if (candidate.startsWith(GIT_PLUS_PREFIX)) {
			candidate = candidate.substring(GIT_PLUS_PREFIX.length());
			type = type == RepositoryType.UNKNOWN ? RepositoryType.GIT : type;
		}

		if (candidate.startsWith("git://") && type == RepositoryType.UNKNOWN) {
			type = RepositoryType.GIT;
		}

		while (candidate.endsWith("/")) {
			candidate = candidate.substring(0, candidate.length() - 1);
		}
		if (candidate.endsWith(".git") && candidate.indexOf('?') == -1) {
			candidate = candidate.substring(0, candidate.length() - 4);
		}

		if (candidate.isBlank()) {
			return null;
		}

		try {
			return new RepositoryUrl(type, candidate);
		} catch (IllegalArgumentException e) {
			// the delegate is not a remote URL
			return null;
		}
	}

	/**
	 * Locate the delimiter between the SCM provider and the delegate URL. Per the
	 * Maven SCM URL format, the delimiter is a colon, or a pipe when the delegate
	 * URL itself contains colons; whichever occurs first separates the provider.
	 */
	private static int providerDelimiter(String wrapped) {

		int colon = wrapped.indexOf(':');
		int pipe = wrapped.indexOf('|');
		if (pipe != -1 && (colon == -1 || pipe < colon)) {
			return pipe;
		}
		return colon;
	}

	/**
	 * Select the fetch URL from a {@code [fetch=]}/{@code [push=]} dual-URL
	 * declaration, tolerating a lone push marker.
	 */
	private static String fetchUrl(String delegate) {

		int fetch = delegate.indexOf(FETCH_MARKER);
		if (fetch != -1) {

			String afterFetch = delegate.substring(fetch + FETCH_MARKER.length());
			int push = afterFetch.indexOf(PUSH_MARKER);
			return push == -1 ? afterFetch : afterFetch.substring(0, push);
		}

		int push = delegate.indexOf(PUSH_MARKER);
		if (push == -1) {
			return delegate;
		}

		String beforePush = delegate.substring(0, push);
		return beforePush.isEmpty() ? delegate.substring(push + PUSH_MARKER.length()) : beforePush;
	}

	/**
	 * Expand npm shorthand notation into a browsable https URL, or return
	 * {@literal null} when the value is not a shorthand.
	 */
	private static @Nullable String expandShorthand(String candidate) {

		if (candidate.startsWith("github:")) {
			return "https://github.com/" + candidate.substring("github:".length());
		}
		if (candidate.startsWith("gitlab:")) {
			return "https://gitlab.com/" + candidate.substring("gitlab:".length());
		}
		if (candidate.startsWith("bitbucket:")) {
			return "https://bitbucket.org/" + candidate.substring("bitbucket:".length());
		}
		if (candidate.startsWith("gist:")) {
			return "https://gist.github.com/" + candidate.substring("gist:".length());
		}

		return OWNER_REPO_SHORTHAND.matcher(candidate).matches() ? "https://github.com/" + candidate : null;
	}

	/**
	 * Return the declared or inferred version-control system.
	 * @return the repository type; guaranteed to be not {@literal null}.
	 */
	public RepositoryType getType() {
		return type;
	}

	/**
	 * Return the unwrapped delegate URL.
	 * @return the delegate URL; guaranteed to be non-blank.
	 */
	public String getUrl() {
		return url;
	}

	public RemoteUrl getRemote() {
		return remoteUrl;
	}

	@Override
	public boolean equals(Object o) {

		if (!(o instanceof RepositoryUrl that)) {
			return false;
		}
		return type == that.type && url.equals(that.url);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, url);
	}

	@Override
	public String toString() {
		return "RepositoryUrl[type=%s, url=%s]".formatted(type, url);
	}

}
