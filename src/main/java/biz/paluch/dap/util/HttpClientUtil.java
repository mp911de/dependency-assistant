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
import java.io.UncheckedIOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Semaphore;

import com.intellij.credentialStore.Credentials;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.Consumer;
import com.intellij.util.net.IdeProxySelector;
import com.intellij.util.net.ProxyAuthentication;
import org.jspecify.annotations.Nullable;

/**
 * Shared HTTP access point for dependency metadata retrieval.
 *
 * <p>The assistant resolves Maven repository metadata and NPM registry
 * documents from background tasks that should behave like IDE-originated
 * network traffic. This utility centralizes that policy by using IntelliJ's
 * proxy selector, proxy credentials, redirect handling, request timeout, and a
 * product-specific {@code User-Agent}.
 *
 * <p>Callers provide only the request-specific details, such as URI, method,
 * authentication headers, and the response body handler. The shared client and
 * concurrency guard keep metadata refreshes from creating an unbounded number
 * of simultaneous outbound connections.
 *
 * <p>The default body handler is intentionally conservative: metadata responses
 * are treated as UTF-8 text and rejected once they exceed the configured size
 * cap. Release sources should translate transport failures into their own
 * domain behavior, for example by returning no releases or raising an
 * artifact-not-found signal.
 *
 * @author Mark Paluch
 */
public class HttpClientUtil {

	private static final long MAX_RESPONSE_BODY_BYTES = 5 * 1024 * 1024;

	private static final String USER_AGENT = doGetUserAgent();

	private static final Semaphore semaphore = new Semaphore(24);

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder() //
			.proxy(IdeProxySelector.getDefault()) //
			.authenticator(ProxyAwareAuthenticator.INSTANCE) //
			.connectTimeout(TIMEOUT) //
			.followRedirects(HttpClient.Redirect.NORMAL) //
			.build();

	/**
	 * Return the process-wide {@code User-Agent} used for metadata requests.
	 * <p>The value is derived from IntelliJ product information when the
	 * application is available and falls back to a generic IDE identifier in
	 * non-application contexts.
	 */
	public static String getUserAgent() {
		return USER_AGENT;
	}

	private static String doGetUserAgent() {

		String userAgent;
		Application app = ApplicationManager.getApplication();
		if (app != null && !app.isDisposed()) {
			String productName = ApplicationNamesInfo.getInstance().getFullProductName();
			String version = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
			userAgent = productName + '/' + version;
		} else {
			userAgent = "IntelliJ";
		}

		return userAgent;
	}

	/**
	 * Send a request through the shared IDE-aware HTTP client.
	 * <p>The request customizer is responsible for the request-specific parts of
	 * the builder. This method applies the common {@code User-Agent}, timeout,
	 * redirect, proxy, and concurrency policies before performing the blocking
	 * send.
	 * @param requestBuilderConsumer callback that configures the request builder.
	 * @param bodyHandler body handler used to convert the response.
	 * @return the HTTP response.
	 * @throws InterruptedException if the calling task is cancelled or interrupted
	 * while waiting for capacity or a response.
	 * @throws IOException if the request cannot be sent or the body handler fails.
	 */
	public static <T> HttpResponse<T> sendRequest(Consumer<HttpRequest.Builder> requestBuilderConsumer,
			HttpResponse.BodyHandler<T> bodyHandler) throws InterruptedException, IOException {
		try {

			HttpRequest.Builder builder = HttpRequest.newBuilder();
			requestBuilderConsumer.accept(builder);

			semaphore.acquire();
			HttpRequest request = builder.header("User-Agent", HttpClientUtil.getUserAgent())
					.timeout(TIMEOUT).build();

			return HTTP_CLIENT.send(request, bodyHandler);
		} finally {
			semaphore.release();
		}
	}

	/**
	 * Return the standard text body handler for remote metadata documents.
	 * <p>The body is decoded as UTF-8 and rejected when it exceeds the internal
	 * response-size cap. Use a different handler only when the caller has a
	 * specific reason to consume a different payload shape.
	 */
	public static HttpResponse.BodyHandler<String> cappedUtf8BodyHandler() {

		return responseInfo -> HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofInputStream(),
				in -> {
					try {
						return readUtf8StreamCapped(in, MAX_RESPONSE_BODY_BYTES);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
	}

	private static String readUtf8StreamCapped(InputStream in, long maxBytes) throws IOException {

		try (in) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[16384];
			long total = 0;
			while (true) {
				int n = in.read(buf);
				if (n == -1) {
					break;
				}
				total += n;
				if (total > maxBytes) {
					throw new IOException("Response body exceeds maximum size");
				}
				out.write(buf, 0, n);
			}
			return out.toString(StandardCharsets.UTF_8);
		}
	}

	/**
	 * Return whether the response status represents a successful response that can
	 * be interpreted as carrying metadata.
	 * <p>Callers remain responsible for any domain-specific handling of empty
	 * success bodies or non-success statuses such as {@code 404}.
	 */
	public static boolean hasBody(HttpResponse<?> response) {
		return response.statusCode() >= 200 && response.statusCode() < 300;
	}

	/**
	 * {@link Authenticator} bridge from JDK HTTP client proxy challenges to
	 * IntelliJ's proxy credential store.
	 * <p>Only proxy authentication is handled here. Origin-server authentication
	 * remains under caller control so repository-specific credentials can be scoped
	 * by the release source that owns the request.
	 */
	public static class ProxyAwareAuthenticator extends Authenticator {

		public static final ProxyAwareAuthenticator INSTANCE = new ProxyAwareAuthenticator();

		private final ProxyAuthentication proxyAuthentication = ProxyAuthentication.getInstance();

		ProxyAwareAuthenticator() {
		}

		@Override
		protected @Nullable PasswordAuthentication getPasswordAuthentication() {

			if (getRequestorType() == RequestorType.PROXY) {

				Credentials knownAuthentication = proxyAuthentication.getKnownAuthentication(getRequestingHost(),
						getRequestingPort());

				if (knownAuthentication == null || knownAuthentication.getUserName() == null) {
					return null;
				}
				return new PasswordAuthentication(knownAuthentication.getUserName(),
						knownAuthentication.getPassword() != null ? knownAuthentication.getPassword().toCharArray()
								: new char[0]);
			}

			return null;
		}

	}

}
