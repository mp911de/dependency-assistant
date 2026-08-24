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
 * Documentation target that can apply a dependency version upgrade in response
 * to a documentation link click.
 *
 * @author Mark Paluch
 * @see DependencyUpgradeLinkHandler
 */
interface DependencyUpgradeTarget extends HasArtifactId, HasPackageIdentity {

	/**
	 * Return the project owning the documented declaration.
	 *
	 * @return the project containing the declaration.
	 */
	Project getProject();

	/**
	 * Return the build file containing the documented declaration, used to scope a
	 * Dependency Check launched from a documentation link.
	 *
	 * <p>Must be called inside a read action.
	 *
	 * @return the declaring file, or {@literal null} when the declaration is no
	 * longer live.
	 */
	@Nullable
	PsiFile getDeclarationFile();

	/**
	 * Apply the given version to the documented declaration.
	 *
	 * <p>Must be called inside a write action. The implementation rewrites the
	 * version literal in place through the shared update path and never opens its
	 * own write action. Resolution that no longer matches a live declaration is a
	 * no-op.
	 *
	 * @param version the target version to apply, as rendered in the documentation
	 * table.
	 */
	void applyVersion(String version);


}
