/*
 * Copyright 2026-present the original author or authors.
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

import java.lang.reflect.Method;
import java.util.Set;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import kotlinx.coroutines.flow.StateFlow;
import org.jetbrains.plugins.github.authentication.GHAccountsUtil;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Facade for {@link GithubAccount} retrieval. We want to reuse the same tokens
 * as the GitHub plugin does therefore we refer to the (now internal)
 * {@code GHAccountManager}.
 * <p>After all, this plugin is not for managing GitHub accounts but reuse
 * existing client functionality.
 *
 * @author Mark Paluch
 */
@Service(Service.Level.APP)
final class GitHubAccountManager {

	private static final String ACCOUNTS_STATE = "getAccountsState";

	private static final String GH_ACCOUNT_MANAGER = "org.jetbrains.plugins.github.authentication.accounts.GHAccountManager";

	private static final String CREDENTIALS_SERVICE_NAME = "GitHub";

	private final StateFlow<Set<GithubAccount>> accountsState;

	GitHubAccountManager() {
		this.accountsState = resolveAccountsState();
	}


	public static GitHubAccountManager getInstance() {
		return ApplicationManager.getApplication().getService(GitHubAccountManager.class);
	}


	StateFlow<Set<GithubAccount>> getAccountsState() {
		return accountsState;
	}

	@Nullable
	String findToken(GithubAccount account) {
		return PasswordSafe.getInstance().getPassword(new CredentialAttributes(
				CredentialAttributesKt.generateServiceName(CREDENTIALS_SERVICE_NAME, account.getId())));
	}

	@SuppressWarnings("unchecked")
	private static StateFlow<Set<GithubAccount>> resolveAccountsState() {

		Method method = ReflectionUtils.findMethod(GHAccountsUtil.class, ACCOUNTS_STATE);
		Object target = null;

		if (method == null) {
			Class<?> accountManagerType = ClassUtils.resolveClassName(GH_ACCOUNT_MANAGER,
					GHAccountsUtil.class.getClassLoader());
			method = ReflectionUtils.findMethod(accountManagerType, ACCOUNTS_STATE);
			target = ApplicationManager.getApplication().getService(accountManagerType);
		}

		Assert.state(method != null, "Cannot resolve GitHub accounts state");
		return (StateFlow<Set<GithubAccount>>) ReflectionUtils.invokeMethod(method, target);
	}

}
