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

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Hosted Git repository coordinates.
 *
 * @author Mark Paluch
 * @param owner a user, organization, or nested GitLab group path.
 * @see RemoteUrl
 */
public record GitRepositoryMetadata(String host, String owner, String repository) {

	/**
	 * Extract flat owner/repository coordinates, ignoring later web or module
	 * paths.
	 * <p>Nested GitLab groups require platform-specific parsing.
	 * @return {@literal null} if fewer than two path segments exist.
	 */
	public static @Nullable GitRepositoryMetadata flat(RemoteUrl remoteUrl) {

		List<String> segments = remoteUrl.pathSegments();
		if (segments.size() < 2) {
			return null;
		}

		return new GitRepositoryMetadata(remoteUrl.host(), segments.get(0), stripDotGit(segments.get(1)));
	}

	/**
	 * Strip a trailing {@code .git} suffix from a repository segment.
	 * <p>SCM inheritance can leave it inside a path, as in
	 * {@code assertj/assertj.git/assertj-parent}.
	 */
	public static String stripDotGit(String segment) {
		return segment.endsWith(".git") ? segment.substring(0, segment.length() - 4) : segment;
	}

	/**
	 * Return the cache key in {@code host/owner/repository} form.
	 */
	public String key() {
		return host + "/" + owner + "/" + repository;
	}

	/**
	 * Attach these repository coordinates to the declared artifact identity for
	 * release lookup.
	 */
	public GitArtifactId toArtifactId(ArtifactId originalArtifactId) {
		return GitArtifactId.of(host(), owner(), repository(), originalArtifactId);
	}

}
