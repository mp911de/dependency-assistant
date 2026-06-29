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

package biz.paluch.dap.antora;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.VersionSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AntoraBundleUrl}.
 *
 * @author Mark Paluch
 */
class AntoraBundleUrlUnitTests {

	@Test
	void parsesWellFormedUrl() {

		AntoraBundleUrl url = AntoraBundleUrl
				.from("https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip");

		assertThat(url).isNotNull();
		assertThat(url.host()).isEqualTo("github.com");
		assertThat(url.owner()).isEqualTo("spring-io");
		assertThat(url.repository()).isEqualTo("antora-ui-spring");
		assertThat(url.version()).isEqualTo("v0.4.26");
	}

	@Test
	void parsesNestedAssetPath() {

		AntoraBundleUrl url = AntoraBundleUrl
				.from("https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/bundle/ui.zip");

		assertThat(url).isNotNull();
		assertThat(url.version()).isEqualTo("v0.4.26");
	}

	@Test
	void parsesHttpScheme() {

		AntoraBundleUrl url = AntoraBundleUrl
				.from("http://example.org/owner/repo/releases/download/1.2.3/ui-bundle.zip");

		assertThat(url).isNotNull();
		assertThat(url.host()).isEqualTo("example.org");
	}

	@Test
	void parsesHostWithPort() {

		AntoraBundleUrl url = AntoraBundleUrl
				.from("https://git.example.com:8443/owner/repo/releases/download/v1.2.3/ui-bundle.zip");

		assertThat(url).isNotNull();
		assertThat(url.host()).isEqualTo("git.example.com:8443");
		assertThat(url.owner()).isEqualTo("owner");
		assertThat(url.repository()).isEqualTo("repo");
		assertThat(url.version()).isEqualTo("v1.2.3");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"",
			"   ",
			"ftp://github.com/o/r/releases/download/v1/ui.zip",
			"https://github.com/spring-io/antora-ui-spring/releases/v0.4.26/ui-bundle.zip",
			"https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26",
			"https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/",
			"https://github.com/owner with space/repo/releases/download/v1/ui.zip",
			"https://github.com/owner/repo?query/releases/download/v1/ui.zip",
			"https://github.com/owner/repo/releases/download/v1/ui.zip?token=abc",
			"https://github.com/owner/repo/releases/download/v1/ui.zip#fragment",
	})
	void rejectsMalformedInput(String input) {
		assertThat(AntoraBundleUrl.from(input)).isNull();
	}

	@Test
	void rejectsNullInput() {
		assertThat(AntoraBundleUrl.from(null)).isNull();
	}

	@Test
	void toArtifactIdReturnsGitArtifactId() {

		AntoraBundleUrl url = AntoraBundleUrl
				.from("https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip");

		assertThat(url).isNotNull();
		assertThat(url.toArtifactId()).isEqualTo(ArtifactId.of("spring-io", "antora-ui-spring"));
		assertThat(url.toArtifactId()).isEqualTo(GitArtifactId.of("github.com", "spring-io", "antora-ui-spring"));
	}

	@Test
	void toVersionSourceReturnsDeclared() {

		AntoraBundleUrl url = AntoraBundleUrl
				.from("https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip");

		assertThat(url).isNotNull();
		assertThat(url.toVersionSource()).isEqualTo(VersionSource.declared("v0.4.26"));
	}

}
