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

package biz.paluch.dap.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Shared helpers for IDE-aware HTTP access.
 * <p>HTTP transport itself uses {@link com.intellij.util.io.HttpRequests},
 * which natively integrates with the IDE proxy selector, proxy authentication,
 * and progress-indicator cancellation. This class only centralizes the
 * {@code User-Agent} computation that release sources apply to their requests.
 *
 * @author Mark Paluch
 */
public class HttpClientUtil {

	/**
	 * Maximum response body size accepted by metadata fetches (5 MB).
	 */
	public static final int MAX_RESPONSE_BODY_BYTES = 5 * 1024 * 1024;

	/**
	 * Connect timeout for metadata fetches (10 seconds).
	 */
	public static final int CONNECT_TIMEOUT_MS = 10_000;

	/**
	 * Read timeout for metadata fetches (10 seconds).
	 */
	public static final int READ_TIMEOUT_MS = 10_000;

	private static final Semaphore semaphore = new Semaphore(24);

	private HttpClientUtil() {
	}

	public static @Nullable String fetchUrl(URI uri, Function<RequestBuilder, RequestBuilder> requestFunction)
			throws IOException {
		return fetchUrl(uri, requestFunction, HttpClientUtil::readUtf8StreamCapped);
	}

	public static <T> @Nullable T fetchUrl(URI uri, Function<RequestBuilder, RequestBuilder> requestFunction,
			HttpRequests.RequestProcessor<T> responseProcessor) throws IOException {

		try {
			semaphore.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}

		try {
			RequestBuilder requestBuilder = HttpRequests.request(uri.toASCIIString()) //
					.userAgent(HttpClientUtil.getUserAgent()) //
					.connectTimeout(HttpClientUtil.CONNECT_TIMEOUT_MS) //
					.readTimeout(HttpClientUtil.READ_TIMEOUT_MS);
			return requestFunction.apply(requestBuilder).connect(responseProcessor);
		} finally {
			semaphore.release();
		}
	}

	/**
	 * Return the {@code User-Agent} for metadata requests.
	 * <p>The value is derived from IntelliJ product information when the
	 * application is available and falls back to a generic IDE identifier in
	 * non-application contexts.
	 *
	 * @return the user agent string.
	 */
	public static String getUserAgent() {

		Application app = ApplicationManager.getApplication();
		if (app != null && !app.isDisposed()) {
			String productName = ApplicationNamesInfo.getInstance().getFullProductName();
			String version = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
			return productName + '/' + version;
		}
		return "IntelliJ";
	}

	/**
	 * Read the response body as a UTF-8 string, streaming with a hard size cap.
	 * <p>The body is read in 8&nbsp;KB chunks and the cumulative size is checked
	 * after each read. Reads exceeding {@link #MAX_RESPONSE_BODY_BYTES} fail with
	 * an {@link IOException} before the full body is materialised, preventing a
	 * hostile or oversized response from being fully allocated in memory.
	 *
	 * @param request the HTTP request to read.
	 * @return the response body decoded as UTF-8.
	 * @throws IOException if the response exceeds {@link #MAX_RESPONSE_BODY_BYTES}
	 * or the underlying stream fails.
	 */
	public static String readUtf8StreamCapped(HttpRequests.Request request) throws IOException {

		try (InputStream in = request.getInputStream()) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[8 * 1024];
			long total = 0;
			int read;
			while ((read = in.read(buf)) >= 0) {
				total += read;
				if (total > MAX_RESPONSE_BODY_BYTES) {
					throw new IOException("Response body exceeds %s bytes"
							.formatted(StringUtil.formatFileSize(MAX_RESPONSE_BODY_BYTES)));
				}
				out.write(buf, 0, read);
			}
			return out.toString(StandardCharsets.UTF_8);
		}
	}

	/**
	 * Return the effective port for the given URI.
	 * <p>When the URI specifies an explicit port, that port is returned. Otherwise
	 * the scheme default is used: {@code 443} for {@code https} and {@code 80} for
	 * {@code http}.
	 *
	 * @param uri the URI to inspect.
	 * @return the explicit port, the scheme default ({@code 443} or {@code 80}), or
	 * {@code -1} when no port is given and the scheme is neither {@code http} nor
	 * {@code https}.
	 */
	public static int getEffectivePort(URI uri) {

		int port = uri.getPort();
		if (port != -1) {
			return port;
		}
		String scheme = uri.getScheme();
		if ("https".equalsIgnoreCase(scheme)) {
			return 443;
		}
		if ("http".equalsIgnoreCase(scheme)) {
			return 80;
		}
		return -1;
	}

}
