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

import java.io.IOException;
import java.util.List;

import biz.paluch.dap.gradle.GradlePluginPortalReleaseSource;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * Strategy interface for obtaining known releases for an artifact.
 *
 * <p>Implementations typically adapt one registry, such as a remote Maven
 * repository or the Gradle Plugin Portal.
 *
 * <p>Throw {@link ArtifactNotFoundException} only for a definitive absence at
 * this source. Return an empty list when release data is simply unavailable.
 *
 * @author Mark Paluch
 * @see MavenRepository
 * @see GradlePluginPortalReleaseSource
 */
public interface ReleaseSource {

	/**
	 * Return the unique identifier of this source.
	 */
	default String getId() {
		return getClass().getSimpleName();
	}

	/**
	 * Return all known releases for the given artifact at this source.
	 * <p>
	 * The returned list may be unsorted and may contain release, preview, and
	 * snapshot versions. Implementations should periodically call
	 * {@link ProgressIndicator#checkCanceled()} during long-running fetches to
	 * honor user cancellation.
	 * @param artifactId the artifact whose releases to retrieve.
	 * @param indicator the progress indicator used to honor cancellation; must not
	 * be {@literal null}.
	 * @return the releases known to this source.
	 * @throws ArtifactNotFoundException if the artifact is definitively absent.
	 */
	List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException;

	/**
	 * Render the artifact coordinates as a human-readable string.
	 * @param artifactId the artifact coordinates.
	 * @return
	 */
	default String toString(ArtifactId artifactId) {
		return artifactId.toString();
	}

	/**
	 * Return the built-in {@link ReleaseSource} backed by Maven Central.
	 */
	static ReleaseSource mavenCentral() {
		return MavenRepository.MAVEN_CENTRAL;
	}

}
