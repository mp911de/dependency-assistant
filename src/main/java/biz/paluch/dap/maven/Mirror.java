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

package biz.paluch.dap.maven;

import java.net.URI;

import org.jspecify.annotations.Nullable;

/**
 * A {@code settings.xml} mirror that redirects repositories matching its
 * {@code mirrorOf} pattern to a single URL.
 *
 * <p>Matching follows Maven's own {@code DefaultMirrorSelector} semantics.
 *
 * @param id the mirror id, used to look up the matching {@code <server>}
 * credentials; never {@literal null} or blank.
 * @param url the mirror URL that replaces the original repository URL; never
 * {@literal null} or blank.
 * @param mirrorOf the {@code mirrorOf} pattern declaring which repositories
 * this mirror replaces; never {@literal null} or blank.
 * @author Mark Paluch
 */
record Mirror(String id, String url, String mirrorOf) {

	/**
	 * Test whether the given repository is mirrored by this mirror.
	 *
	 * @param repositoryId the original repository id.
	 * @param repositoryUrl the original repository URL.
	 * @return {@literal true} if this mirror replaces the repository;
	 * {@literal false} otherwise.
	 */
	boolean matches(String repositoryId, String repositoryUrl) {

		if ("*".equals(mirrorOf) || mirrorOf.equals(repositoryId)) {
			return true;
		}

		boolean matched = false;
		for (String token : mirrorOf.split(",")) {
			String pattern = token.trim();

			if (pattern.length() > 1 && pattern.startsWith("!")) {
				if (pattern.substring(1).equals(repositoryId)) {
					return false;
				}
			} else if (pattern.equals(repositoryId)) {
				return true;
			} else if ("external:http:*".equals(pattern)) {
				matched |= isExternalHttpRepo(repositoryUrl);
			} else if ("external:*".equals(pattern)) {
				matched |= isExternalRepo(repositoryUrl);
			} else if ("*".equals(pattern)) {
				matched = true;
			}
		}

		return matched;
	}

	private static boolean isExternalRepo(String repositoryUrl) {

		URI uri = parse(repositoryUrl);
		if (uri == null) {
			return false;
		}
		return !(isLocal(uri.getHost()) || "file".equals(uri.getScheme()));
	}

	private static boolean isExternalHttpRepo(String repositoryUrl) {

		URI uri = parse(repositoryUrl);
		if (uri == null) {
			return false;
		}
		String scheme = uri.getScheme();
		boolean http = "http".equalsIgnoreCase(scheme) || "dav".equalsIgnoreCase(scheme)
				|| "dav:http".equalsIgnoreCase(scheme) || "dav+http".equalsIgnoreCase(scheme);
		return http && !isLocal(uri.getHost());
	}

	private static boolean isLocal(@Nullable String host) {
		return "localhost".equals(host) || "127.0.0.1".equals(host);
	}

	private static @Nullable URI parse(String url) {
		try {
			return URI.create(url);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

}
