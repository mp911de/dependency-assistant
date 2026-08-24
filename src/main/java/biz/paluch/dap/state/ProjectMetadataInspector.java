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

package biz.paluch.dap.state;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageSystem;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;

/**
 * Strategy for capturing project metadata, such as its display name, source
 * repository, and issue tracker, from locally available build metadata.
 *
 * <p>Implementations inspect ecosystem-specific stores such as the local Maven
 * repository and return metadata or a nothing-found marker. The caller owns
 * staleness gating and persistence. Inspection may touch the filesystem, so
 * callers must invoke inspectors from a background thread.
 *
 * @author Mark Paluch
 */
public interface ProjectMetadataInspector {

	/**
	 * Extension point for project metadata inspectors.
	 */
	ExtensionPointName<ProjectMetadataInspector> EP_NAME = ExtensionPointName
			.create("biz.paluch.dap.projectMetadataInspector");

	/**
	 * Return whether this inspector can capture metadata for artifacts of the given
	 * ecosystem.
	 *
	 * @param packageSystem the ecosystem the artifact belongs to.
	 * @return {@code true} if {@link #inspect} understands the ecosystem.
	 */
	boolean supports(PackageSystem packageSystem);

	/**
	 * Inspect the artifact's locally available build metadata.
	 *
	 * @param project the project providing repository configuration and local build
	 * models.
	 * @param artifactId the artifact to inspect.
	 * @param version the currently used version.
	 * @param indicator the progress indicator.
	 * @return the captured metadata, including an empty nothing-found marker when
	 * inspection completed without usable metadata.
	 */
	@RequiresBackgroundThread
	CachedMetadata inspect(Project project, ArtifactId artifactId, ArtifactVersion version,
			ProgressIndicator indicator);

}
