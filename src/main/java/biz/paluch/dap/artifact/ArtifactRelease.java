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

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * An artifact and its release metadata.
 * <p>Natural ordering is newest-first and ignores artifact coordinates. Use an
 * artifact-aware key when sorting releases from different artifacts.
 */
public record ArtifactRelease(ArtifactId artifactId,
		Release release) implements Comparable<ArtifactRelease>, VersionAware {

	@Override
	public int compareTo(ArtifactRelease o) {

		// newest version first.
		return o.release().compareTo(release);
	}

	public boolean isNewer(ArtifactVersion currentVersion) {
		return release.isNewer(currentVersion);
	}

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
