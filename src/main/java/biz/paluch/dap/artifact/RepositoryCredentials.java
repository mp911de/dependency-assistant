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
 * @param allowedOrigins repository URLs from settings whose origins (scheme,
 * host, port) the credentials are bound to. An empty list leaves URL binding to
 * the Maven server id.
 */
public record RepositoryCredentials(String id, String username, String password,
		@Nullable List<URI> allowedOrigins) {

	public RepositoryCredentials(String id, String username, String password) {
		this(id, username, password, List.of());
	}

	public RepositoryCredentials(String id, String username, String password, URI allowedOrigin) {
		this(id, username, password, List.of(allowedOrigin));
	}

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
	 * <p>Bound credentials require the same scheme, host, and effective port.
	 * Invalid URLs are rejected. Without a binding list, any URL is accepted for
	 * the matching server id.
	 *
	 * @param repositoryUrl the effective repository URL.
	 * @return {@literal true} if the credentials may be sent to the repository.
	 */
	public boolean allowsRepositoryUrl(String repositoryUrl) {

		if (allowedOrigins == null || allowedOrigins.isEmpty()) {
			return true;
		}

		try {
			return allowsRepositoryUrl(URI.create(repositoryUrl.trim()));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Return whether credentials may be used for the repository URL.
	 * <p>Bound credentials require the same scheme, host, and effective port.
	 * Invalid URLs are rejected. Without a binding list, any URL is accepted for
	 * the matching server id.
	 *
	 * @return {@literal true} if the credentials may be sent to the repository.
	 */
	public boolean allowsRepositoryUrl(URI repository) {

		if (allowedOrigins == null || allowedOrigins.isEmpty()) {
			return true;
		}

		String scheme = repository.getScheme();
		if (scheme == null) {
			return false;
		}

		for (URI declared : allowedOrigins) {
			if (scheme.equalsIgnoreCase(declared.getScheme())
					&& HttpClientUtil.hasSameHostAndPort(repository, declared)) {
				return true;
			}
		}

		return false;
	}

}
