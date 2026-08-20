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
 * Collects all items of a paginated GitHub REST API resource by following the
 * {@code Link: rel="next"} header of each response.
 *
 * <p>Each follow-up URL must stay on the API base the initial request was built
 * for. A {@code next} link pointing elsewhere is rejected instead of followed
 * so a redirected or spoofed header cannot send credentials to another host.
 *
 * <p>This is a Java replacement for the GitHub plugin's internal
 * {@code GithubApiPagesLoader}. The page-request flavor ({@code JsonPage}
 * versus {@code JsonSearchPage}) is supplied by the caller.
 *
 * @author Mark Paluch
 */
class GitHubApiPages {

	private GitHubApiPages() {
	}

	/**
	 * Load all pages, starting at {@code initialRequest}.
	 * @param executor the executor running the requests.
	 * @param indicator progress indicator to cancel long-running fetches.
	 * @param apiBase the API base URL every page request must start with.
	 * @param initialRequest request for the first page.
	 * @param nextPageRequest factory creating a page request for a {@code next}
	 * URL.
	 * @return all items across all pages, in page order.
	 * @throws IOException if a page request fails.
	 * @throws IllegalStateException if a {@code next} URL points outside
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
