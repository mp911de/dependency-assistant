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

package biz.paluch.dap.assistant.documentation;

import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.HasPackageIdentity;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Documentation target supporting version updates and upgrade review.
 *
 * @author Mark Paluch
 * @see DependencyUpgradeLinkHandler
 */
interface DependencyUpgradeTarget extends HasArtifactId, HasPackageIdentity {

	Project getProject();

	/**
	 * Return the declaration file, or {@literal null} if no longer live.
	 * <p>The caller must hold a read action.
	 */
	@Nullable
	PsiFile getDeclarationFile();

	/**
	 * Apply the version shown in the documentation.
	 * <p>The caller owns the write action. A declaration that is no longer live is
	 * left unchanged.
	 */
	void applyVersion(String version);


}
