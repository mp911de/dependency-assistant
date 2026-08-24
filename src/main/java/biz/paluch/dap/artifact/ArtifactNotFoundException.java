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

/**
 * Thrown when a release source definitively cannot resolve the requested
 * artifact, for example because its coordinates are invalid or the upstream
 * repository reports that the artifact is absent.
 *
 * <p>The unresolved coordinates are available through {@link #getArtifactId()}.
 *
 * @author Mark Paluch
 */
public class ArtifactNotFoundException extends RuntimeException {

	private final ArtifactId artifactId;

	/**
	 * Create a new {@code ArtifactNotFoundException} for the missing artifact.
	 *
	 * @param message the failure detail.
	 * @param artifactId the artifact that could not be resolved.
	 */
	public ArtifactNotFoundException(String message, ArtifactId artifactId) {
		super(message);
		this.artifactId = artifactId;
	}

	/**
	 * Return the artifact that could not be resolved.
	 *
	 * @return the unresolved artifact coordinates.
	 */
	public ArtifactId getArtifactId() {
		return artifactId;
	}

}
