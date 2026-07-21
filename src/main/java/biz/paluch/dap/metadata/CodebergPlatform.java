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
 * Recognizes Codeberg ({@code codeberg.org}) only. URL patterns coincide with
 * GitHub's (issues {@code /issues}, releases {@code /releases}, release notes
 * {@code /releases/tag/{tag}}), yet the platforms stay independent: Forgejo
 * evolves its URLs separately. Codeberg is a flat {@code owner/repo} host, so
 * coordinates are minted from the first two path segments via
 * {@link GitRepositoryMetadata#flat(RemoteUrl)}.
 *
 * @author Mark Paluch
 */
public class CodebergPlatform extends PlatformSupport {

	/**
	 * user and organization names: start with an alphanumeric, hyphen, underscore,
	 * and dot only singly between alphanumerics.
	 */
	private static final Pattern OWNER = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9]|[-._](?=[A-Za-z0-9]))*");

	/**
	 * repository names: alphanumerics, hyphen, underscore, and dot, but neither
	 * consecutive dots nor a name of separators only ({@code .}, {@code ..}, and
	 * {@code -} are reserved).
	 */
	private static final Pattern REPOSITORY = Pattern.compile("(?![-.]+$)(?!.*\\.\\.)[A-Za-z0-9._-]+");

	public CodebergPlatform() {
		super("codeberg", "releases", "releases/tag", "issues");
	}

	@Override
	public @Nullable RepositoryConnection detect(RepositoryUrl repositoryUrl, @Nullable String hint) {

		if (repositoryUrl.getType() != RepositoryType.GIT) {
			return null;
		}
		GitRepositoryMetadata metadata = GitRepositoryMetadata.flat(repositoryUrl.getRemote());
		if (metadata != null && isCodebergHost(metadata.host()) && OWNER.matcher(metadata.owner()).matches()
				&& REPOSITORY.matcher(metadata.repository()).matches()) {
			return new SimpleRepositoryConnection(this, metadata.key(), "https://" + metadata.key());
		}

		return null;
	}

	private static boolean isCodebergHost(String host) {

		String name = host.toLowerCase(Locale.ROOT);
		return name.equals("codeberg.org") || name.equals("www.codeberg.org");
	}

}
