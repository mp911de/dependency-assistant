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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import biz.paluch.dap.util.StringUtils;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import git4idea.remote.hosting.GitHostingUrlUtil;
import git4idea.repo.GitRepositoryManager;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder;
import org.jspecify.annotations.Nullable;

/**
 * Project-level service that resolves the configured {@link GithubAccount} (and
 * its bearer token) for GitHub API access.
 *
 * <p>
 * Project-scoped resolution prefers an authenticated account whose server
 * host matches a Git remote configured in the project, then the project default
 * account, then the first configured account. If no usable token is available,
 * resolution falls back to anonymous access.
 *
 * <p>
 * Host-scoped resolution first attempts an exact host match and then falls
 * back to the project default account. It does not use an arbitrary first
 * account for a different host.
 *
 * <p>
 * The service uses direct compile-time references to the IntelliJ GitHub
 * plugin and Git4Idea APIs. Both plugins are declared as optional dependencies
 * so the always-loaded {@link biz.paluch.dap.github} package only reaches this
 * class after {@link GitHubAssistant} has confirmed that GitHub integration is
 * available.
 *
 * @author Mark Paluch
 */
class GitHubAccountResolver {

	private static final Logger LOG = Logger.getInstance(GitHubAccountResolver.class);

	private final Project project;

	/**
	 * Production constructor invoked by the IntelliJ service container.
	 */
	GitHubAccountResolver(Project project) {
		this.project = project;
	}

	/**
	 * Resolve the most appropriate GitHub account for the project.
	 *
	 * <p>
	 * Resolution order:
	 * <ol>
	 * <li>account whose server host matches a Git remote configured in the
	 * project;</li>
	 * <li>the project default GitHub account;</li>
	 * <li>the first configured GitHub account;</li>
	 * <li>anonymous (no account, no token).</li>
	 * </ol>
	 *
	 * @return the resolved account; the result may be
	 * {@link ResolvedAccount#anonymous() anonymous} when no usable account is
	 * configured.
	 */
	ResolvedAccount resolve() {

		Collection<GithubAccount> accounts = lookupAccounts();
		if (accounts.isEmpty()) {
			return ResolvedAccount.anonymous();
		}

		if (project == null || TrustedProjects.isProjectTrusted(project)) {

			Collection<String> remoteHosts = collectRemoteHosts();

			for (GithubAccount account : accounts) {
				if (accountMatchesHost(account, remoteHosts)) {
					ResolvedAccount resolved = withToken(account);
					if (resolved.isAuthenticated()) {
						return resolved;
					}
				}
			}

			GithubAccount defaultAccount = lookupDefaultAccount();
			if (defaultAccount != null) {
				return withToken(defaultAccount);
			}
		}

		return ResolvedAccount.anonymous();
	}

	/**
	 * Resolve the most appropriate GitHub account for the given
	 * {@code remoteOriginHost}.
	 * <p>
	 * The host-specific variant does not fall back to an unrelated first
	 * account. If no authenticated exact host match is available, the project
	 * default account is used when present; otherwise resolution is anonymous.
	 *
	 * @return the resolved account, never {@literal null}; the result may be
	 * {@link ResolvedAccount#anonymous() anonymous} when no usable account is
	 * configured.
	 */
	ResolvedAccount resolve(String remoteOriginHost) {

		Collection<GithubAccount> accounts = lookupAccounts();
		if (accounts.isEmpty()) {
			return ResolvedAccount.anonymous();
		}

		for (GithubAccount account : accounts) {
			if (getHost(account).equalsIgnoreCase(remoteOriginHost)) {
				ResolvedAccount resolved = withToken(account);
				if (resolved.isAuthenticated()) {
					return resolved;
				}
			}
		}

		GithubAccount defaultAccount = lookupDefaultAccount();
		return defaultAccount != null ? withToken(defaultAccount) : ResolvedAccount.anonymous();
	}

	private boolean accountMatchesHost(GithubAccount account, Collection<String> remoteHosts) {
		return remoteHosts.contains(getHost(account));
	}

	private String getHost(GithubAccount account) {
		return account.getServer().getHost().toLowerCase(Locale.ROOT);
	}

	private ResolvedAccount withToken(GithubAccount account) {

		String token = lookupToken(account);
		if (token == null || token.isBlank()) {
			return ResolvedAccount.anonymous();
		}
		return new ResolvedAccount(account.getServer(), account, token);
	}

	protected Collection<GithubAccount> lookupAccounts() {

		GHAccountManager manager = ApplicationManager.getApplication().getService(GHAccountManager.class);
		return manager.getAccountsState().getValue();
	}

	protected Collection<String> collectRemoteHosts() {

		GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
		Set<String> hosts = new LinkedHashSet<>();

		repositoryManager.getRepositories().stream().flatMap(it -> it.getRemotes().stream())
				.flatMap(it -> it.getUrls().stream())
				.forEach(it -> {
					URI uri = GitHostingUrlUtil.getUriFromRemoteUrl(it);
					if (uri != null && StringUtils.hasText(uri.getHost())) {
						hosts.add(uri.getHost());
					}
				});

		return hosts;
	}

	protected @Nullable GithubAccount lookupDefaultAccount() {
		return project.getService(GithubProjectDefaultAccountHolder.class).getAccount();
	}

	protected @Nullable String lookupToken(GithubAccount account) {

		GHAccountManager manager = ApplicationManager.getApplication().getService(GHAccountManager.class);

		try {
			return BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,
					(scope, continuation) -> manager.findCredentials(account, continuation));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return null;
		} catch (RuntimeException ex) {
			LOG.warn("Failed to read GitHub credentials for %s: %s".formatted(account.getName(), ex.getMessage()));
			return null;
		}
	}

	/**
	 * Result of an account-resolution attempt, carrying the resolved server along
	 * with the matched account and its bearer token (if any).
	 *
	 * @param server the GitHub server path, never {@literal null}.
	 * @param account the resolved account; {@literal null} for anonymous access.
	 * @param token the bearer token; {@literal null} for anonymous access.
	 */
	record ResolvedAccount(GithubServerPath server, @Nullable GithubAccount account, @Nullable String token) {

		static ResolvedAccount anonymous() {
			return new ResolvedAccount(GithubServerPath.DEFAULT_SERVER, null, null);
		}

		boolean isAuthenticated() {
			return StringUtils.hasText(token);
		}

		@Override
		public String toString() {
			return server.toString();
		}

	}

}
