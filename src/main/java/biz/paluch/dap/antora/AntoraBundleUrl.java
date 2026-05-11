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

package biz.paluch.dap.antora;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Parsed Antora playbook {@code ui.bundle.url} declaration.
 *
 * <p>An Antora UI bundle URL points to a Git-hosted release asset of the form
 * {@code https://{host}/{owner}/{repository}/releases/download/{version}/{asset}}.
 * This record exposes those segments so callers can route lookups to a
 * Git-backed release source and treat the version segment as the declared
 * dependency version.
 *
 * <p>The owner and repository segments are validated against an allowlist to
 * prevent path traversal or query injection when the URL is interpolated into
 * downstream REST calls.
 *
 * @param host the Git host serving the release asset.
 * @param owner the repository owner.
 * @param repository the repository name.
 * @param version the version segment between {@code /releases/download/} and
 * the next path separator.
 *
 * @author Mark Paluch
 */
record AntoraBundleUrl(String host, String owner, String repository, String version) {

	private static final Pattern URL = Pattern.compile(
			"^https?://(?<host>[A-Za-z0-9._-]+(?::[0-9]+)?)/(?<owner>[A-Za-z0-9._-]+)/(?<repository>[A-Za-z0-9._-]+)"
					+ "/releases/download/(?<version>[A-Za-z0-9._+%-]*)/(?<asset>[A-Za-z0-9._/-]+)$");

	/**
	 * Parse the given URL string into an {@code AntoraBundleUrl}.
	 * @param url the URL to parse; can be {@literal null}.
	 * @return the parsed bundle URL, or {@literal null} if the input is blank or
	 * does not match the expected release-asset shape.
	 */
	static @Nullable AntoraBundleUrl from(@Nullable String url) {

		if (!StringUtils.hasText(url)) {
			return null;
		}

		Matcher matcher = URL.matcher(url);
		if (!matcher.matches()) {
			return null;
		}

		return new AntoraBundleUrl(matcher.group("host"), matcher.group("owner"), matcher.group("repository"),
				matcher.group("version"));
	}

	/**
	 * Return the Git-backed artifact identity for this bundle URL.
	 */
	ArtifactId toArtifactId() {
		return GitArtifactId.of(host, owner, repository);
	}

	/**
	 * Return the declared version source for this bundle URL.
	 */
	VersionSource toVersionSource() {
		return StringUtils.hasText(version) ? VersionSource.declared(version) : VersionSource.none();
	}

}
