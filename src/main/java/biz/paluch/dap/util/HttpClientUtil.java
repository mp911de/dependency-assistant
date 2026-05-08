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
 * Utility to create HTTP clients and issue requests.
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
	 * Send an HTTP request.
	 * @throws InterruptedException
	 * @throws IOException
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
	 * Body handler that reads the response body into a string and limits at
	 * {@link #MAX_RESPONSE_BODY_BYTES}.
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

	public static boolean hasBody(HttpResponse<?> response) {
		return response.statusCode() >= 200 && response.statusCode() < 300;
	}

	/**
	 * {@link Authenticator} that uses the {@link ProxyAuthentication} to
	 * authenticate against a proxy.
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
