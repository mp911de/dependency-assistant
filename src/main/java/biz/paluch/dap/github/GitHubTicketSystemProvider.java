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

import biz.paluch.dap.github.GithubApiRequestExecutorFactory.ExecutorResult;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.ticket.TicketSystemProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jspecify.annotations.Nullable;

/**
 * {@link TicketSystemProvider} contributing GitHub issue tracking.
 *
 * <p>A project is supported when the bundled GitHub plugin supplies one
 * unambiguous repository/account selection and can create an authenticated API
 * executor for it. A remembered Pull Requests selection takes precedence;
 * otherwise exactly one compatible repository/account pair is required.
 *
 * @author Mark Paluch
 */
public class GitHubTicketSystemProvider implements TicketSystemProvider {

	@Override
	public boolean supports(Project project) {
		return resolveSelection(project) != null;
	}

	@Override
	public TicketSystem create(Project project) {

		GitHubAccountSelector.@Nullable Selection selection = resolveSelection(project);
		if (selection == null) {
			throw new IllegalStateException(
					"Project %s has no usable GitHub ticket system".formatted(project.getName()));
		}
		ExecutorResult result = GithubApiRequestExecutorFactory.getInstance(project)
				.getExecutor(selection.getRepository(), selection.getAccount());
		if (!result.hasExecutor()) {
			throw new IllegalStateException(
					"Project %s has no usable GitHub ticket system".formatted(project.getName()));
		}

		GHGitRepositoryMapping mapping = result.getDecision().getRepository();
		if (mapping == null) {
			throw new IllegalStateException(
					"Project %s has no selected GitHub repository".formatted(project.getName()));
		}

		GithubApiRequestExecutor executor = result.getRequiredExecutor();
		GitHubTicketRepository repository = new GitHubTicketRepository(mapping.getRepository(), executor,
				GitHubTicketCache.getInstance(project));
		return new GitHubTicketSystem(repository);
	}


	private static GitHubAccountSelector.@Nullable Selection resolveSelection(Project project) {

		GitHubAccountSelector selector = project.getService(GitHubAccountSelector.class);
		return selector.select();
	}

}
