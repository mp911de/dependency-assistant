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

package biz.paluch.dap.github;

import java.io.IOException;
import java.util.List;

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.github.GitHubReleases.GitHubTagDto;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.*;
import static org.assertj.core.api.Assertions.*;

/**
 * WireMock tests for {@link GitHubApiPages}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GitHubApiPagesTests {

	static final EmptyProgressIndicator EMPTY_INDICATOR = new EmptyProgressIndicator(ModalityState.NON_MODAL);

	static final String ACCEPT = "application/vnd.github+json";

	WireMockServer server;

	GithubApiRequestExecutor executor;

	String apiBase;

	@BeforeEach
	void setUp() {

		server = new WireMockServer(wireMockConfig().dynamicPort());
		server.start();

		executor = GithubApiRequestExecutor.Factory.getInstance().create();
		apiBase = server.baseUrl() + "/api/v3";
	}

	@AfterEach
	void tearDown() {

		if (server != null) {
			server.stop();
		}
	}

	@Test
	void followsNextLinkUntilLastPage() throws IOException {

		server.stubFor(get(urlEqualTo("/api/v3/tags?page=1")).willReturn(okJson(tags("v1.0.0", "v1.0.1"))
				.withHeader("Link", "<%s/tags?page=2>; rel=\"next\"".formatted(apiBase))));
		server.stubFor(get(urlEqualTo("/api/v3/tags?page=2")).willReturn(okJson(tags("v1.1.0"))));

		List<GitHubTagDto> tags = loadAll("/tags?page=1");

		assertThat(tags).extracting(GitHubTagDto::name).containsExactly("v1.0.0", "v1.0.1", "v1.1.0");
	}

	@Test
	void returnsSinglePageWithoutNextLink() throws IOException {

		server.stubFor(get(urlEqualTo("/api/v3/tags")).willReturn(okJson(tags("v1.0.0"))));

		assertThat(loadAll("/tags")).extracting(GitHubTagDto::name).containsExactly("v1.0.0");
	}

	@Test
	void rejectsNextLinkPointingToAnotherServer() {

		server.stubFor(get(urlEqualTo("/api/v3/tags")).willReturn(okJson(tags("v1.0.0"))
				.withHeader("Link", "<https://evil.example.com/api/v3/tags?page=2>; rel=\"next\"")));

		assertThatIllegalStateException().isThrownBy(() -> loadAll("/tags"))
				.withMessageContaining("https://evil.example.com/api/v3/tags?page=2");
	}

	private List<GitHubTagDto> loadAll(String path) throws IOException {

		GithubApiRequest<GithubResponsePage<GitHubTagDto>> initial = new GithubApiRequest.Get.JsonPage<>(
				apiBase + path, GitHubTagDto.class, ACCEPT);

		return GitHubApiPages.loadAll(executor, EMPTY_INDICATOR, apiBase, initial,
				nextUrl -> new GithubApiRequest.Get.JsonPage<>(nextUrl, GitHubTagDto.class, ACCEPT));
	}

	private static String tags(String... names) {

		StringBuilder json = new StringBuilder("[");
		for (String name : names) {
			if (json.length() > 1) {
				json.append(",");
			}
			json.append("{\"name\":\"%s\",\"commit\":{\"sha\":\"%s\"}}".formatted(name, name.hashCode()));
		}

		return json.append("]").toString();
	}

}
