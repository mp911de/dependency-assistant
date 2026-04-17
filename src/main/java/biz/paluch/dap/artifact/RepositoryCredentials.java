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

import org.jspecify.annotations.Nullable;

/**
 * HTTP Basic-auth credentials for a Maven repository server entry.
 *
 * @param username server username
 * @param password plain-text password
 * @param settingsDeclaredRepositoryBases when not {@literal null}, credentials
 * are only sent to repository URLs whose host and path match one of these bases
 * (from {@code settings.xml} mirrors and profile repositories). When
 * {@literal null}, no URL binding was derived and the legacy behaviour applies
 * (any POM repository with the same {@code <id>}).
 */
public record RepositoryCredentials(String id, String username, String password,
		@Nullable List<URI> settingsDeclaredRepositoryBases) {

	@Override
	public String toString() {
		return "RepositoryCredentials: " + id();
	}

	/**
	 * @param repositoryUrl the repository URL from the effective POM (typically
	 * with a trailing slash).
	 * @return {@literal true} if credentials may be sent to that URL.
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

		String repoPath = pathOrSlash(repoUri);

		for (URI declared : settingsDeclaredRepositoryBases) {

			String declaredHost = declared.getHost();
			if (declaredHost == null) {
				continue;
			}
			if (!repoHost.equalsIgnoreCase(declaredHost)) {
				continue;
			}

			int repoPort = repoUri.getPort();
			int declaredPort = declared.getPort();
			if (declaredPort != -1 && repoPort != -1 && repoPort != declaredPort) {
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
