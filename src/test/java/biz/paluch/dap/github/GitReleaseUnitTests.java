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

package biz.paluch.dap.github;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitRelease}.
 *
 * @author Mark Paluch
 */
class GitReleaseUnitTests {

	private static final String SHA = "abc123def456abc123def456abc123def456abc1";

	@Test
	void toGitVersionWithShaAttachesSha() {

		ArtifactVersion version = ArtifactVersion.of("4.2.0");
		GitRelease release = new GitRelease(Release.of(version), SHA);

		GitVersion gitVersion = release.toGitVersion();

		assertThat(gitVersion.getSha()).isEqualTo(SHA);
		assertThat(gitVersion.getVersion()).isEqualTo(version);
	}

	@Test
	void toGitVersionWithoutShaProducesNoSha() {

		ArtifactVersion version = ArtifactVersion.of("3.6.0");
		GitRelease release = new GitRelease(Release.of(version), null);

		GitVersion gitVersion = release.toGitVersion();

		assertThat(gitVersion.getSha()).isNull();
		assertThat(gitVersion.getVersion()).isEqualTo(version);
	}

}
