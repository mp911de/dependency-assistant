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

package biz.paluch.dap.rule;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Versioned;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Resolver for {@link DependencyRule}s.
 *
 * @author Mark Paluch
 * @see DependencyfileService
 */
public interface DependencyRuleService {

	/**
	 * Return a resolver that resolves every artifact to
	 * {@link DependencyRule#absent()}.
	 *
	 * @return the absent rule resolver.
	 */
	static DependencyRuleService absent() {
		return AbsentDependencyRuleService.INSTANCE;
	}

	/**
	 * Resolve an effective {@link DependencyRule} for the given {@link ArtifactId}
	 * and {@link Versioned} project version.
	 *
	 * @param artifactId the artifact to resolve a rule for.
	 * @param file the file used to detect the active branch; can be {@literal null}
	 * if no branch context is available.
	 * @param projectVersion the project version used to select branch rules.
	 * @return the governing dependency rule, or {@link DependencyRule#absent()}
	 * when no rule applies.
	 */
	DependencyRule resolve(ArtifactId artifactId, @Nullable VirtualFile file, Versioned projectVersion);

	/**
	 * Resolve the dependency rule for the given artifact using an explicit branch
	 * name instead of VCS branch detection.
	 *
	 * @param artifactId the artifact to resolve a rule for.
	 * @param branchName the current branch name, can be {@literal null} if the
	 * project is not versioned.
	 * @param projectVersion the project version, can be {@literal null} if not
	 * provided.
	 * @return the governing dependency rule, or {@link DependencyRule#absent()}
	 * when no rule applies.
	 * @see #resolve(ArtifactId, VirtualFile, Versioned)
	 */
	DependencyRule resolve(ArtifactId artifactId, @Nullable String branchName,
			@Nullable ArtifactVersion projectVersion);

}
