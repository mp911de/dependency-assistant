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

package biz.paluch.dap.metadata;

import java.util.Locale;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.artifact.RemoteUrl;
import org.jspecify.annotations.Nullable;

/**
 * Recognizes Bitbucket Cloud ({@code bitbucket.org}) only; self-hosted
 * Bitbucket Server uses different URL shapes and is not supported. Bitbucket is
 * a flat {@code workspace/repo} host, so coordinates are minted from the first
 * two path segments via {@link GitRepositoryMetadata#flat(RemoteUrl)}.
 *
 * <p>Bitbucket has no releases concept. The downloads page {@code /downloads}
 * is the closest browsable listing and {@code /commits/tag/{tag}} serves as the
 * per-tag page. The issue tracker is frequently disabled per repository, so no
 * tracker is derived.
 *
 * @author Mark Paluch
 */
public class BitbucketPlatform extends PlatformSupport {

	/**
	 * Bitbucket workspace IDs: alphanumerics, hyphen, and underscore, starting with
	 * an alphanumeric. Canonically lowercase; case is tolerated because Bitbucket
	 * redirects.
	 */
	private static final Pattern WORKSPACE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

	/**
	 * Bitbucket repository slugs: alphanumerics, hyphen, underscore, and dot,
	 * starting with an alphanumeric, at most 62 characters.
	 */
	private static final Pattern REPOSITORY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,61}");

	public BitbucketPlatform() {
		super(null, "downloads", "commits/tag", null);
	}

	@Override
	public @Nullable RepositoryConnection detect(RepositoryUrl repositoryUrl, @Nullable String hint) {

		if (repositoryUrl.getType() != RepositoryType.GIT) {
			return null;
		}

		GitRepositoryMetadata metadata = GitRepositoryMetadata.flat(repositoryUrl.getRemote());
		if (metadata == null) {
			return null;
		}

		if (!isBitbucketHost(metadata.host()
				.toLowerCase(Locale.ROOT)) || !WORKSPACE.matcher(metadata.owner())
						.matches()
				|| !REPOSITORY.matcher(metadata.repository()).matches()) {
			return null;
		}
		return new SimpleRepositoryConnection(this, metadata.key(), "https://" + metadata.key());

	}

	private static boolean isBitbucketHost(String host) {
		return host.equals("bitbucket.org") || host.equals("www.bitbucket.org");
	}

}
