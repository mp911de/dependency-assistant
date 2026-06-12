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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * {@link DependencyRuleService} resolving every artifact to
 * {@link DependencyRule#absent()}.
 *
 * @author Mark Paluch
 * @see DependencyRuleService#absent()
 */
enum AbsentDependencyRuleService implements DependencyRuleService {

	INSTANCE;

	@Override
	public DependencyRule resolve(ArtifactId artifactId, Project project, @Nullable VirtualFile file,
			Versioned projectVersion) {
		return DependencyRule.absent();
	}

	@Override
	public DependencyRule resolve(ArtifactId artifactId, @Nullable String branchName,
			@Nullable ArtifactVersion projectVersion) {
		return DependencyRule.absent();
	}

}
