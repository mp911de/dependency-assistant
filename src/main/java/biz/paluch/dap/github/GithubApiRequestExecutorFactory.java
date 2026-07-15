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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder;
import org.jetbrains.plugins.github.exceptions.GithubParseException;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Project-scoped factory for {@link GithubApiRequestExecutor} instances.
 *
 * <p>This class mirrors the IntelliJ pull-request tool window shape without
 * owning UI. It can create an executor when the repository/account choice is
 * already clear, or return enough selector input for a future UI adapter to
 * perform the repository and account selection.
 *
 * @author Mark Paluch
 */
class GithubApiRequestExecutorFactory {

	private static final Logger LOG = Logger.getInstance(GithubApiRequestExecutorFactory.class);

	private final Project project;

	private final GHHostedRepositoriesManager repositoriesManager;

	private final GHAccountManager accountManager;

	private final GithubProjectDefaultAccountHolder defaultAccountHolder;

	private volatile @Nullable Decision lastDecision;

	/**
	 * Production constructor invoked by the IntelliJ service container.
	 */
	GithubApiRequestExecutorFactory(Project project) {
		this.project = project;
		this.repositoriesManager = project.getService(GHHostedRepositoriesManager.class);
		this.accountManager = ApplicationManager.getApplication().getService(GHAccountManager.class);
		this.defaultAccountHolder = project.getService(GithubProjectDefaultAccountHolder.class);
	}

	static GithubApiRequestExecutorFactory getInstance(Project project) {
		return project.getService(GithubApiRequestExecutorFactory.class);
	}

	/**
	 * Create a best-effort executor for the first known GitHub repository.
	 * @return the executor resolution result.
	 */
	ExecutorResult getExecutor() {

		List<GHGitRepositoryMapping> repositories = getRepositoryMappings();
		GHGitRepositoryMapping repository = repositories.isEmpty() ? null : repositories.getFirst();
		GithubServerPath server = repository != null ? repository.getRepository().getServerPath()
				: GithubServerPath.DEFAULT_SERVER;
		SelectionDetails selectionDetails = getSelectionDetails(server,
				repository != null ? List.of(repository) : List.of());

		GithubAccount firstMatchingAccount = selectionDetails.getAccounts().isEmpty() ? null
				: selectionDetails.getAccounts().getFirst();
		ExecutorResult executor = createAuthenticatedIfPossible(server, repository, firstMatchingAccount,
				selectionDetails, Reason.FIRST_MATCHING_ACCOUNT);
		if (executor != null) {
			return executor;
		}

		GithubAccount defaultAccount = lookupDefaultAccount();
		if (defaultAccount != null && !defaultAccount.equals(firstMatchingAccount)) {
			executor = createAuthenticatedIfPossible(server, repository, defaultAccount, selectionDetails,
					Reason.DEFAULT_ACCOUNT);
			if (executor != null) {
				return executor;
			}
		}

		return anonymous(server, selectionDetails);
	}

	/**
	 * Create an executor for the given repository coordinates if the account choice
	 * is clear.
	 * @param repository the GitHub repository coordinates.
	 * @return the executor resolution result.
	 */
	ExecutorResult getExecutor(GHRepositoryCoordinates repository) {
		return getExecutor(repository.getServerPath());
	}

	/**
	 * Create an executor for the given GitHub repository mapping if the account
	 * choice is clear.
	 * @param repository the GitHub repository mapping.
	 * @return the executor resolution result.
	 */
	ExecutorResult getExecutor(GHGitRepositoryMapping repository) {
		GithubServerPath server = repository.getRepository().getServerPath();
		SelectionDetails selectionDetails = getSelectionDetails(server, repository);
		return resolve(server, selectionDetails);
	}

	/**
	 * Create an executor for the given server if the account choice is clear.
	 * @param server the GitHub server.
	 * @return the executor resolution result.
	 */
	ExecutorResult getExecutor(GithubServerPath server) {
		return resolve(server, getSelectionDetails(server));
	}

	/**
	 * Create an executor from a selection made by a future UI adapter.
	 */
	ExecutorResult getExecutor(GHGitRepositoryMapping repository, GithubAccount account) {
		GithubServerPath server = repository.getRepository().getServerPath();
		SelectionDetails selectionDetails = getSelectionDetails(server, repository);
		return createAuthenticated(server, repository, account, selectionDetails, Reason.EXPLICIT_SELECTION);
	}

	/**
	 * Return the selector inputs for a future UI adapter.
	 * @param server the GitHub server.
	 * @return immutable selector details.
	 */
	SelectionDetails getSelectionDetails(GithubServerPath server) {
		return getSelectionDetails(server, getRepositoryMappings(server));
	}

