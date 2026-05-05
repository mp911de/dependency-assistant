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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;
import org.jspecify.annotations.Nullable;

class GithubApiPagesLoader {

	private GithubApiPagesLoader() {
	}

	public static <T> List<T> loadAll(GithubApiRequestExecutor executor,
			ProgressIndicator indicator,
			Request<T> pagesRequest) throws IOException {
		List<T> result = new ArrayList<>();
		loadAll(executor, indicator, pagesRequest, result::addAll);
		return result;
	}

	public static <T> void loadAll(GithubApiRequestExecutor executor,
			ProgressIndicator indicator,
			Request<T> pagesRequest,
			java.util.function.Consumer<? super List<T>> pageItemsConsumer) throws IOException {
		GithubApiRequest<GithubResponsePage<T>> request = pagesRequest.getInitialRequest();

		while (request != null) {
			GithubResponsePage<T> page = executor.execute(indicator, request);
			pageItemsConsumer.accept(page.getItems());

			String nextLink = page.getNextLink();
			request = nextLink != null ? pagesRequest.getUrlRequestProvider().apply(nextLink) : null;
		}
	}

	public static <T> @Nullable T find(GithubApiRequestExecutor executor,
			ProgressIndicator indicator,
			Request<T> pagesRequest,
			Predicate<T> predicate) throws IOException {
		GithubApiRequest<GithubResponsePage<T>> request = pagesRequest.getInitialRequest();

		while (request != null) {
			GithubResponsePage<T> page = executor.execute(indicator, request);

			for (T item : page.getItems()) {
				if (predicate.test(item)) {
					return item;
				}
			}

			String nextLink = page.getNextLink();
			request = nextLink != null ? pagesRequest.getUrlRequestProvider().apply(nextLink) : null;
		}

		return null;
	}

	public static <T> List<T> load(GithubApiRequestExecutor executor,
			ProgressIndicator indicator,
			Request<T> pagesRequest,
			int maximum) throws IOException {
		List<T> result = new ArrayList<>();
		GithubApiRequest<GithubResponsePage<T>> request = pagesRequest.getInitialRequest();

		while (request != null) {
			GithubResponsePage<T> page = executor.execute(indicator, request);

			for (T item : page.getItems()) {
				result.add(item);
				if (result.size() == maximum) {
					return result;
				}
			}

			String nextLink = page.getNextLink();
			request = nextLink != null ? pagesRequest.getUrlRequestProvider().apply(nextLink) : null;
		}

		return result;
	}

	public static class Request<T> {

		private final GithubApiRequest<GithubResponsePage<T>> initialRequest;

		private final Function<String, GithubApiRequest<GithubResponsePage<T>>> urlRequestProvider;

		public Request(GithubApiRequest<GithubResponsePage<T>> initialRequest,
				Function<String, GithubApiRequest<GithubResponsePage<T>>> urlRequestProvider) {
			this.initialRequest = initialRequest;
			this.urlRequestProvider = urlRequestProvider;
		}

		public GithubApiRequest<GithubResponsePage<T>> getInitialRequest() {
			return initialRequest;
		}

		public Function<String, GithubApiRequest<GithubResponsePage<T>>> getUrlRequestProvider() {
			return urlRequestProvider;
		}

	}

}
