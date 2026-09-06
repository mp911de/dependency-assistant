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

import biz.paluch.dap.util.HttpClientUtil;
import org.jspecify.annotations.Nullable;

/**
 * HTTP Basic credentials selected by Maven server id.
 * <p>Equality uses only the server id. The repository-base list is retained and
 * must not change because it controls credential scope.
 *
 * @param settingsDeclaredRepositoryBases allowed repository bases from
 * settings. A {@literal null} list leaves URL binding to the Maven server id.
 * An empty list permits no repository.
 */
public record RepositoryCredentials(String id, String username, String password,
		@Nullable List<URI> settingsDeclaredRepositoryBases) {

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof RepositoryCredentials other) {
			return id.equals(other.id);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public String toString() {
		return "RepositoryCredentials: " + id();
	}

	/**
	 * Return whether credentials may be used for the repository URL.
	 * <p>Bound credentials require the same scheme, host, effective port, and a
	 * path below a declared base. Invalid URLs are rejected. Without a binding
	 * list, any URL is accepted for the matching server id.
	 */
	public boolean allowsRepositoryUrl(String repositoryUrl) {

		if (settingsDeclaredRepositoryBases == null) {
			return true;
		}
		if (settingsDeclaredRepositoryBases.isEmpty()) {
			return false;
		}

		URI repoUri;
		try {
			repoUri = normalizeBaseUri(repositoryUrl);
		} catch (IllegalArgumentException e) {
			return false;
		}

		String repoHost = repoUri.getHost();
		if (repoHost == null) {
			return false;
		}
		String repoScheme = repoUri.getScheme();
		int repoPort = HttpClientUtil.getEffectivePort(repoUri);

		String repoPath = pathOrSlash(repoUri);

		for (URI declared : settingsDeclaredRepositoryBases) {

			String declaredHost = declared.getHost();
			if (declaredHost == null) {
				continue;
			}
			if (!repoHost.equalsIgnoreCase(declaredHost)) {
				continue;
			}
			if (repoScheme == null || !repoScheme.equalsIgnoreCase(declared.getScheme())
					|| repoPort != HttpClientUtil.getEffectivePort(declared)) {
				continue;
			}

			String declaredPath = pathOrSlash(declared);
			if (repoPath.startsWith(declaredPath)) {
				return true;
			}
		}

		return false;
	}

	private static URI normalizeBaseUri(String url) {

		String trimmed = url.trim();
		if (!trimmed.endsWith("/")) {
			trimmed = trimmed + "/";
		}
		return URI.create(trimmed).normalize();
	}

	private static String pathOrSlash(URI uri) {

		String path = uri.getPath();
		if (path == null || path.isEmpty()) {
			return "/";
		}
		return path.endsWith("/") ? path : path + "/";
	}

}
