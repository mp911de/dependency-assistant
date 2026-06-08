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

package biz.paluch.dap.npm;

import java.io.IOException;
import java.util.List;

import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link NpmRegistry}.
 *
 * @author Mark Paluch
 */
class NpmRegistryTests {

	private static final NpmRegistry SOURCE = NpmRegistry.NPM_REGISTRY;

	@Test
	void parsesAxiosLikePayload() throws IOException {

		String body = """
				{
				  "name": "axios",
				  "versions": {
				    "1.6.7": { "gitHead": "1111111"},
				    "1.6.8": { "gitHead": "2222222"},
				    "1.7.0": { "gitHead": "3333333"}
				  },
				  "time": {
				    "1.6.7": "2024-02-01T10:00:00Z",
				    "1.6.8": "2024-03-01T10:00:00Z",
				    "1.7.0": "2024-04-01T10:00:00Z"
				  }
				}
				""";

		List<Release> releases = SOURCE.parseReleases(body);

		for (Release release : releases) {
			assertThat(release.version()).isInstanceOf(GitVersion.class);
		}
		assertThat(releases).extracting(r -> r.version().toString())
				.containsExactlyInAnyOrder("1.6.7", "1.6.8", "1.7.0");
		assertThat(releases).allSatisfy(r -> assertThat(r.releaseDate()).isNotNull());
	}

	@Test
	void parsesScopedPackagePayload() throws IOException {

		String body = """
				{
				  "name": "@vitejs/plugin-vue",
				  "versions": {
				    "3.1.0": {},
				    "3.1.2": {}
				  },
				  "time": {
				    "3.1.0": "2023-09-01T10:00:00Z",
				    "3.1.2": "2023-09-15T10:00:00Z"
				  }
				}
				""";

		List<Release> releases = SOURCE.parseReleases(body);

		assertThat(releases).extracting(r -> r.version().toString())
				.containsExactlyInAnyOrder("3.1.0", "3.1.2");
	}

	@Test
	void preservesPreReleaseVersions() throws IOException {

		String body = """
				{
				  "versions": {
				    "1.0.0": {},
				    "1.1.0-rc.1": {},
				    "1.1.0-beta": {},
				    "1.1.0-next.0": {}
				  },
				  "time": {
				    "1.0.0": "2024-01-01T10:00:00Z",
				    "1.1.0-rc.1": "2024-02-01T10:00:00Z",
				    "1.1.0-beta": "2024-01-20T10:00:00Z",
				    "1.1.0-next.0": "2024-01-25T10:00:00Z"
				  }
				}
				""";

		List<Release> releases = SOURCE.parseReleases(body);

		assertThat(releases).extracting(r -> r.version().toString())
				.containsExactlyInAnyOrder("1.0.0", "1.1.0-rc.1", "1.1.0-beta", "1.1.0-next.0");
	}

	@Test
	void encodesScopedPackageName() {
		assertThat(NpmRegistry.encodePackageName("axios")).isEqualTo("axios");
		assertThat(NpmRegistry.encodePackageName("@vitejs/plugin-vue"))
				.isEqualTo("%40vitejs/plugin-vue");
	}

	@Test
	void parsesPayloadWithoutTimeMap() throws IOException {

		String body = """
				{
				  "versions": {
				    "1.0.0": {},
				    "2.0.0": {}
				  }
				}
				""";

		List<Release> releases = SOURCE.parseReleases(body);

		assertThat(releases).hasSize(2);
		assertThat(releases).allSatisfy(r -> assertThat(r.releaseDate()).isNull());
	}

}
