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

import biz.paluch.dap.ProjectId;
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
 * Resolver for the GitHub repository metadata associated with workflow files in
 * an IntelliJ project.
 *
 * <p>GitHub workflow support needs repository coordinates for two separate
 * purposes: a stable {@link ProjectId} for persisted dependency state and the
 * GitHub host from which action releases should be resolved. This resolver
 * derives both from configured Git remotes instead of from the workflow YAML
 * itself, because workflow files do not identify the repository that owns them.
 *
 * <p>Resolution is intentionally best-effort. If the anchor can be associated
 * with a Git root, that root's preferred remote provides the metadata.
 * Otherwise any configured GitHub remote may be used as a project-level
 * fallback so that workflow assistance remains available in partially indexed
 * or non-standard projects.
 *
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
	 * Resolve repository metadata for the given anchor.
	 * <p>The anchor is expected to be an existing directory in the project VFS. A
	 * {@code null} result means no configured Git remote could be interpreted as a
	 * GitHub or GitHub Enterprise repository; callers should then fall back to
	 * host-agnostic GitHub support.
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
	 * Parse a Git remote URL into GitHub repository metadata.
	 * <p>Both public GitHub and GitHub Enterprise remotes are supported as long as
	 * the IntelliJ GitHub URL utilities can extract an owner and repository. A
	 * {@code null} result indicates that the URL is blank, malformed, or not a
	 * supported GitHub remote.
	 *
	 * @param url the remote URL to parse
	 * @return the parsed metadata, or {@code null} if the URL is not supported
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
	 * GitHub repository metadata extracted from a Git remote URL.
	 *
	 * <p>The host is retained so release lookup can target GitHub Enterprise when a
	 * project is not hosted on {@code github.com}. The owner and repository form
	 * the framework's project identity for workflow dependency state.
	 *
	 * @param host the GitHub host name (e.g. {@code github.com} or a GitHub
	 * Enterprise host).
	 * @param owner the GitHub user or organization.
	 * @param repository the repository name.
	 */
	record GitRepositoryMetadata(String host, String owner, String repository) {

		/**
		 * Return the project identity for the given workflow file.
		 * <p>The workflow file path is part of the identity because a repository may
		 * contain several workflow files with independent dependency declarations.
		 * @param buildFile the workflow file path
		 * @return the project identity for the workflow
		 */
		public ProjectId toProjectId(String buildFile) {
			return ProjectId.of(owner(), repository(), buildFile);
		}

	}

}
