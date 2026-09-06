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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;
import org.jspecify.annotations.Nullable;

/**
 * Loads paginated GitHub REST resources without using the plugin's internal
 * loader.
 * <p>Follow-up URLs must start with the supplied API base. This lexical check
 * is not a credential or trust boundary.
 *
 * @author Mark Paluch
 */
class GitHubApiPages {

	private GitHubApiPages() {
	}

	/**
	 * Load all items in page order.
	 * @param nextPageRequest creates a request for the next-page URL.
	 * @throws IOException if a page request fails.
	 * @throws IllegalStateException if a next-page URL does not start with
	 * {@code apiBase}.
	 */
	static <T> List<T> loadAll(GithubApiRequestExecutor executor, ProgressIndicator indicator, String apiBase,
			GithubApiRequest<GithubResponsePage<T>> initialRequest,
			Function<String, GithubApiRequest<GithubResponsePage<T>>> nextPageRequest) throws IOException {

		List<T> items = new ArrayList<>();
		GithubApiRequest<GithubResponsePage<T>> request = initialRequest;

		while (request != null) {

			GithubResponsePage<T> page = executor.execute(indicator, request);
			items.addAll(page.getItems());
			request = nextRequest(apiBase, page.getNextLink(), nextPageRequest);
		}

		return items;
	}

	private static <T> @Nullable GithubApiRequest<GithubResponsePage<T>> nextRequest(String apiBase,
			@Nullable String nextUrl, Function<String, GithubApiRequest<GithubResponsePage<T>>> nextPageRequest) {

		if (nextUrl == null) {
			return null;
		}

		if (!nextUrl.startsWith(apiBase)) {
			throw new IllegalStateException("Pagination URL does not match expected server: %s".formatted(nextUrl));
		}

		return nextPageRequest.apply(nextUrl);
	}

}
