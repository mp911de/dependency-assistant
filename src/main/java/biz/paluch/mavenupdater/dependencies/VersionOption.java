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
package biz.paluch.mavenupdater.dependencies;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

/**
 * A version string with optional release date from Maven metadata (for display in version dropdown).
 */
public class VersionOption implements Comparable<VersionOption> {

	private final ArtifactVersion version;

	private final @Nullable LocalDateTime releaseDate;

	public VersionOption(ArtifactVersion version, @Nullable LocalDateTime releaseDate) {
		this.version = version;
		this.releaseDate = releaseDate;
	}

	public ArtifactVersion getVersion() {
		return version;
	}

	public @Nullable LocalDateTime getReleaseDate() {
		return releaseDate;
	}

	@Override
	public int compareTo(VersionOption o) {
		return version.compareTo(o.version);
	}

	@Override
	public String toString() {
		return version.toString();
	}

}
