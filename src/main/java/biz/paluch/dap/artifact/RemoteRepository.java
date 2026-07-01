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

package biz.paluch.dap.artifact;

import java.net.URI;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * A remote Maven repository with an optional set of HTTP Basic-auth credentials resolved from {@code settings.xml}.
 *
 * @author Mark Paluch
 */
public final class RemoteRepository {

	static final RemoteRepository MAVEN_CENTRAL = new RemoteRepository("central", "https://repo1.maven.org/maven2/",
			null);

	private final String id;

	private final String url;

	private final URI uri;

	private final @Nullable RepositoryCredentials credentials;


	public RemoteRepository(String id, String url, @Nullable RepositoryCredentials credentials) {
		Assert.hasText(id, "Id must not be null or empty!");
		Assert.hasText(url, "URL must not be null or empty!");

		if (!url.endsWith("/")) {
			url = url + "/";
		}
		this.id = id;
		this.url = url;
		this.uri = URI.create(url);
		this.credentials = credentials;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RemoteRepository that)) {
			return false;
		}
		if (!ObjectUtils.nullSafeEquals(url, that.url)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(credentials, that.credentials);
	}

	public String id() {
		return id;
	}

	public String url() {
		return url;
	}

	public URI uri() {
		return uri;
	}

	public @Nullable RepositoryCredentials credentials() {
		return credentials;
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(url, credentials);
	}

	/**
	 * Return the Maven Central repository descriptor.
	 */
	public static RemoteRepository mavenCentral() {
		return MAVEN_CENTRAL;
	}

	@Override
	public String toString() {
		return "Repository " + id + " [" + url + "]";
	}

}
