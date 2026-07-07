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

import biz.paluch.dap.artifact.ArtifactId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitHubWorkflowParser#parseUses(String)}.
 *
 * @author Mark Paluch
 */
class GitHubWorkflowParserUnitTests {

	@Test
	void parsesVersionRef() {

		UsesRepositoryAction ref = GitHubWorkflowParser.parseUses("actions/checkout@v4.2.0");

		assertThat(ref).isNotNull();
		assertThat(ref.owner()).isEqualTo("actions");
		assertThat(ref.repository()).isEqualTo("checkout");
		assertThat(ref.version()).isEqualTo("v4.2.0");
	}

	@Test
	void parsesVersionRefWithoutVPrefix() {

		UsesRepositoryAction ref = GitHubWorkflowParser.parseUses("actions/checkout@4.2.0");

		assertThat(ref).isNotNull();
		assertThat(ref.version()).isEqualTo("4.2.0");
	}

	@Test
	void parsesShaRef() {

		String sha = "aabbccddeeff001122334455667788990011aabb";
		UsesRepositoryAction ref = GitHubWorkflowParser
				.parseUses("actions/checkout@aabbccddeeff001122334455667788990011aabb");

		assertThat(ref).isNotNull();
		assertThat(ref.version()).isEqualTo(sha);
	}

	@Test
	void parsesActionPath() {

		UsesRepositoryAction ref = GitHubWorkflowParser
				.parseUses("actions/aws-actions/configure-aws-credentials@v4");

		assertThat(ref).isNotNull();
		assertThat(ref.owner()).isEqualTo("actions");
		assertThat(ref.repository()).isEqualTo("aws-actions");
		assertThat(ref.version()).isEqualTo("v4");
	}

	@Test
	void toArtifactIdUsesGithubPrefix() {

		UsesRepositoryAction ref = GitHubWorkflowParser.parseUses("actions/checkout@v4");

		assertThat(ref).isNotNull();
		assertThat(ref.toArtifactId()).isEqualTo(ArtifactId.of("actions", "checkout"));
		assertThat(ref.toArtifactId()).isEqualTo(GitHubAction.of("actions", "checkout"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"./local-action@v1",
			"../sibling@v1",
			"docker://ghcr.io/owner/image:tag",
			"",
			"   ",
			"missing-at-sign",
			"owner@v1",
	})
	void ignoresInvalidOrLocalRefs(String input) {
		assertThat(GitHubWorkflowParser.parseUses(input)).isNull();
	}

}
