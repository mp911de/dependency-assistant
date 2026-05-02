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

import biz.paluch.dap.artifact.GitRepositoryMetadata;

/**
 * Git-backed NPM dependency reference resolved through
 * {@link GitRepositoryMetadata#parseGitUrl(String)}.
 *
 * <p>The committish is the substring after the trailing {@code #} marker in the
 * NPM Git URL, with any {@code semver:} prefix already stripped by the parser.
 * An empty committish indicates that the user did not pin the dependency to a
 * specific ref; downstream resolution treats that as no concrete version.
 *
 * @param repository the resolved GitHub repository metadata, including the
 * GitHub host that drives release-source routing.
 * @param committish the version reference written after {@code #}; can be empty
 * when the user did not pin a ref.
 * @author Mark Paluch
 * @see NpmVersionExpression.Git
 */
record NpmGitRef(GitRepositoryMetadata repository, String committish) {

}
