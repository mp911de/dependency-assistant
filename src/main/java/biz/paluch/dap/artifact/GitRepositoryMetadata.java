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
import java.util.regex.Pattern;

import git4idea.remote.hosting.GitHostingUrlUtil;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jspecify.annotations.Nullable;

/**
 * GitHub repository metadata extracted from a Git remote URL.
 *
 * <p>
 * The host is retained so release lookup can target GitHub Enterprise when a
 * project is not hosted on {@code github.com}. The owner and repository form
 * the framework's project identity for Git-backed dependency entries (GitHub
 * Actions {@code uses:} declarations and NPM {@code git+} URLs).
 *
 * <p>
 * This type is the canonical, ecosystem-agnostic shape for parsed Git
 * coordinates, used both by the GitHub package (resolver and release source)
 * and by the NPM package (Git URL classifier).
 *
 * @param host the GitHub host name (e.g. {@code github.com} or a GitHub
 * Enterprise host).
 * @param owner the GitHub user or organization.
 * @param repository the repository name.
 * @author Mark Paluch
 */
public record GitRepositoryMetadata(String host, String owner, String repository) {

	private static final Pattern GITHUB_NAME = Pattern.compile("[A-Za-z0-9._-]+");

	/**
	 * Parse a Git remote URL into GitHub repository metadata.
	 * <p>
	 * Both public GitHub and GitHub Enterprise remotes are supported as long as
	 * the IntelliJ GitHub URL utilities can extract an owner and repository. A
	 * {@literal null} result indicates that the URL is blank, malformed, or not a
	 * supported GitHub remote.
	 * @param url the remote URL to parse; can be {@literal null}.
	 * @return the parsed metadata, or {@literal null} if the URL is not supported.
	 */
	public static @Nullable GitRepositoryMetadata parseGitUrl(@Nullable String url) {

		if (url == null || url.isBlank()) {
			return null;
		}

		GHRepositoryPath repoPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
		URI uri = GitHostingUrlUtil.getUriFromRemoteUrl(url);

		if (uri == null || repoPath == null) {
			return null;
		}

		String owner = repoPath.getOwner();
		String repository = repoPath.getRepository();
		if (!GITHUB_NAME.matcher(owner).matches() || !GITHUB_NAME.matcher(repository).matches()) {
			return null;
		}

		return new GitRepositoryMetadata(uri.getHost(), owner, repository);
	}

	public GitArtifactId toArtifactId(ArtifactId originalArtifactId) {
		return GitArtifactId.of(host(), owner(), repository(), originalArtifactId);
	}

}
