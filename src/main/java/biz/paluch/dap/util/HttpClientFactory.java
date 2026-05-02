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

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.http.HttpClient;
import java.time.Duration;

import com.intellij.credentialStore.Credentials;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.net.IdeProxySelector;
import com.intellij.util.net.ProxyAuthentication;

/**
 * @author Mark Paluch
 */
public class HttpClientFactory {

	private static final String USER_AGENT = doGetUserAgent();

	public static HttpClient createHttpClient() {

		return HttpClient.newBuilder() //
				.proxy(IdeProxySelector.getDefault()) //
				.authenticator(ProxyAwareAuthenticator.INSTANCE) //
				.connectTimeout(Duration.ofSeconds(10)) //
				.followRedirects(HttpClient.Redirect.NORMAL) //
				.build();
	}

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
	 * {@link Authenticator} that uses the {@link ProxyAuthentication} to
	 * authenticate against a proxy.
	 */
	public static class ProxyAwareAuthenticator extends Authenticator {

		public static final ProxyAwareAuthenticator INSTANCE = new ProxyAwareAuthenticator();

		private final ProxyAuthentication proxyAuthentication = ProxyAuthentication.getInstance();

		ProxyAwareAuthenticator() {
		}

		@Override
		protected PasswordAuthentication getPasswordAuthentication() {

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
