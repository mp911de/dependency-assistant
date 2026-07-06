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
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.ticket.TicketRepository;
import biz.paluch.dap.ticket.TicketSystem;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubServerPath;

/**
 * {@link TicketSystem} bound to the GitHub repository resolved from the
 * project's Git remotes.
 *
 * @author Mark Paluch
 * @see GitHubTicketSystemProvider
 */
class GitHubTicketSystem implements TicketSystem {

	private final GitHubTicketRepository repository;

	GitHubTicketSystem(Project project, GitRepositoryMetadata coordinates) {
		this(new GitHubTicketRepository(coordinates, createExecutor(project, toRepositoryCoordinates(coordinates)),
				GitHubTicketCache.getInstance(project)));
	}

	GitHubTicketSystem(Project project, GHRepositoryCoordinates repository) {
		this(new GitHubTicketRepository(repository, createExecutor(project, repository),
				GitHubTicketCache.getInstance(project)));
	}

	GitHubTicketSystem(GitHubTicketRepository repository) {
		this.repository = repository;
	}

	@Override
	public TicketRepository getRepository() {
		return repository;
	}

	@Override
	public String getDisplayReference(TicketKey key) {
		return "#" + key;
	}

	@Override
	public String getCloseReference(TicketKey key) {
		return "Closes " + getDisplayReference(key);
	}

	@Override
	public String toString() {
		return repository.toString();
	}

	private static GithubApiRequestExecutor createExecutor(Project project, GHRepositoryCoordinates repository) {

		GithubApiRequestExecutorFactory.ExecutorResult result = GithubApiRequestExecutorFactory.getInstance(project)
				.getExecutor(repository);
		return result.getRequiredExecutor();
	}

	private static GHRepositoryCoordinates toRepositoryCoordinates(GitRepositoryMetadata coordinates) {
		return new GHRepositoryCoordinates(GithubServerPath.from(coordinates.host()),
				new GHRepositoryPath(coordinates.owner(), coordinates.repository()));
	}

}
