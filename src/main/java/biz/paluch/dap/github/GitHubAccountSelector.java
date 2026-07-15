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

import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.ticket.TicketSystemInvalidationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import kotlin.Pair;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;
import kotlinx.coroutines.flow.StateFlow;
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;

/**
 * Selector for the GitHub account and hosted-repository sets for removals that
 * invalidate an already bound ticket system. Additions become visible when the
 * Upgrade Plan content is reconstructed.
 *
 * @author Mark Paluch
 */
@Service(Service.Level.PROJECT)
final class GitHubAccountSelector {

	private final Project project;

	private Set<GithubAccount> accounts;

	private Set<GHGitRepositoryMapping> repositories;

	GitHubAccountSelector(Project project, CoroutineScope coroutineScope) {

		this.project = project;
		GHAccountManager accountManager = ApplicationManager.getApplication().getService(GHAccountManager.class);
		GHHostedRepositoriesManager repositoriesManager = project.getService(GHHostedRepositoriesManager.class);
		this.accounts = accountManager.getAccountsState().getValue();
		this.repositories = repositoriesManager.getKnownRepositoriesState().getValue();

		observe(accountManager.getAccountsState(), this::accountsChanged, coroutineScope);
		observe(repositoriesManager.getKnownRepositoriesState(), this::repositoriesChanged, coroutineScope);
	}

	public @Nullable Selection select() {

		Pair<String, GithubAccount> remembered = GithubPullRequestsProjectUISettings.getInstance(project)
				.getSelectedUrlAndAccount();

		Selection selection = selectSaved(remembered);
		if (selection != null) {
			return selection;
		}

		return selectImplicit();
	}


	@Contract("null -> null")
	public @Nullable Selection selectSaved(
			@Nullable Pair<String, GithubAccount> remembered) {
		return remembered == null ? null : selectSaved(remembered.getFirst(), remembered.getSecond());
	}

	@Nullable
	Selection selectSaved(String url, GithubAccount account) {

		if (!accounts.contains(account)) {
			return null;
		}

		for (GHGitRepositoryMapping repository : repositories) {
			if (matches(repository, url, account)) {
				return new Selection(repository, account);
			}
		}

		return null;
	}

	@Nullable
	Selection selectImplicit() {
		Selection result = null;
		for (GHGitRepositoryMapping repository : repositories) {
			for (GithubAccount account : accounts) {
				if (!matches(repository, account)) {
					continue;
				}
				if (result != null) {
					return null;
				}
				result = new Selection(repository, account);
			}
		}

		return result;
	}

	private static boolean matches(GHGitRepositoryMapping repository, String url, GithubAccount account) {
		return repository.getRemote().getUrl().equals(url)
				&& matches(repository, account);
	}

	private static boolean matches(GHGitRepositoryMapping repository, GithubAccount account) {
		return repository.getRepository().getServerPath()
				.equals(account.getServer(), true);
	}

	private synchronized void accountsChanged(Set<GithubAccount> current) {

		boolean removed = !current.containsAll(accounts);
		accounts = current;
		if (removed) {
			invalidate();
		}
	}

	private synchronized void repositoriesChanged(Set<GHGitRepositoryMapping> current) {

		boolean removed = !current.containsAll(repositories);
		repositories = current;
		if (removed) {
			invalidate();
		}
	}

	private void invalidate() {
		project.getMessageBus().syncPublisher(TicketSystemInvalidationListener.TOPIC).ticketSystemInvalidated();
	}

	private static <T> void observe(StateFlow<T> state, Consumer<T> consumer, CoroutineScope coroutineScope) {

		Flow<T> observed = FlowKt.onEach(state, (value, continuation) -> {
			consumer.accept(value);
			return Unit.INSTANCE;
		});
		FlowKt.launchIn(observed, coroutineScope);
	}

	static class Selection {

		private final GHGitRepositoryMapping repository;

		private final GithubAccount account;

		Selection(GHGitRepositoryMapping repository, GithubAccount account) {
			this.repository = repository;
			this.account = account;
		}

		GHGitRepositoryMapping getRepository() {
			return repository;
		}

		GithubAccount getAccount() {
			return account;
		}

	}

}
