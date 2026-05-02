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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Artifact identity for a dependency whose versions are resolved from a Git
 * repository.
 *
 * <p>The declared coordinates remain the public {@link ArtifactId} contract:
 * {@link #groupId()} and {@link #artifactId()} return the dependency identity
 * as it appears in the build file. The {@linkplain #releaseSource() release
 * source} identifies the Git repository queried for tags and releases, and
 * {@link #host()} selects the Git hosting endpoint.
 *
 * <p>This split is needed for ecosystems such as NPM, where a package can be
 * declared under one name while its version candidates come from a {@code git+}
 * URL that points to a different owner/repository. Caches, declarations, and UI
 * grouping should generally use the declared coordinates; Git release sources
 * should use {@link #host()} and {@link #releaseSource()}.
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
	 * Create a Git-backed artifact whose declared coordinates and release-source
	 * coordinates are the same.
	 * @param host the Git host used for release lookup
	 * @param artifactId the declared artifact coordinates
	 * @return the Git-backed artifact identity
	 */
	public static GitArtifactId of(String host, ArtifactId artifactId) {
		return of(host, artifactId.groupId(), artifactId.artifactId());
	}

	/**
	 * Create a Git-backed artifact for a repository that also represents the
	 * declared dependency identity.
	 * @param host the Git host used for release lookup
	 * @param owner the repository owner
	 * @param repository the repository name
	 * @return the Git-backed artifact identity
	 */
	public static GitArtifactId of(String host, String owner, String repository) {
		return new GitArtifactId(host, ArtifactId.of(owner, repository), ArtifactId.of(owner, repository));
	}

	/**
	 * Create a Git-backed artifact with declared coordinates that may differ from
	 * the repository used for release lookup.
	 * @param host the Git host used for release lookup
	 * @param owner the repository owner
	 * @param repository the repository name
	 * @param originalArtifactId the dependency identity declared in the build file
	 * @return the Git-backed artifact identity
	 */
	public static GitArtifactId of(String host, String owner, String repository, ArtifactId originalArtifactId) {
		return new GitArtifactId(host, originalArtifactId, ArtifactId.of(owner, repository));
	}

	/**
	 * Return the declared group id, which is not necessarily the Git repository
	 * owner.
	 */
	@Override
	public String groupId() {
		return declared.groupId();
	}

	/**
	 * Return the declared artifact id, which is not necessarily the Git repository
	 * name.
	 */
	@Override
	public String artifactId() {
		return declared.artifactId();
	}

	/**
	 * Return the Git host used for release lookup.
	 */
	public String host() {
		return host;
	}

	/**
	 * Return the repository coordinates to use when querying Git tags and releases.
	 */
	public ArtifactId releaseSource() {
		return releaseSource;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (o == null || getClass() != o.getClass())
			return false;
		GitArtifactId that = (GitArtifactId) o;
		return Objects.equals(host, that.host) && Objects.equals(declared, that.declared)
				&& Objects.equals(releaseSource, that.releaseSource);
	}

	@Override
	public int hashCode() {
		return Objects.hash(host, declared, releaseSource);
	}

	@Override
	public String toString() {
		return "%s@git://%s/%s/%s.git".formatted(declared, host, releaseSource.groupId(), releaseSource.artifactId());
	}

}
