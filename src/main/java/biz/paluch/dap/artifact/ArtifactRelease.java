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

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * An artifact release consisting of an {@link ArtifactId} and a
 * {@link Release}.
 *
 * @param artifactId the released artifact.
 * @param release the release metadata.
 */
public record ArtifactRelease(ArtifactId artifactId,
		Release release) implements Comparable<ArtifactRelease>, VersionAware {

	@Override
	public int compareTo(ArtifactRelease o) {
		return release.compareTo(o.release());
	}

	/**
	 * Return whether this release is newer than the given current version.
	 */
	public boolean isNewer(ArtifactVersion currentVersion) {
		return release.isNewer(currentVersion);
	}

	/**
	 * Return whether this release is older than the given current version.
	 */
	public boolean isOlder(ArtifactVersion currentVersion) {
		return release.isOlder(currentVersion);
	}

	@Override
	public ArtifactVersion getVersion() {
		return release.version();
	}

	public @Nullable LocalDateTime getReleaseDate() {
		return release.releaseDate();
	}

}
