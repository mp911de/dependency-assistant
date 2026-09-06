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

package biz.paluch.dap.artifact;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Host and path of a remote repository URL, without hosting-platform semantics.
 * <p>Accepts scheme URLs and scp-style Git addresses. Revision fragments and
 * trailing {@code .git} suffixes are ignored. Query-based repository views are
 * unsupported.
 * <p>Hosting platforms interpret the path segments as repository coordinates.
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
	 * Parse a remote repository URL.
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
	 * Keep non-default HTTP ports so self-hosted instances remain reachable. SSH
	 * and Git ports do not apply to HTTPS browsing.
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
	 * Return the host, including a non-default HTTP(S) port if declared.
	 */
	public String host() {
		return host;
	}

	/**
	 * Return path segments, or an empty list for a bare host.
	 */
	public List<String> pathSegments() {
		return pathSegments;
	}

	private static URI toUri(String url) {

		if (url.contains("://")) {
			return URI.create(rewriteScpColon(url));
		}
		return URI.create("ssh://" + removeUserInfo(url).replace(":/", "/").replace(':', '/'));
	}

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
