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

package biz.paluch.dap.antora;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Parsed Antora {@code ui.bundle.url} declaration.
 * <p>Accepts
 * {@code http[s]://host/owner/repository/releases/download/version/asset}
 * without query strings or fragments. The host may include a numeric port. The
 * version may be empty so completion can populate it.
 * <p>The host routes release lookup to the corresponding GitHub server.
 *
 * @author Mark Paluch
 */
record AntoraBundleUrl(String host, String owner, String repository, String version) {

	private static final Pattern URL = Pattern.compile(
			"^https?://(?<host>[A-Za-z0-9._-]+(?::[0-9]+)?)/(?<owner>[A-Za-z0-9._-]+)/(?<repository>[A-Za-z0-9._-]+)"
					+ "/releases/download/(?<version>[A-Za-z0-9._+%-]*)/(?<asset>[A-Za-z0-9._/-]+)$");

	/**
	 * Parse a release asset URL, or return {@literal null} if absent or
	 * unsupported.
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

	ArtifactId toArtifactId() {
		return GitArtifactId.of(host, owner, repository);
	}

	/**
	 * Return the declared version source, or {@link VersionSource#none()} for an
	 * empty version.
	 */
	VersionSource toVersionSource() {
		return VersionSource.from(version());
	}

}
