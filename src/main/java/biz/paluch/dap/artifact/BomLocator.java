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

package biz.paluch.dap.artifact;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Strategy for locating the POM file of a Bill of Materials artifact on the
 * local machine.
 *
 * <p>Implementations resolve BOM coordinates against a build-tool storage such
 * as the local Maven repository or the Gradle module cache, without network
 * access. Locating may touch the filesystem, so callers must invoke locators
 * from a background thread.
 *
 * @author Mark Paluch
 * @see BillOfMaterials
 */
public interface BomLocator {

	/**
	 * Extension point for BOM locators.
	 */
	ExtensionPointName<BomLocator> EP_NAME = ExtensionPointName.create("biz.paluch.dap.bomLocator");

	/**
	 * Locate the BOM POM file for the given coordinates.
	 *
	 * @param project the project providing repository configuration.
	 * @param artifactId the BOM artifact coordinates.
	 * @param version the BOM version.
	 * @return the POM file, or {@literal null} if this locator cannot find it.
	 */
	@Nullable
	VirtualFile locateBom(Project project, ArtifactId artifactId, ArtifactVersion version);

	/**
	 * Locate the BOM POM file through the registered locators, returning the first
	 * match in registration order.
	 *
	 * @param project the project providing repository configuration.
	 * @param artifactId the BOM artifact coordinates.
	 * @param version the BOM version.
	 * @return the POM file, or {@literal null} if no locator finds it.
	 */
	static @Nullable VirtualFile findBom(Project project, ArtifactId artifactId, ArtifactVersion version) {
		for (BomLocator locator : EP_NAME.getExtensionList()) {
			VirtualFile virtualFile = locator.locateBom(project, artifactId, version);
			if (virtualFile != null) {
				return virtualFile;
			}
		}
		return null;
	}

}
