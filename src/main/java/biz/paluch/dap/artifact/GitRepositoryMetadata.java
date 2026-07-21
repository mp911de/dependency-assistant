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

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Hosted Git repository coordinates: host, owner, and repository name.
 *
 * @param host the repository host name (e.g. {@code github.com} or a
 * self-hosted instance).
 * @param owner the owner path: a user or organization, or a GitLab group path
 * whose subgroup segments are preserved (e.g.
 * {@code gitlab-org/security-products/analyzers}).
 * @param repository the repository name.
 * @author Mark Paluch
 * @see RemoteUrl
 */
public record GitRepositoryMetadata(String host, String owner, String repository) {

	/**
	 * Create coordinates for a flat {@code owner/repo} host (GitHub, Bitbucket,
	 * Codeberg): the first path segment is the owner, the second the repository.
	 * Anything beyond the second segment is web-path or module-path debris and is
	 * ignored; a trailing {@code .git} on the repository segment is stripped.
	 * @param remoteUrl the parsed remote URL.
	 * @return the coordinates, or {@literal null} if the URL carries fewer than two
	 * path segments.
	 */
	public static @Nullable GitRepositoryMetadata flat(RemoteUrl remoteUrl) {

		List<String> segments = remoteUrl.pathSegments();
		if (segments.size() < 2) {
			return null;
		}

		return new GitRepositoryMetadata(remoteUrl.host(), segments.get(0), stripDotGit(segments.get(1)));
	}

	/**
	 * Strip a trailing {@code .git} left inside the path by scm-inheritance debris
	 * (e.g. {@code assertj/assertj.git/assertj-parent}).
	 */
	public static String stripDotGit(String segment) {
		return segment.endsWith(".git") ? segment.substring(0, segment.length() - 4) : segment;
	}

	/**
	 * Return the canonical cache and connection key of these coordinates.
	 * @return the key in {@code host/owner/repository} form; guaranteed to be not
	 * {@literal null}.
	 */
	public String key() {
		return host + "/" + owner + "/" + repository;
	}

	public GitArtifactId toArtifactId(ArtifactId originalArtifactId) {
		return GitArtifactId.of(host(), owner(), repository(), originalArtifactId);
	}

}
