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

package biz.paluch.dap.artifact;

import java.io.IOException;

import biz.paluch.dap.gradle.GradlePluginPortalReleaseSource;
import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * Strategy for obtaining known releases for an artifact.
 *
 * <p>Adapters translate one upstream registry or service into {@link Release}
 * values. Routing implementations may translate the artifact coordinates or
 * select another release source before fetching.
 *
 * <p>Throw {@link ArtifactNotFoundException} only for a definitive absence at
 * this source. Return an empty sequence when release data is simply
 * unavailable.
 *
 * @author Mark Paluch
 * @see MavenRepository
 * @see GradlePluginPortalReleaseSource
 */
public interface ReleaseSource {

	/**
	 * Return the source identifier.
	 */
	default String getId() {
		return getClass().getSimpleName();
	}

	/**
	 * Fetch known releases, which may be unsorted and include previews or
	 * snapshots.
	 * <p>Implementations should check the indicator during long-running fetches.
	 * @throws ArtifactNotFoundException if the artifact is definitively absent.
	 * @throws IOException if release data cannot be read from the source.
	 */
	Sequence<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException;

	/**
	 * Render coordinates for this source.
	 */
	default String toString(ArtifactId artifactId) {
		return artifactId.toString();
	}

	static ReleaseSource mavenCentral() {
		return MavenRepository.MAVEN_CENTRAL;
	}

}
