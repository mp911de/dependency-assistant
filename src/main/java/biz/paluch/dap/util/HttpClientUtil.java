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

package biz.paluch.dap.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;
import org.springframework.util.Assert;

/**
 * Performs bounded, IDE-aware HTTP requests and validates browser targets used
 * by plugin actions.
 *
 * <p>HTTP transport uses {@link HttpRequests}, which integrates with the IDE
 * proxy selector, proxy authentication, and progress-indicator cancellation.
 * Requests share the configured timeouts, user agent, and a 24-request
 * concurrency limit. The string-returning fetch enforces the response-size
 * limit; a fetch supplying its own response processor opts in through
 * {@link #capped(InputStream, int)}. A thread interrupted while waiting for a
 * request permit returns an absent result with its interrupt status restored.
 *
 * @author Mark Paluch
 */
public class HttpClientUtil {

	/**
	 * Maximum response body size accepted by metadata fetches (10 MB).
	 */
	public static final int MAX_RESPONSE_BODY_BYTES = 10 * 1024 * 1024;

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

	/**
	 * Fetch the given URI as a UTF-8 string after applying the request
	 * customization.
	 *
	 * <p>The response body is limited to {@link #MAX_RESPONSE_BODY_BYTES}.
	 *
	 * @param uri the URL to fetch.
	 * @param requestFunction the request customization to apply before connecting.
	 * @return the response body, or {@literal null} if the thread is interrupted
	 * while waiting for a request permit.
	 * @throws IOException if the request fails or the response exceeds the size
	 * limit.
	 */
	public static @Nullable String fetchUrl(URI uri, Function<RequestBuilder, RequestBuilder> requestFunction)
			throws IOException {
		return fetchUrl(uri, requestFunction, HttpClientUtil::readUtf8StreamCapped);
	}

	/**
	 * Fetch the given URI and process its connected response.
	 *
	 * @param <T> the processed response type.
	 * @param uri the URL to fetch.
	 * @param requestFunction the request customization to apply before connecting.
	 * @param responseProcessor the function that reads the connected response.
	 * @return the processed response, or {@literal null} if the thread is
	 * interrupted while waiting for a request permit.
	 * @throws IOException if the request or response processing fails.
	 */
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
	 * Test whether the URI uses the HTTP or HTTPS scheme.
	 *
	 * @param uri the URI to inspect. It must declare a scheme.
	 * @return {@code true} if the scheme is {@code http} or {@code https}.
	 */
	public static boolean isBrowsable(URI uri) {
		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		return StringUtils.hasText(scheme) && (scheme.equals("http") || scheme.equals("https"));
	}

	/**
	 * Test whether the supplied URI text starts with a lowercase HTTP or HTTPS
	 * scheme.
	 *
	 * @param scheme the URI text to inspect, or {@literal null}.
	 * @return {@code true} if the text starts with {@code http:} or {@code https:}.
	 */
	@Contract("null -> false")
	public static boolean isBrowsable(@Nullable String scheme) {
		return StringUtils.hasText(scheme) && (scheme.startsWith("http:") || scheme.startsWith("https:"));
	}

	/**
	 * Open the given URI text in the default browser.
	 *
	 * @param uri the URI to open.
	 * @throws IllegalArgumentException if the value is blank or does not start with
	 * an HTTP or HTTPS scheme.
	 */
	public static void openBrowser(String uri) {
		Assert.hasText(uri, "URI must not be empty");
		String scheme = uri.toLowerCase(Locale.ROOT);
		Assert.isTrue(isBrowsable(scheme), "URI must start with http or https");
		BrowserUtil.browse(uri);
	}

	/**
	 * Open the given URI in the default browser.
	 *
	 * @param uri the URI to open. It must declare a scheme.
	 * @throws IllegalArgumentException if the declared scheme is not HTTP or HTTPS.
	 */
	public static void openBrowser(URI uri) {
		Assert.isTrue(isBrowsable(uri), "URI must start with http or https");
		BrowserUtil.browse(uri);
	}

	/**
	 * Return the {@code User-Agent} for metadata requests.
	 *
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
	 * Read the response body as a UTF-8 string, failing before a body larger than
	 * {@link #MAX_RESPONSE_BODY_BYTES} is fully allocated.
	 *
	 * @param request the HTTP request to read.
	 * @return the response body decoded as UTF-8.
	 * @throws ResponseTooLargeException if the response exceeds
	 * {@link #MAX_RESPONSE_BODY_BYTES}.
	 * @throws IOException if the response cannot be read.
	 */
	public static String readUtf8StreamCapped(HttpRequests.Request request) throws IOException {

		try (InputStream in = capped(request.getInputStream(), MAX_RESPONSE_BODY_BYTES)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * Wrap a response body stream so that reading past {@code maxBytes} throws
	 * {@link ResponseTooLargeException}.
	 *
	 * <p>Closing the returned stream closes the wrapped one.
	 *
	 * @param body the response body stream to wrap.
	 * @param maxBytes the number of bytes to accept.
	 * @return the capped stream.
	 */
	public static InputStream capped(InputStream body, int maxBytes) {
		return new CappedInputStream(body, maxBytes);
	}

	/**
	 * Return the effective port for the given URI.
	 *
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

	/**
	 * Return whether two URIs have the same host and effective port.
	 *
	 * <p>Host comparison is case-insensitive. Scheme and path are not compared.
	 *
	 * @param u1 the configured repository URI.
	 * @param u2 the effective request URI.
	 * @return {@code true} if host and effective port match.
	 */
	public static boolean hasSameBaseUri(URI u1, URI u2) {

		String baseHost = u1.getHost();
		String targetHost = u2.getHost();
		if (baseHost == null || targetHost == null) {
			return false;
		}
		return baseHost.equalsIgnoreCase(targetHost) && getEffectivePort(u1) == getEffectivePort(u2);
	}

	/**
	 * Stream that fails once the number of bytes read exceeds the configured cap.
	 */
	private static class CappedInputStream extends FilterInputStream {

		private final int maxBytes;

		private long total;

		CappedInputStream(InputStream in, int maxBytes) {
			super(in);
			this.maxBytes = maxBytes;
		}

		@Override
		public int read() throws IOException {
			int read = super.read();
			if (read >= 0) {
				count(1);
			}
			return read;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int read = super.read(b, off, len);
			if (read > 0) {
				count(read);
			}
			return read;
		}

		private void count(int read) throws IOException {
			total += read;
			if (total > maxBytes) {
				throw new ResponseTooLargeException(maxBytes);
			}
		}

	}

}
