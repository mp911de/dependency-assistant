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

import java.util.Collection;
import java.util.List;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.state.ProjectId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jspecify.annotations.Nullable;

/**
 * Resolver for the GitHub repository metadata associated with GitHub Actions
 * files in an IntelliJ project.
 *
 * <p>
 * GitHub Actions support needs repository coordinates for two separate
 * purposes: a stable {@link ProjectId} for persisted dependency state and the
 * GitHub host from which action releases should be resolved. This resolver
 * derives both from configured Git remotes instead of from the YAML source
 * itself, because action files do not identify the repository that owns them.
 *
 * <p>
 * Resolution is intentionally best-effort. If the anchor can be associated
 * with a Git root, that root's preferred remote provides the metadata.
 * Otherwise any configured GitHub remote may be used as a project-level
 * fallback so that GitHub Actions assistance remains available in partially
 * indexed or non-standard projects.
 *
 * @author Mark Paluch
 */
class GitRepositoryResolver {

	private final Project project;

	private final GitRepositoryManager repositoryManager;

	GitRepositoryResolver(Project project) {
		this.project = project;
		this.repositoryManager = GitRepositoryManager.getInstance(project);
	}

	/**
	 * Resolve repository metadata for the given anchor.
	 * <p>
	 * The anchor may be a GitHub Actions file or directory in the project VFS. A
	 * {@literal null} result means no configured Git remote could be interpreted as a
	 * GitHub or GitHub Enterprise repository; callers should then fall back to
	 * host-agnostic GitHub support.
	 */
	@Nullable
	GitRepositoryMetadata resolveOwnerAndRepository(VirtualFile anchor) {

		if (!anchor.isValid() || !anchor.exists()) {
			return null;
		}

		VirtualFile anchorDirectory = anchor.isDirectory() ? anchor : anchor.getParent();
		if (anchorDirectory == null || !anchorDirectory.isValid() || !anchorDirectory.exists()) {
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

			if (VfsUtilCore.isAncestor(root, anchorDirectory, true)) {
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
		return preferred == null ? null : GitRepositoryMetadata.parseGitUrl(preferred.getFirstUrl());
	}

}
