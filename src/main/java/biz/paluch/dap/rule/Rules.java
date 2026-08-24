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

package biz.paluch.dap.rule;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import org.jspecify.annotations.Nullable;

/**
 * Package-level resolution view over parsed Dependency Rules.
 *
 * <p>A view resolves the governing {@link DependencyRule} for a single artifact
 * and branch context. The resolved rule can be {@link DependencyRule#absent()
 * absent}, meaning no governance applies.
 *
 * @author Mark Paluch
 * @see DependencyRules
 */
interface Rules {

	/**
	 * Return an empty {@code Rules} instance.
	 *
	 * @return the absent resolution view.
	 */
	static Rules absent() {
		return (a, b, c) -> DependencyRule.absent();
	}

	/**
	 * Resolve the {@link DependencyRule} for the given {@link ArtifactId} and
	 * context.
	 *
	 * @param artifactId the artifact to resolve a rule for.
	 * @param branchName the active branch name matched against branch rules, or
	 * {@literal null} when unavailable.
	 * @param projectVersion the project version, used to select a branch rule when
	 * no branch name matches, or {@literal null} when unavailable.
	 * @return the governing dependency rule, or {@link DependencyRule#absent()}
	 * when no rule applies.
	 */
	DependencyRule resolve(ArtifactId artifactId, @Nullable String branchName,
			@Nullable ArtifactVersion projectVersion);

}
