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
import java.util.function.Function;

import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitHubAccountResolver}.
 *
 * @author Mark Paluch
 */
class GitHubAccountResolverTests {

	@Test
	void resolvesAnonymouslyWhenNoAccountConfigured() {

		GitHubAccountResolver resolver = resolver(List.of(), List.of(), null, account -> null);

		GitHubAccountResolver.ResolvedAccount result = resolver.resolve();

		assertThat(result.isAuthenticated()).isFalse();
		assertThat(result.account()).isNull();
		assertThat(result.token()).isNull();
		assertThat(result.server()).isEqualTo(GithubServerPath.DEFAULT_SERVER);
	}

	@Test
	void anonymousFactoryProducesUnauthenticatedDefaultServer() {

		GitHubAccountResolver.ResolvedAccount anonymous = GitHubAccountResolver.ResolvedAccount.anonymous();

		assertThat(anonymous.isAuthenticated()).isFalse();
		assertThat(anonymous.token()).isNull();
		assertThat(anonymous.account()).isNull();
		assertThat(anonymous.server()).isEqualTo(GithubServerPath.DEFAULT_SERVER);
	}

	@Test
	void prefersAccountMatchingProjectRemote() {

		GithubAccount alice = account("id-alice", "alice", GithubServerPath.DEFAULT_SERVER);
		GithubAccount bob = account("id-bob", "bob", GithubServerPath.DEFAULT_SERVER);
		GitHubAccountResolver resolver = resolver(List.of(alice, bob), List.of("github.com"), bob,
				tokenLookup("id-alice", "alice-token", "id-bob", "bob-token"));

		GitHubAccountResolver.ResolvedAccount result = resolver.resolve();

		assertThat(result.token()).isEqualTo("alice-token");
		assertThat(result.account()).isSameAs(alice);
	}

	@Test
	void fallsBackToDefaultAccountWhenNoRemoteMatches() {

		GithubAccount alice = account("id-alice", "alice", GithubServerPath.DEFAULT_SERVER);
		GithubAccount bob = account("id-bob", "bob", GithubServerPath.DEFAULT_SERVER);
		GitHubAccountResolver resolver = resolver(List.of(alice, bob), List.of(), bob,
				tokenLookup("id-alice", "alice-token", "id-bob", "bob-token"));

		GitHubAccountResolver.ResolvedAccount result = resolver.resolve();

		assertThat(result.token()).isEqualTo("bob-token");
		assertThat(result.account()).isSameAs(bob);
	}

	@Test
	void fallsBackToAnonymousWhenAccountManagerUnavailable() {

		GitHubAccountResolver resolver = resolver(List.of(), List.of("github.com"), null, account -> null);

		GitHubAccountResolver.ResolvedAccount result = resolver.resolve();

		assertThat(result.isAuthenticated()).isFalse();
	}

	@Test
	void fallsBackToAnonymousWhenTokenLookupReturnsNull() {

		GithubAccount alice = account("id-alice", "alice", GithubServerPath.DEFAULT_SERVER);
		GitHubAccountResolver resolver = resolver(List.of(alice), List.of(), alice, account -> null);

		GitHubAccountResolver.ResolvedAccount result = resolver.resolve();

		assertThat(result.isAuthenticated()).isFalse();
	}

	private static GitHubAccountResolver resolver(List<GithubAccount> accounts, List<String> remoteHosts,
			@Nullable GithubAccount defaultAccount, Function<GithubAccount, String> tokens) {
		return new GitHubAccountResolver(null) {

			@Override
			protected Collection<GithubAccount> lookupAccounts() {
				return accounts;
			}

			@Override
			protected Collection<String> collectRemoteHosts() {
				return remoteHosts;
			}

			@Override
			protected @Nullable GithubAccount lookupDefaultAccount() {
				return defaultAccount;
			}

			@Override
			protected @Nullable String lookupToken(GithubAccount account) {
				return tokens.apply(account);
			}

		};
	}

	private static GithubAccount account(String id, String name, GithubServerPath server) {
		return new GithubAccount(name, server, id);
	}

	private static Function<GithubAccount, String> tokenLookup(String id1, String token1, String id2, String token2) {
		return account -> {
			if (id1.equals(account.getId())) {
				return token1;
			}
			if (id2.equals(account.getId())) {
				return token2;
			}
			return null;
		};
	}

}
