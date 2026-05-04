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
 * Unit tests for {@link GitVersion}.
 *
 * @author Mark Paluch
 */
class GitVersionUnitTests {

	private static final String SHA = "d1185ce59f7757407fe6a5febb1e03e3dba2a530";

	@Test
	void rendersTagForVersionStyle() {
		GitVersion version = GitVersion.of(SHA, ArtifactVersion.of("v4.2.0"));
		assertThat(version.renderRef(RefStyle.VERSION, "v4.1.0")).isEqualTo("v4.2.0");
	}

	@Test
	void rendersTagForVersionStyleEvenWithoutSha() {
		GitVersion version = GitVersion.of(ArtifactVersion.of("v4.2.0"));
		assertThat(version.renderRef(RefStyle.VERSION, "v4.1.0")).isEqualTo("v4.2.0");
	}

	@Test
	void rendersFullShaForShaStyleAndFullCommittish() {
		GitVersion version = GitVersion.of(SHA, ArtifactVersion.of("v4.2.0"));
		assertThat(version.renderRef(RefStyle.SHA, "abcdef1234567890abcdef1234567890abcdef12"))
				.isEqualTo(SHA);
	}

	@Test
	void truncatesShaToOriginalCommittishLength() {
		GitVersion version = GitVersion.of(SHA, ArtifactVersion.of("v4.2.0"));
		assertThat(version.renderRef(RefStyle.SHA, "7b4f3880")).isEqualTo("d1185ce5");
	}

	@Test
	void rendersFullShaWhenOriginalCommittishLongerThanSha() {
		GitVersion version = GitVersion.of(SHA, ArtifactVersion.of("v4.2.0"));
		String longer = SHA + "extra";
		assertThat(version.renderRef(RefStyle.SHA, longer)).isEqualTo(SHA);
	}

	@Test
	void rendersFullShaWhenOriginalCommittishNull() {
		GitVersion version = GitVersion.of(SHA, ArtifactVersion.of("v4.2.0"));
		assertThat(version.renderRef(RefStyle.SHA, null)).isEqualTo(SHA);
	}

	@Test
	void rendersTagWhenNoShaAvailableForShaStyle() {
		GitVersion version = GitVersion.of(ArtifactVersion.of("v4.2.0"));
		assertThat(version.renderRef(RefStyle.SHA, "7b4f3880")).isEqualTo("v4.2.0");
	}

	@Test
	void rendersFullShaWhenOriginalCommittishEmpty() {
		GitVersion version = GitVersion.of(SHA, ArtifactVersion.of("v4.2.0"));
		assertThat(version.renderRef(RefStyle.SHA, "")).isEqualTo(SHA);
	}

}
