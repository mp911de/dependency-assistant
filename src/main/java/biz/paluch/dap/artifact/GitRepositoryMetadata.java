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
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * GitHub repository metadata extracted from a Git remote URL.
 *
 * <p>The host is retained so release lookup can target GitHub Enterprise when a
 * project is not hosted on {@code github.com}. The owner and repository form
 * the framework's project identity for Git-backed dependency entries (GitHub
 * Actions {@code uses:} declarations and NPM {@code git+} URLs).
 *
 * <p>This type is the canonical, ecosystem-agnostic shape for parsed Git
 * coordinates, used both by the GitHub package (resolver and release source)
 * and by the NPM package (Git URL classifier). URL parsing is self-contained,
 * mirroring the Git and GitHub plugins' URL utilities, so this core type does
 * not link against optional plugins.
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
	 * <p>Both public GitHub and GitHub Enterprise remotes are supported, in scheme
	 * form ({@code https://host/owner/repo.git}, {@code ssh://git@host/owner/repo})
	 * and scp-like form ({@code git@host:owner/repo.git}). A {@literal null} result
	 * indicates that the URL is blank, malformed, or not a supported Git remote.
	 * @param url the remote URL to parse; can be {@literal null}.
	 * @return the parsed metadata, or {@literal null} if the URL is not supported.
	 */
	public static @Nullable GitRepositoryMetadata parseGitUrl(@Nullable String url) {

		if (url == null || url.isBlank()) {
			return null;
		}

		String host = parseHost(url.trim());
		if (host == null) {
			return null;
		}

		String path = removeProtocolPrefix(removeDotGitSuffix(url.trim()));
		int repositoryStart = path.lastIndexOf('/');
		if (repositoryStart == -1) {
			return null;
		}

		String beforeRepository = path.substring(0, repositoryStart);
		int ownerStart = Math.max(beforeRepository.lastIndexOf('/'), beforeRepository.lastIndexOf(':'));
		if (ownerStart == -1) {
			return null;
		}

		String owner = path.substring(ownerStart + 1, repositoryStart);
		String repository = path.substring(repositoryStart + 1);
		if (!GITHUB_NAME.matcher(owner).matches() || !GITHUB_NAME.matcher(repository).matches()) {
			return null;
		}

		return new GitRepositoryMetadata(host, owner, repository);
	}

	/**
	 * Extract the host by reading the URL as a URI, rewriting the scp-like
	 * {@code git@host:path} form into an ssh URI first.
	 */
	private static @Nullable String parseHost(String url) {

		String cleaned = url;
		while (cleaned.endsWith("/")) {
			cleaned = cleaned.substring(0, cleaned.length() - 1);
		}
		cleaned = removeDotGitSuffix(cleaned);

		try {
			URI uri = cleaned.contains("://") ? new URI(cleaned)
					: new URI("ssh://" + removeProtocolPrefix(cleaned).replace(":/", "/").replace(':', '/'));
			return uri.getHost();
		} catch (URISyntaxException e) {
			return null;
		}
	}

	private static String removeProtocolPrefix(String url) {

		int atIndex = url.indexOf('@');
		if (atIndex != -1) {
			return url.substring(atIndex + 1);
		}

		int schemeIndex = url.indexOf("://");
		return schemeIndex != -1 ? url.substring(schemeIndex + 3) : url;
	}

	private static String removeDotGitSuffix(String url) {

		String cleaned = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
		return cleaned.endsWith(".git") ? cleaned.substring(0, cleaned.length() - 4) : cleaned;
	}

	public GitArtifactId toArtifactId(ArtifactId originalArtifactId) {
		return GitArtifactId.of(host(), owner(), repository(), originalArtifactId);
	}

}
