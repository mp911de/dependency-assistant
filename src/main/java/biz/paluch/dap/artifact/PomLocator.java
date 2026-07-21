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
 * Strategy for locating the POM file of an artifact on the local machine.
 *
 * <p>Implementations resolve artifact coordinates against a build-tool storage
 * such as the local Maven repository or the Gradle module cache, without
 * network access. Locating may touch the filesystem, so callers must invoke
 * locators from a background thread.
 *
 * @author Mark Paluch
 */
public interface PomLocator {

	/**
	 * Extension point for POM locators.
	 */
	ExtensionPointName<PomLocator> EP_NAME = ExtensionPointName.create("biz.paluch.dap.pomLocator");

	/**
	 * Locate the POM file for the given coordinates.
	 *
	 * @param project the project providing repository configuration.
	 * @param artifactId the artifact coordinates.
	 * @param version the artifact version.
	 * @return the POM file, or {@literal null} if this locator cannot find it.
	 */
	@Nullable
	default VirtualFile locatePom(Project project, ArtifactId artifactId, ArtifactVersion version) {
		return locatePom(project, artifactId, version.toString());
	}

	/**
	 * Locate the POM file for the given coordinates.
	 *
	 * @param project the project providing repository configuration.
	 * @param artifactId the artifact coordinates.
	 * @param version the artifact version.
	 * @return the POM file, or {@literal null} if this locator cannot find it.
	 */
	@Nullable
	VirtualFile locatePom(Project project, ArtifactId artifactId, String version);

	/**
	 * Locate the POM file through the registered locators, returning the first
	 * match in registration order.
	 *
	 * @param project the project providing repository configuration.
	 * @param artifactId the artifact coordinates.
	 * @param version the artifact version.
	 * @return the POM file, or {@literal null} if no locator finds it.
	 */
	static @Nullable VirtualFile findPom(Project project, ArtifactId artifactId, ArtifactVersion version) {
		for (PomLocator locator : EP_NAME.getExtensionList()) {
			VirtualFile virtualFile = locator.locatePom(project, artifactId, version);
			if (virtualFile != null) {
				return virtualFile;
			}
		}
		return null;
	}

	/**
	 * Locate the POM file through the registered locators, returning the first
	 * match in registration order.
	 *
	 * @param project the project providing repository configuration.
	 * @param artifactId the artifact coordinates.
	 * @param version the artifact version.
	 * @return the POM file, or {@literal null} if no locator finds it.
	 */
	static @Nullable VirtualFile findPom(Project project, ArtifactId artifactId, String version) {
		for (PomLocator locator : EP_NAME.getExtensionList()) {
			VirtualFile virtualFile = locator.locatePom(project, artifactId, version);
			if (virtualFile != null) {
				return virtualFile;
			}
		}
		return null;
	}

}
