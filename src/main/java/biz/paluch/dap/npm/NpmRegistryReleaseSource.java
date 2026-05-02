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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactNotFoundException;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.util.HttpClientFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import org.jspecify.annotations.Nullable;

/**
 * {@link ReleaseSource} that fetches release metadata from the public NPM
 * registry at {@code https://registry.npmjs.org/}.
 *
 * <p>The HTTP client setup is duplicated locally rather than extracted into a
 * shared abstraction. The Maven and NPM sources each have their own setup; if a
 * third HTTP-backed source appears later, extraction can be reconsidered. For
 * now, duplicating ~30 lines of {@link HttpClient} setup is preferable to a
 * one-shot abstraction.
 *
 * <p>The source caps response bodies at the same 5 MiB limit used by
 * {@code RemoteRepositoryReleaseSource}; oversized responses produce an empty
 * release list rather than throwing, mirroring the conservative posture of the
 * Maven source.
 *
 * @author Mark Paluch
 */
public class NpmRegistryReleaseSource implements ReleaseSource {

	/**
	 * Singleton release source bound to the public NPM registry.
	 */
	public static final NpmRegistryReleaseSource NPM_REGISTRY = new NpmRegistryReleaseSource(
			"https://registry.npmjs.org/");

	private static final Logger LOG = Logger.getInstance(NpmRegistryReleaseSource.class);

	private static final long MAX_RESPONSE_BODY_BYTES = 5 * 1024 * 1024;

	private static final String ACCEPT_HEADER = "application/vnd.npm.install-v1+json";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final HttpClient client = HttpClientFactory.createHttpClient();

	private final String registryBaseUrl;

	NpmRegistryReleaseSource(String registryBaseUrl) {
		this.registryBaseUrl = registryBaseUrl.endsWith("/") ? registryBaseUrl : registryBaseUrl + "/";
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId) {

		String packageName = toPackageName(artifactId);
		URI uri = URI.create(registryBaseUrl + encodePackageName(packageName));

		try {
			String body = fetchUrl(artifactId, uri);
			if (body == null || body.isEmpty()) {
				return List.of();
			}

			return parseReleases(body);
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private static String toPackageName(ArtifactId artifactId) {

		String groupId = artifactId.groupId();
		String name = artifactId.artifactId();
		if (groupId.equals(name)) {
			return name;
		}
		return groupId + "/" + name;
	}

	/**
	 * Encode a validated NPM package name for inclusion in the registry URL path.
	 * The leading {@code @} of a scoped name is percent-encoded, and the scope/name
	 * separator slash is preserved; all other characters in the name allowlist are
	 * URL-safe.
	 */
	protected static String encodePackageName(String packageName) {

		if (packageName.startsWith("@")) {
			int slash = packageName.indexOf('/');
			if (slash > 0) {
				String scope = packageName.substring(1, slash);
				String name = packageName.substring(slash + 1);
				return "%40" + URLEncoder.encode(scope, StandardCharsets.UTF_8) + "/"
						+ URLEncoder.encode(name, StandardCharsets.UTF_8);
			}
		}
		return URLEncoder.encode(packageName, StandardCharsets.UTF_8);
	}

	protected List<Release> parseReleases(String body) {

		try {
			JsonNode root = MAPPER.readTree(body);
			JsonNode versions = root.path("versions");
			JsonNode time = root.path("time");

			if (!versions.isObject()) {
				return List.of();
			}

			List<Release> result = new ArrayList<>();
			Iterator<Map.Entry<String, JsonNode>> entries = versions.fields();
			while (entries.hasNext()) {

				Map.Entry<String, JsonNode> entry = entries.next();
				String versionString = entry.getKey();
				if (versionString == null || versionString.isEmpty()) {
					continue;
				}

				ArtifactVersion.from(versionString).ifPresent(parsed -> {
					String publishedAt = time.path(versionString).asText(null);
					if (publishedAt != null && !publishedAt.isEmpty()) {
						try {
							OffsetDateTime instant = OffsetDateTime.parse(publishedAt);
							result.add(Release.of(parsed, instant.toLocalDateTime()));
							return;
						} catch (RuntimeException ignored) {
							// fall through to date-less release
						}
					}
					result.add(Release.of(parsed));
				});
			}
			return result;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

	}

	private @Nullable String fetchUrl(ArtifactId artifactId, URI uri) throws IOException, InterruptedException {

		HttpRequest request = HttpRequest.newBuilder(uri) //
				.header("User-Agent", HttpClientFactory.getUserAgent()) //
				.header("Accept", ACCEPT_HEADER) //
				.timeout(Duration.ofSeconds(10)) //
				.GET().build();

		HttpResponse<String> response = client.send(request, cappedUtf8BodyHandler());

		if (response.statusCode() >= 200 && response.statusCode() < 300) {
			return response.body();
		}

		if (response.statusCode() == 404) {
			throw new ArtifactNotFoundException(response.body(), artifactId);
		}

		LOG.debug("HTTP " + response.statusCode() + " fetching: " + uri);
		return null;
	}

	private static BodyHandler<String> cappedUtf8BodyHandler() {

		return responseInfo -> BodySubscribers.mapping(BodySubscribers.ofInputStream(), in -> {
			try {
				return readUtf8StreamCapped(in, MAX_RESPONSE_BODY_BYTES);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	private static String readUtf8StreamCapped(InputStream in, long maxBytes) throws IOException {

		try (in) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[16384];
			long total = 0;
			while (true) {
				int n = in.read(buf);
				if (n == -1) {
					break;
				}
				total += n;
				if (total > maxBytes) {
					throw new IOException("Response body exceeds maximum size");
				}
				out.write(buf, 0, n);
			}
			return out.toString(StandardCharsets.UTF_8);
		}
	}


}