	/**
	 * Return the last resolution decision made by this factory.
	 * @return the last decision, or {@literal null} if the factory has not been
	 * used yet.
	 */
	@Nullable
	Decision getLastDecision() {
		return lastDecision;
	}

	static GithubServerPath serverPath(String gitHost) {

		if (!StringUtils.hasText(gitHost)) {
			return GithubServerPath.DEFAULT_SERVER;
		}

		try {
			return GithubServerPath.from(gitHost.trim());
		} catch (GithubParseException ex) {
			LOG.warn("Failed to parse GitHub server path '%s': %s".formatted(gitHost, ex.getMessage()));
			return new GithubServerPath(gitHost.trim());
		}
	}

	private ExecutorResult resolve(GithubServerPath server, SelectionDetails selectionDetails) {

		GHGitRepositoryMapping repository = selectRepository(selectionDetails);
		GithubAccount account = selectAccount(server, selectionDetails);
		if (account == null) {
			if (!selectionDetails.getAccounts().isEmpty()) {
				return selectionRequired(server, repository, null, selectionDetails, Reason.MULTIPLE_ACCOUNTS);
			}
			if (repository != null) {
				return selectionRequired(server, repository, null, selectionDetails, Reason.NO_ACCOUNT);
			}
			return anonymous(server, selectionDetails);
		}

		if (selectionDetails.getAccounts().size() > 1 && !matchesLastDecision(repository, account)
				&& !isDefaultAccount(account, server)) {
			return selectionRequired(server, repository, account, selectionDetails, Reason.MULTIPLE_ACCOUNTS);
		}

		Reason reason = matchesLastDecision(repository, account) ? Reason.REMEMBERED_SELECTION
				: isDefaultAccount(account, server) ? Reason.DEFAULT_ACCOUNT : Reason.SINGLE_ACCOUNT;
		return createAuthenticated(server, repository, account, selectionDetails, reason);
	}

	private ExecutorResult createAuthenticated(GithubServerPath server, @Nullable GHGitRepositoryMapping repository,
			GithubAccount account, SelectionDetails selectionDetails, Reason reason) {

		if (!accountMatchesServer(account, server)) {
			return selectionRequired(server, repository, account, selectionDetails, Reason.ACCOUNT_SERVER_MISMATCH);
		}

		String token = lookupToken(account);
		if (!StringUtils.hasText(token)) {
			return selectionRequired(server, repository, account, selectionDetails, Reason.MISSING_CREDENTIALS);
		}

		GithubApiRequestExecutor executor = createExecutor(server, account, token);
		Decision decision = Decision.authenticated(server, repository, account, reason);
		return result(decision, selectionDetails, executor);
	}

	private @Nullable ExecutorResult createAuthenticatedIfPossible(GithubServerPath server,
			@Nullable GHGitRepositoryMapping repository, @Nullable GithubAccount account,
			SelectionDetails selectionDetails, Reason reason) {

		if (account == null || !accountMatchesServer(account, server)) {
			return null;
		}

		String token = lookupToken(account);
		if (!StringUtils.hasText(token)) {
			return null;
		}

		GithubApiRequestExecutor executor = createExecutor(server, account, token);
		Decision decision = Decision.authenticated(server, repository, account, reason);
		return result(decision, selectionDetails, executor);
	}

	private ExecutorResult anonymous(GithubServerPath server, SelectionDetails selectionDetails) {
		return result(new Decision(Kind.ANONYMOUS, server, null, null, Reason.ANONYMOUS), selectionDetails,
				createAnonymousExecutor());
	}

	private ExecutorResult selectionRequired(GithubServerPath server, @Nullable GHGitRepositoryMapping repository,
			@Nullable GithubAccount account, SelectionDetails selectionDetails, Reason reason) {
		return result(Decision.selectionRequired(server, repository, account, reason), selectionDetails, null);
	}

	private ExecutorResult result(Decision decision, SelectionDetails selectionDetails,
			@Nullable GithubApiRequestExecutor executor) {
		lastDecision = decision;
		return new ExecutorResult(decision, selectionDetails, executor);
	}

	private @Nullable GHGitRepositoryMapping selectRepository(SelectionDetails details) {

		Decision previous = lastDecision;
		if (previous != null && previous.getRepository() != null
				&& details.getRepositories().contains(previous.getRepository())) {
			return previous.getRepository();
		}

		List<GHGitRepositoryMapping> repositories = details.getRepositories();
		return repositories.size() == 1 ? repositories.getFirst() : null;
	}

	private @Nullable GithubAccount selectAccount(GithubServerPath server, SelectionDetails details) {

		Decision previous = lastDecision;
		if (previous != null && previous.getAccount() != null
				&& details.getAccounts().contains(previous.getAccount())) {
			return previous.getAccount();
		}

		GithubAccount defaultAccount = lookupDefaultAccount();
		if (defaultAccount != null && details.getAccounts().contains(defaultAccount)
				&& accountMatchesServer(defaultAccount, server)) {
			return defaultAccount;
		}

		List<GithubAccount> accounts = details.getAccounts();
		return accounts.size() == 1 ? accounts.getFirst() : null;
	}

