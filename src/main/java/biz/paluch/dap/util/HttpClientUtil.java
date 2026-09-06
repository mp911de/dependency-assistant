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
 * IDE-aware HTTP requests using platform proxy and cancellation support.
 * Requests share timeouts and a concurrency bound. Interruption while waiting
 * for a permit returns an absent result with interrupt status restored.
 *
 * <p>String responses are size-limited. Custom response processors must apply
 * {@link #capped(InputStream, int)} themselves when a size limit is needed.
 *
 * @author Mark Paluch
 */
public class HttpClientUtil {

	public static final int MAX_RESPONSE_BODY_BYTES = 10 * 1024 * 1024;

	public static final int CONNECT_TIMEOUT_MS = 10_000;

	public static final int READ_TIMEOUT_MS = 10_000;

	private static final Semaphore semaphore = new Semaphore(24);

	private HttpClientUtil() {
	}

	/**
	 * Fetch a UTF-8 response limited to {@link #MAX_RESPONSE_BODY_BYTES}.
	 *
	 * @param requestFunction customizes the request before connecting.
	 * @return {@code null} if interrupted while waiting for a permit.
	 * @throws IOException if the request fails or the response exceeds the limit.
	 */
	public static @Nullable String fetchUrl(URI uri, Function<RequestBuilder, RequestBuilder> requestFunction)
			throws IOException {
		return fetchUrl(uri, requestFunction, HttpClientUtil::readUtf8StreamCapped);
	}

	/**
	 * Fetch and process a connected response. The processor owns response-size
	 * limits.
	 *
	 * @param requestFunction customizes the request before connecting.
	 * @return {@code null} if interrupted while waiting for a permit.
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
	 * Test for HTTP or HTTPS, ignoring scheme case. The URI must declare a scheme.
	 */
	public static boolean isBrowsable(URI uri) {
		String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
		return StringUtils.hasText(scheme) && (scheme.equals("http") || scheme.equals("https"));
	}

	/**
	 * Test whether the text begins with lowercase {@code http:} or {@code https:}.
	 */
	@Contract("null -> false")
	public static boolean isBrowsable(@Nullable String scheme) {
		return StringUtils.hasText(scheme) && (scheme.startsWith("http:") || scheme.startsWith("https:"));
	}

	/**
	 * Open an HTTP or HTTPS target in the browser.
	 *
	 * @throws IllegalArgumentException if the text is blank or has another scheme.
	 */
	public static void openBrowser(String uri) {
		Assert.hasText(uri, "URI must not be empty");
		String scheme = uri.toLowerCase(Locale.ROOT);
		Assert.isTrue(isBrowsable(scheme), "URI must start with http or https");
		BrowserUtil.browse(uri);
	}

	/**
	 * Open an HTTP or HTTPS target in the browser. The URI must declare a scheme.
	 *
	 * @throws IllegalArgumentException if the scheme is not HTTP or HTTPS.
	 */
	public static void openBrowser(URI uri) {
		Assert.isTrue(isBrowsable(uri), "URI must start with http or https");
		BrowserUtil.browse(uri);
	}

	/**
	 * Return an IDE user agent, falling back to a generic identifier outside the
	 * application.
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
	 * Read a UTF-8 response and close its stream.
	 *
	 * @throws ResponseTooLargeException if the body exceeds
	 * {@link #MAX_RESPONSE_BODY_BYTES}.
	 * @throws IOException if the body cannot be read.
	 */
	public static String readUtf8StreamCapped(HttpRequests.Request request) throws IOException {

		try (InputStream in = capped(request.getInputStream(), MAX_RESPONSE_BODY_BYTES)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/**
	 * Limit bytes read from the stream, throwing {@link ResponseTooLargeException}
	 * when the limit is exceeded. Closing the wrapper closes the underlying stream.
	 */
	public static InputStream capped(InputStream body, int maxBytes) {
		return new CappedInputStream(body, maxBytes);
	}

	/**
	 * Return the explicit port or HTTP/HTTPS default. Return {@code -1} for other
	 * schemes without an explicit port.
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
	 * Compare hosts case-insensitively and effective ports. Scheme and path are not
	 * compared. Missing hosts never match.
	 */
	public static boolean hasSameBaseUri(URI u1, URI u2) {

		String baseHost = u1.getHost();
		String targetHost = u2.getHost();
		if (baseHost == null || targetHost == null) {
			return false;
		}
		return baseHost.equalsIgnoreCase(targetHost) && getEffectivePort(u1) == getEffectivePort(u2);
	}

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
