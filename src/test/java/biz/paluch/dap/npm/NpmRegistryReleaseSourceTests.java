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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Release;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link NpmRegistryReleaseSource}.
 *
 * @author Mark Paluch
 */
class NpmRegistryReleaseSourceTests {

	private static final NpmRegistryReleaseSource SOURCE = NpmRegistryReleaseSource.NPM_REGISTRY;

	@Test
	void parsesAxiosLikePayload() {

		String body = """
				{
				  "name": "axios",
				  "versions": {
				    "1.6.7": {},
				    "1.6.8": {},
				    "1.7.0": {}
				  },
				  "time": {
				    "1.6.7": "2024-02-01T10:00:00Z",
				    "1.6.8": "2024-03-01T10:00:00Z",
				    "1.7.0": "2024-04-01T10:00:00Z"
				  }
				}
				""";

		List<Release> releases = SOURCE.parseReleases(body);

		assertThat(releases).hasSize(3);
		assertThat(releases).extracting(r -> r.version().toString())
				.containsExactlyInAnyOrder("1.6.7", "1.6.8", "1.7.0");
		assertThat(releases).allSatisfy(r -> assertThat(r.releaseDate()).isNotNull());
	}

	@Test
	void parsesScopedPackagePayload() {

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
	void preservesPreReleaseVersions() {

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
	void emptyOrInvalidPayloadProducesNoReleases() {
		assertThat(SOURCE.parseReleases("")).isEmpty();
		assertThat(SOURCE.parseReleases("{}")).isEmpty();
		assertThat(SOURCE.parseReleases("not-json")).isEmpty();
	}

	@Test
	void encodesScopedPackageName() {
		assertThat(NpmRegistryReleaseSource.encodePackageName("axios")).isEqualTo("axios");
		assertThat(NpmRegistryReleaseSource.encodePackageName("@vitejs/plugin-vue"))
				.isEqualTo("%40vitejs/plugin-vue");
	}

	@Test
	void parsesPayloadWithoutTimeMap() {

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

	@Test
	void returnsEmptyOnHttpErrorStatus() throws IOException {

		try (StubHttpServer stub = StubHttpServer.respondingWith(404, "Not Found")) {
			NpmRegistryReleaseSource source = new NpmRegistryReleaseSource(stub.baseUrl());
			assertThat(source.getReleases(ArtifactId.of("axios", "axios"))).isEmpty();
		}
	}

	@Test
	void returnsEmptyOnServerError() throws IOException {

		try (StubHttpServer stub = StubHttpServer.respondingWith(500, "Internal Server Error")) {
			NpmRegistryReleaseSource source = new NpmRegistryReleaseSource(stub.baseUrl());
			assertThat(source.getReleases(ArtifactId.of("axios", "axios"))).isEmpty();
		}
	}

	@Test
	void returnsEmptyOnOversizeBody() throws IOException {

		String oversize = "{\"versions\":{\"1.0.0\":{}},\"padding\":\""
				+ "x".repeat(6 * 1024 * 1024) + "\"}";
		try (StubHttpServer stub = StubHttpServer.respondingWith(200, oversize)) {
			NpmRegistryReleaseSource source = new NpmRegistryReleaseSource(stub.baseUrl());
			assertThat(source.getReleases(ArtifactId.of("axios", "axios"))).isEmpty();
		}
	}

	@Test
	void sendsAcceptHeader() throws IOException {

		AtomicReference<String> capturedAccept = new AtomicReference<>();
		String body = """
				{"versions":{"1.0.0":{}}}
				""";
		try (StubHttpServer stub = StubHttpServer.respondingWith(200, body, capturedAccept)) {
			NpmRegistryReleaseSource source = new NpmRegistryReleaseSource(stub.baseUrl());
			source.getReleases(ArtifactId.of("axios", "axios"));
		}

		assertThat(capturedAccept.get()).isEqualTo("application/vnd.npm.install-v1+json");
	}

	private static final class StubHttpServer implements AutoCloseable {

		private final HttpServer server;

		private StubHttpServer(HttpServer server) {
			this.server = server;
		}

		static StubHttpServer respondingWith(int status, String body) throws IOException {
			return respondingWith(status, body, new AtomicReference<>());
		}

		static StubHttpServer respondingWith(int status, String body, AtomicReference<String> capturedAccept)
				throws IOException {

			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> handle(exchange, status, body, capturedAccept));
			server.start();
			return new StubHttpServer(server);
		}

		String baseUrl() {
			return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
		}

		@Override
		public void close() {
			server.stop(0);
		}

		private static void handle(HttpExchange exchange, int status, String body,
				AtomicReference<String> capturedAccept) throws IOException {

			capturedAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}

	}

}
