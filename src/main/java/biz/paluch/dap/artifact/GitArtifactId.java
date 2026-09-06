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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Declared artifact coordinates with separate Git release-routing metadata.
 * <p>An npm package name can differ from the repository supplying its versions.
 * Use the declared coordinates for dependency identity and grouping. Use
 * {@link #host()} and {@link #releaseSource()} for Git release lookup.
 *
 * @author Mark Paluch
 * @see GitRepositoryMetadata
 */
public class GitArtifactId implements ArtifactId {

	private final String host;

	private final ArtifactId declared;

	private final ArtifactId releaseSource;

	private GitArtifactId(String host, ArtifactId declared, ArtifactId releaseSource) {
		this.host = host;
		this.declared = declared;
		this.releaseSource = releaseSource;
	}

	/**
	 * Use the same coordinates for dependency identity and release lookup.
	 */
	public static GitArtifactId of(String host, ArtifactId artifactId) {
		return of(host, artifactId.groupId(), artifactId.artifactId());
	}

	/**
	 * Use the repository coordinates for dependency identity and release lookup.
	 */
	public static GitArtifactId of(String host, String owner, String repository) {
		return new GitArtifactId(host, ArtifactId.of(owner, repository), ArtifactId.of(owner, repository));
	}

	/**
	 * Use separate declared coordinates and Git repository coordinates.
	 * @param originalArtifactId the dependency identity declared in the build file.
	 */
	public static GitArtifactId of(String host, String owner, String repository, ArtifactId originalArtifactId) {
		return new GitArtifactId(host, originalArtifactId, ArtifactId.of(owner, repository));
	}

	@Override
	public String groupId() {
		return declared.groupId();
	}

	@Override
	public String artifactId() {
		return declared.artifactId();
	}

	public String host() {
		return host;
	}

	/**
	 * Return the repository coordinates used for Git release lookup.
	 */
	public ArtifactId releaseSource() {
		return releaseSource;
	}

	@Override
	public boolean equals(@Nullable Object o) {

		if (o instanceof GitArtifactId that) {
			return Objects.equals(host, that.host) && Objects.equals(declared, that.declared)
					&& Objects.equals(releaseSource, that.releaseSource);
		}

		if (o instanceof ArtifactId that) {
			return declared.equals(that);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return declared.hashCode();
	}

	@Override
	public String toString() {
		return "%s@git://%s/%s/%s.git".formatted(declared, host, releaseSource.groupId(), releaseSource.artifactId());
	}

}
