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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitArtifactId}.
 *
 * @author Mark Paluch
 */
class GitArtifactIdUnitTests {

	@Test
	void createsGitArtifactWithMatchingDeclaredAndReleaseSourceCoordinates() {

		ArtifactId artifactId = ArtifactId.of("owner", "repository");

		GitArtifactId gitArtifactId = GitArtifactId.of("github.example.com", artifactId);

		assertThat(gitArtifactId.groupId()).isEqualTo("owner");
		assertThat(gitArtifactId.artifactId()).isEqualTo("repository");
		assertThat(gitArtifactId.releaseSource()).isEqualTo(artifactId);
		assertThat(gitArtifactId.toString())
				.isEqualTo("owner:repository@git://github.example.com/owner/repository.git");
	}

	@Test
	void keepsDeclaredCoordinatesSeparateFromReleaseSourceCoordinates() {

		ArtifactId declared = ArtifactId.of("@scope", "package");

		GitArtifactId gitArtifactId = GitArtifactId.of("github.com", "owner", "repository", declared);

		assertThat(gitArtifactId.groupId()).isEqualTo("@scope");
		assertThat(gitArtifactId.artifactId()).isEqualTo("package");
		assertThat(gitArtifactId.releaseSource()).isEqualTo(ArtifactId.of("owner", "repository"));
		assertThat(gitArtifactId.toString()).isEqualTo("@scope:package@git://github.com/owner/repository.git");
	}

}