	private boolean matchesLastDecision(@Nullable GHGitRepositoryMapping repository, GithubAccount account) {

		Decision previous = lastDecision;
		return previous != null && previous.getKind() == Kind.AUTHENTICATED
				&& (previous.getRepository() == null || previous.getRepository().equals(repository))
				&& previous.getAccount() != null && previous.getAccount().equals(account);
	}

	private boolean isDefaultAccount(GithubAccount account, GithubServerPath server) {

		GithubAccount defaultAccount = lookupDefaultAccount();
		return defaultAccount != null && defaultAccount.equals(account) && accountMatchesServer(defaultAccount, server);
	}

	private boolean accountMatchesServer(GithubAccount account, GithubServerPath server) {
		return server.equals(account.getServer(), true);
	}

	private SelectionDetails getSelectionDetails(GithubServerPath server,
			GHGitRepositoryMapping repository) {
		return getSelectionDetails(server, List.of(repository));
	}

	private SelectionDetails getSelectionDetails(GithubServerPath server,
			Collection<GHGitRepositoryMapping> repositories) {

		List<GHGitRepositoryMapping> repositoryMappings = List.copyOf(repositories);
		List<GithubAccount> accounts = getAccounts(server);
		Decision previous = lastDecision;
		return new SelectionDetails(project, repositoriesManager, accountManager, server, repositoryMappings,
				accounts, selectRepository(repositoryMappings, previous), selectAccount(accounts, previous), previous);
	}

	private @Nullable GHGitRepositoryMapping selectRepository(List<GHGitRepositoryMapping> repositories,
			@Nullable Decision previous) {

		if (previous != null && previous.getRepository() != null && repositories.contains(previous.getRepository())) {
			return previous.getRepository();
		}
		return repositories.size() == 1 ? repositories.getFirst() : null;
	}

	private @Nullable GithubAccount selectAccount(List<GithubAccount> accounts, @Nullable Decision previous) {

		if (previous != null && previous.getAccount() != null && accounts.contains(previous.getAccount())) {
			return previous.getAccount();
		}

		GithubAccount defaultAccount = lookupDefaultAccount();
		if (defaultAccount != null && accounts.contains(defaultAccount)) {
			return defaultAccount;
		}
		return accounts.size() == 1 ? accounts.getFirst() : null;
	}

	private List<GHGitRepositoryMapping> getRepositoryMappings(GithubServerPath server) {

		List<GHGitRepositoryMapping> result = new ArrayList<>();
		for (GHGitRepositoryMapping mapping : getRepositoryMappings()) {
			if (server.equals(mapping.getRepository().getServerPath(), true)) {
				result.add(mapping);
			}
		}
		return result;
	}

	private List<GHGitRepositoryMapping> getRepositoryMappings() {

		List<GHGitRepositoryMapping> result = new ArrayList<>();
		for (GHGitRepositoryMapping mapping : repositoriesManager.getKnownRepositoriesState()
				.getValue()) {
			result.add(mapping);
		}
		return result;
	}

	private List<GithubAccount> getAccounts(GithubServerPath server) {

		List<GithubAccount> result = new ArrayList<>();
		GHAccountManager manager = accountManager;
		for (GithubAccount account : (Collection<GithubAccount>) (manager != null ? manager.getAccountsState()
				.getValue() : List.of())) {
			if (accountMatchesServer(account, server)) {
				result.add(account);
			}
		}
		return result;
	}

	protected @Nullable GithubAccount lookupDefaultAccount() {
		return defaultAccountHolder.getAccount();
	}

