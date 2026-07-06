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

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.ticket.TicketSystemProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

/**
 * {@link TicketSystemProvider} contributing GitHub issue tracking.
 *
 * <p>A project is supported when a configured Git remote parses as a GitHub or
 * GitHub Enterprise repository and an account for that host is registered with
 * the bundled GitHub plugin. Both checks inspect in-memory state only;
 * credential access is deferred to the first repository operation.
 *
 * @author Mark Paluch
 */
public class GitHubTicketSystemProvider implements TicketSystemProvider {

	@Override
	public boolean supports(Project project) {
		return resolveKnownRepository(project) != null || resolveLegacyCoordinates(project) != null;
	}

	@Override
	public TicketSystem create(Project project) {

		GHRepositoryCoordinates repository = resolveKnownRepository(project);
		if (repository != null) {
			return new GitHubTicketSystem(project, repository);
		}

		GitRepositoryMetadata coordinates = resolveLegacyCoordinates(project);
		if (coordinates == null) {
			throw new IllegalStateException(
					"Project %s has no usable GitHub ticket system".formatted(project.getName()));
		}

		return new GitHubTicketSystem(project, coordinates);
	}

	private static @Nullable GHRepositoryCoordinates resolveKnownRepository(Project project) {

		GHHostedRepositoriesManager repositoriesManager = project.getService(GHHostedRepositoriesManager.class);
		if (repositoriesManager == null) {
			return null;
		}

		for (GHGitRepositoryMapping mapping : repositoriesManager.getKnownRepositoriesState().getValue()) {
			GHRepositoryCoordinates repository = mapping.getRepository();
			if (resolveAccount(project, repository.getServerPath()) != null) {
				return repository;
			}
		}

		return null;
	}

	private static @Nullable GitRepositoryMetadata resolveLegacyCoordinates(Project project) {

		GitRepositoryMetadata coordinates = new GitRepositoryResolver(project).resolveProjectRepository();
		if (coordinates == null || resolveAccount(project, GithubServerPath.from(coordinates.host())) == null) {
			return null;
		}

		return coordinates;
	}

	private static @Nullable GithubAccount resolveAccount(Project project, GithubServerPath server) {

		GithubAccount selected = GHAccountsUtil.getSingleOrDefaultAccount(project);
		if (selected != null && selected.getServer().equals(server, true)) {
			return selected;
		}

		for (GithubAccount account : GHAccountsUtil.getAccounts()) {
			if (account.getServer().equals(server, true)) {
				return account;
			}
		}

		return null;
	}

}
