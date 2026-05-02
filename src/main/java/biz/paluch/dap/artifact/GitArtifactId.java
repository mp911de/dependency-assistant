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
 * Git-served artifact.
 * 
 * @author Mark Paluch
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

	public static GitArtifactId of(String host, ArtifactId artifactId) {
		return of(host, artifactId.groupId(), artifactId.groupId());
	}

	public static GitArtifactId of(String host, String owner, String repository) {
		return new GitArtifactId(host, ArtifactId.of(owner, repository), ArtifactId.of(owner, repository));
	}

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
		return "%s@git://%s/%s/%s.git".formatted(declared, host, releaseSource.groupId(), declared.artifactId());
	}

}
