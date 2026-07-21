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

package biz.paluch.dap.artifact;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Syntactic view of a remote repository URL: the host and the slash-separated
 * path segments, without any forge semantics.
 *
 * <p>Both scheme forms ({@code https://host/a/b}, {@code ssh://git@host/a/b},
 * {@code git+ssh://git@host:a/b}) and the scp-like form
 * ({@code git@host:a/b.git}) parse; userinfo components are tolerated, a
 * non-default port of an http(s) URL stays part of {@link #host()} while ssh
 * and git transport ports are dropped, {@code #commit-ish} fragments are
 * ignored, and trailing slashes and the {@code .git} suffix are stripped. URLs
 * carrying a query string (e.g. gitweb {@code ?p=repo.git} views) are rejected:
 * a query never denotes a forge-style repository URL.
 *
 * <p>The segments are uninterpreted: which of them form owner and repository
 * coordinates is a per-platform decision made by the {@code Platform}
 * implementations, never by this type.
 *
 * @author Mark Paluch
 * @see GitRepositoryMetadata
 */
public class RemoteUrl {

	private final String host;

	private final List<String> pathSegments;

	private RemoteUrl(String host, List<String> pathSegments) {
		this.host = host;
		this.pathSegments = pathSegments;
	}

	/**
	 * Parse a remote URL into its syntactic form.
	 * @param url the remote URL to parse.
	 * @return the parsed URL
	 * @throws IllegalArgumentException if the value is blank, malformed, carries a
	 * query string, or has no host.
	 */
	public static RemoteUrl parse(String url) {

		String cleaned = url.trim();
		int fragment = cleaned.indexOf('#');
		if (fragment != -1) {
			cleaned = cleaned.substring(0, fragment);
		}

		URI uri = toUri(removeDotGitSuffix(cleaned));
		String host = uri.getHost();
		String rawPath = uri.getPath();
		if (host == null || rawPath == null || uri.getRawQuery() != null) {
			throw new IllegalArgumentException("Invalid url: " + url);
		}

		String path = trimSlashes(rawPath);
		return new RemoteUrl(httpAuthority(uri), path.isEmpty() ? List.of() : List.of(path.split("/")));
	}

	/**
	 * Return the authority to address the remote over https: the host, retaining an
	 * explicit non-default port of http(s) URLs so self-hosted instances on a
	 * custom port stay reachable. Ports of ssh, git, and scp-like URLs are
	 * transport ports and do not carry over to https addressing.
	 */
	private static String httpAuthority(URI uri) {

		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		boolean secure = scheme.equals("https") || scheme.endsWith("+https");
		boolean plain = scheme.equals("http") || scheme.endsWith("+http");
		int port = uri.getPort();
		if ((!secure && !plain) || port == -1 || port == (plain ? 80 : 443)) {
			return uri.getHost();
		}
		return uri.getHost() + ":" + port;
	}

	/**
	 * Return the host component, including an explicit non-default port for http(s)
	 * URLs.
	 * @return the host.
	 */
	public String host() {
		return host;
	}

	/**
	 * Return the slash-separated path segments without leading or trailing slashes.
	 * @return the path segments, empty for a bare host URL; guaranteed to be not
	 * {@literal null}.
	 */
	public List<String> pathSegments() {
		return pathSegments;
	}

	/**
	 * Read the URL as a URI, rewriting the scp-like {@code git@host:path} form and
	 * the scp-colon-after-scheme form ({@code git+ssh://git@host:path}) into
	 * slash-separated URIs first.
	 */
	private static URI toUri(String url) {

		if (url.contains("://")) {
			return URI.create(rewriteScpColon(url));
		}
		return URI.create("ssh://" + removeUserInfo(url).replace(":/", "/").replace(':', '/'));
	}

	/**
	 * Replace a colon that separates host and path in a scheme URL with a slash,
	 * leaving numeric port declarations intact.
	 */
	private static String rewriteScpColon(String url) {

		int authorityStart = url.indexOf("://") + 3;
		int pathStart = url.indexOf('/', authorityStart);
		String authority = pathStart == -1 ? url.substring(authorityStart) : url.substring(authorityStart, pathStart);

		int colon = authority.indexOf(':', authority.lastIndexOf('@') + 1);
		if (colon == -1) {
			return url;
		}

		String afterColon = authority.substring(colon + 1);
		if (!afterColon.isEmpty() && afterColon.chars().allMatch(Character::isDigit)) {
			return url;
		}

		int urlColon = authorityStart + colon;
		return url.substring(0, urlColon) + '/' + url.substring(urlColon + 1);
	}

	private static String removeUserInfo(String url) {

		int atIndex = url.indexOf('@');
		return atIndex != -1 ? url.substring(atIndex + 1) : url;
	}

	private static String removeDotGitSuffix(String url) {

		String cleaned = trimSlashes(url);
		return cleaned.endsWith(".git") ? cleaned.substring(0, cleaned.length() - 4) : cleaned;
	}

	private static String trimSlashes(String path) {

		while (path.startsWith("/")) {
			path = path.substring(1);
		}
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}

	@Override
	public boolean equals(Object o) {

		if (!(o instanceof RemoteUrl that)) {
			return false;
		}
		return host.equals(that.host) && pathSegments.equals(that.pathSegments);
	}

	@Override
	public int hashCode() {
		return Objects.hash(host, pathSegments);
	}

	@Override
	public String toString() {
		return host + "/" + String.join("/", pathSegments);
	}

}
