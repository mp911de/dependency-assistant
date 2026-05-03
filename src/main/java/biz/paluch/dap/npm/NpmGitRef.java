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

package biz.paluch.dap.npm;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitRepositoryMetadata;

/**
 * Git-backed NPM dependency reference resolved through
 * {@link GitRepositoryMetadata#parseGitUrl(String)}.
 *
 * <p>The {@code prefix} is the original Git URL or shorthand text up to the
 * committish replacement point, including {@code #} and, for semver refs, the
 * {@code semver:} marker. The {@code committish} is modeled as an
 * {@link NpmVersionExpression} so tag-like refs, comparator refs, prefix refs,
 * SHAs, and branch names can share the same rendering contract. An empty
 * committish indicates that the user did not pin the dependency to a specific
 * ref; downstream resolution treats that as no concrete version.
 *
 * @param prefix the raw declaration prefix preserved when rendering an update.
 * @param repository the resolved GitHub repository metadata, including the
 * GitHub host that drives release-source routing.
 * @param committish the parsed ref expression written after {@code #}; may have
 * empty text when the user did not pin a ref.
 * @author Mark Paluch
 * @see NpmVersionExpression.Git
 */
record NpmGitRef(String prefix, GitRepositoryMetadata repository, NpmVersionExpression committish) {

	/**
	 * Render this Git reference with the given target version while preserving the
	 * original URL or shorthand prefix.
	 * @param version the target artifact version; must not be {@literal null}.
	 * @return the rendered Git dependency value.
	 */
	public String renderUpdate(ArtifactVersion version) {
		return prefix + committish.renderUpdate(version);
	}

}
