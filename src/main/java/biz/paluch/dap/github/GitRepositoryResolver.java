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

package biz.paluch.dap.github;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.remote.hosting.GitHostingUrlUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jspecify.annotations.Nullable;

/**
 * @author Mark Paluch
 */
class GitRepositoryResolver {

	private final Project project;

	private final GitRepositoryManager repositoryManager;

	public GitRepositoryResolver(Project project) {
		this.project = project;
		this.repositoryManager = GitRepositoryManager.getInstance(project);
	}

	/**
	 * Resolve the GitHub {@code owner/repository} that owns the given workflow file
	 * by matching it against configured Git remotes.
	 *
	 * <p>Resolution order:
	 * <ol>
	 * <li>The Git repository whose root contains the workflow file: parse its
	 * {@code origin} (or first) remote URL.</li>
	 * <li>Any other configured Git repository whose remote parses to a
	 * {@code owner/repository} pair.</li>
	 * </ol>
	 */
	public @Nullable GitRepositoryMetadata resolveOwnerAndRepository(
			VirtualFile anchor) {

		if (!anchor.isValid() || !anchor.exists() || !anchor.isDirectory()) {
			return null;
		}

		List<GitRepository> repositories = repositoryManager.getRepositories();
		if (repositories.isEmpty()) {
			return null;
		}

		for (GitRepository repository : repositories) {

			VirtualFile root = repository.getRoot();

			if (!root.isValid() || !root.exists() || !root.isDirectory()) {
				continue;
			}

			if (VfsUtilCore.isAncestor(root, anchor, true)) {
				GitRepositoryMetadata parsed = preferredRemoteCoordinates(repository);
				if (parsed != null) {
					return parsed;
				}
			}
		}

		for (GitRepository repository : repositories) {
			GitRepositoryMetadata parsed = preferredRemoteCoordinates(repository);
			if (parsed != null) {
				return parsed;
			}
		}

		return null;
	}

	private static @Nullable GitRepositoryMetadata preferredRemoteCoordinates(
			GitRepository repository) {

		Collection<GitRemote> remotes = repository.getRemotes();
		if (remotes.isEmpty()) {
			return null;
		}

		GitRemote origin = null;
		GitRemote first = null;
		for (GitRemote remote : remotes) {
			if (first == null) {
				first = remote;
			}
			if (GitRemote.ORIGIN.equals(remote.getName())) {
				origin = remote;
				break;
			}
		}

		GitRemote preferred = origin != null ? origin : first;
		return preferred == null ? null : parseGitUrl(preferred.getFirstUrl());
	}

	/**
	 * Parse a Git remote URL into a host plus owner/repository triple.
	 *
	 * @return the parsed components, or {@code null} when the URL is not a
	 * supported GitHub or GitHub-Enterprise remote.
	 */
	public static @Nullable GitRepositoryMetadata parseGitUrl(@Nullable String url) {

		if (url == null || url.isBlank()) {
			return null;
		}

		GHRepositoryPath repoPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
		URI uri = GitHostingUrlUtil.getUriFromRemoteUrl(url);

		if (uri != null && repoPath != null) {
			return new GitRepositoryMetadata(uri.getHost(), repoPath.getOwner(), repoPath.getRepository());
		}

		return null;
	}

	/**
	 * Triple of host plus {@code owner/repository} extracted from a Git remote URL.
	 *
	 * @param host the GitHub host name (e.g. {@code github.com} or a GitHub
	 * Enterprise host).
	 * @param owner the GitHub user or organization.
	 * @param repository the repository name.
	 */
	record GitRepositoryMetadata(String host, String owner, String repository) {


	}

}
