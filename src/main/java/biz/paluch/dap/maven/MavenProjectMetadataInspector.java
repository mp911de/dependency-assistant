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

package biz.paluch.dap.maven;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.state.ProjectMetadataInspector;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

/**
 * {@link ProjectMetadataInspector} for Maven-system artifacts, delegating to
 * {@link MavenPomMetadataIntrospector} which walks the artifact's POM chain in
 * the local repository stores and persists the result.
 *
 * @author Mark Paluch
 */
public class MavenProjectMetadataInspector implements ProjectMetadataInspector {

	@Override
	public boolean supports(PackageSystem packageSystem) {
		return packageSystem == PackageSystem.MAVEN;
	}

	@Override
	public CachedMetadata inspect(Project project, ArtifactId artifactId, ArtifactVersion version,
			ProgressIndicator indicator) {
		MavenPomMetadataIntrospector introspector = new MavenPomMetadataIntrospector(project);
		return introspector.getProjectMetadata(artifactId, version, indicator);
	}

}