	protected @Nullable String lookupToken(GithubAccount account) {

		try {
			return BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
					(scope, continuation) -> accountManager.findCredentials(account, continuation));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ProcessCanceledException(ex);
		}
	}

	protected GithubApiRequestExecutor createExecutor(GithubServerPath server, GithubAccount account, String token) {
		return GithubApiRequestExecutor.Factory.getInstance().create(server, token);
	}

	protected GithubApiRequestExecutor createAnonymousExecutor() {
		return GithubApiRequestExecutor.Factory.getInstance().create();
	}

	enum Kind {

		AUTHENTICATED,

		ANONYMOUS,

		SELECTION_REQUIRED

	}

	enum Reason {

		REMEMBERED_SELECTION(true),

		EXPLICIT_SELECTION(true),

		DEFAULT_ACCOUNT(true),

		FIRST_MATCHING_ACCOUNT(true),

		SINGLE_ACCOUNT(true),

		ANONYMOUS(true),

		MULTIPLE_ACCOUNTS(false),

		NO_ACCOUNT(false),

		MISSING_CREDENTIALS(false),

		ACCOUNT_SERVER_MISMATCH(false);

		private final boolean successful;

		Reason(boolean successful) {
			this.successful = successful;
		}

		boolean isSuccessful() {
			return successful;
		}

	}



	static class SelectionDetails {

		private final @Nullable Project project;

		private final @Nullable GHHostedRepositoriesManager repositoriesManager;

		private final @Nullable GHAccountManager accountManager;

		private final GithubServerPath server;

		private final List<GHGitRepositoryMapping> repositories;

		private final List<GithubAccount> accounts;

		private final @Nullable GHGitRepositoryMapping suggestedRepository;

		private final @Nullable GithubAccount suggestedAccount;

		private final @Nullable Decision previousDecision;

		SelectionDetails(@Nullable Project project, @Nullable GHHostedRepositoriesManager repositoriesManager,
				@Nullable GHAccountManager accountManager, GithubServerPath server,
				List<GHGitRepositoryMapping> repositories, List<GithubAccount> accounts,
				@Nullable GHGitRepositoryMapping suggestedRepository, @Nullable GithubAccount suggestedAccount,
				@Nullable Decision previousDecision) {

			this.project = project;
			this.repositoriesManager = repositoriesManager;
			this.accountManager = accountManager;
			this.server = server;
			this.repositories = List.copyOf(repositories);
			this.accounts = List.copyOf(accounts);
			this.suggestedRepository = suggestedRepository;
			this.suggestedAccount = suggestedAccount;
			this.previousDecision = previousDecision;
		}

		@Nullable
		Project getProject() {
			return project;
		}

		@Nullable
		GHHostedRepositoriesManager getRepositoriesManager() {
			return repositoriesManager;
		}

		@Nullable
		GHAccountManager getAccountManager() {
			return accountManager;
		}

		GithubServerPath getServer() {
			return server;
		}

		List<GHGitRepositoryMapping> getRepositories() {
			return repositories;
		}

		List<GithubAccount> getAccounts() {
			return accounts;
		}

		@Nullable
		GHGitRepositoryMapping getSuggestedRepository() {
			return suggestedRepository;
		}

		@Nullable
		GithubAccount getSuggestedAccount() {
			return suggestedAccount;
		}

		@Nullable
		Decision getPreviousDecision() {
			return previousDecision;
		}

		boolean canShowSelector() {
			return project != null && repositoriesManager != null && accountManager != null && !repositories.isEmpty();
		}

	}

	static class Decision {

		private final Kind kind;

		private final @Nullable GithubServerPath server;

		private final @Nullable GHGitRepositoryMapping repository;

		private final @Nullable GithubAccount account;

		private final Reason reason;

		private Decision(Kind kind, @Nullable GithubServerPath server, @Nullable GHGitRepositoryMapping repository,
				@Nullable GithubAccount account, Reason reason) {

			this.kind = kind;
			this.server = server;
			this.repository = repository;
			this.account = account;
			this.reason = reason;
		}

		static Decision authenticated(GithubServerPath server, @Nullable GHGitRepositoryMapping repository,
				GithubAccount account, Reason reason) {
			return new Decision(Kind.AUTHENTICATED, server, repository, account, reason);
		}

		static Decision selectionRequired(GithubServerPath server, @Nullable GHGitRepositoryMapping repository,
				@Nullable GithubAccount account, Reason reason) {
			return new Decision(Kind.SELECTION_REQUIRED, server, repository, account, reason);
		}

		Kind getKind() {
			return kind;
		}

		GithubServerPath getServer() {
			return server;
		}

		@Nullable
		GHGitRepositoryMapping getRepository() {
			return repository;
		}

		@Nullable
		GithubAccount getAccount() {
			return account;
		}

		Reason getReason() {
			return reason;
		}

		boolean hasExecutor() {
			return reason.isSuccessful();
		}

	}

	static class ExecutorResult {

		private final Decision decision;

		private final SelectionDetails selectionDetails;

		private final @Nullable GithubApiRequestExecutor executor;

		ExecutorResult(Decision decision, SelectionDetails selectionDetails,
				@Nullable GithubApiRequestExecutor executor) {

			this.decision = decision;
			this.selectionDetails = selectionDetails;
			this.executor = executor;
		}

		Decision getDecision() {
			return decision;
		}

		SelectionDetails getSelectionDetails() {
			return selectionDetails;
		}

		@Nullable
		GithubApiRequestExecutor getExecutor() {
			return executor;
		}

		boolean hasExecutor() {
			return executor != null;
		}

		public GithubApiRequestExecutor getRequiredExecutor() {
			Assert.state(hasExecutor(), "No executor available");
			return executor;
		}

	}

}
