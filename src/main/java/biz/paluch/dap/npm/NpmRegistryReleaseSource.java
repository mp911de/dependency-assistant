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
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactNotFoundException;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.LRUMap;
import com.intellij.openapi.diagnostic.Logger;
import org.jspecify.annotations.Nullable;

/**
 * {@link ReleaseSource} that fetches release metadata from the public NPM
 * registry at {@code https://registry.npmjs.org/}.
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

	private static final String ACCEPT_HEADER = "application/json";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final LRUMap<ArtifactId, @Nullable AtomicInteger> KNOWN_FAILURES = new LRUMap<>(256, 256);

	private final String registryBaseUrl;

	NpmRegistryReleaseSource(String registryBaseUrl) {
		this.registryBaseUrl = registryBaseUrl.endsWith("/") ? registryBaseUrl : registryBaseUrl + "/";
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId) {

		if (artifactId instanceof GitArtifactId && NpmProjectContext.GITHUB_AVAILABLE) {
			return List.of();
		}

		AtomicInteger counter = KNOWN_FAILURES.get(artifactId);
		if (counter != null && counter.get() > 1) {
			return List.of();
		}

		String packageName = toPackageName(artifactId);
		URI uri = URI.create(registryBaseUrl + encodePackageName(packageName));

		try {
			String body = fetchUrl(artifactId, uri);
			if (body == null || body.isEmpty()) {
				return List.of();
			}

			return parseReleases(body);
		} catch (InterruptedException e) {
			LOG.debug("%s: HTTP fetch interrupted: %s".formatted(artifactId, uri), e);
			Thread.currentThread().interrupt();
			return List.of();
		} catch (IOException e) {
			if (counter == null) {
				counter = new AtomicInteger(0);
			}
			counter.incrementAndGet();
			KNOWN_FAILURES.putIfAbsent(artifactId, counter);
			throw new UncheckedIOException(e);
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

	protected List<Release> parseReleases(String body) throws IOException {

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
			if (StringUtils.isEmpty(versionString)) {
				continue;
			}

			JsonNode version = entry.getValue();
			JsonNode gitHead = version.get("gitHead");

			ArtifactVersion.from(versionString).ifPresent(it -> {

				ArtifactVersion artifactVersion = it;
				if (gitHead != null) {

					String sha = gitHead.asText(null);
					if (StringUtils.hasText(sha)) {
						artifactVersion = GitVersion.of(sha, artifactVersion);
					}
				}

				String publishedAt = time.path(versionString).asText(null);
				if (publishedAt != null && !publishedAt.isEmpty()) {
					try {
						OffsetDateTime instant = OffsetDateTime.parse(publishedAt);
						result.add(Release.of(artifactVersion, instant.toLocalDateTime()));
						return;
					} catch (RuntimeException ignored) {
						// fall through to date-less release
					}
				}
				result.add(Release.of(artifactVersion));
			});
		}
		return result;
	}

	private @Nullable String fetchUrl(ArtifactId artifactId, URI uri) throws IOException, InterruptedException {

		HttpResponse<String> response = HttpClientUtil.sendRequest(it -> it.GET().header("Accept", ACCEPT_HEADER),
				HttpClientUtil.cappedUtf8BodyHandler());

		if (response.statusCode() >= 200 && response.statusCode() < 300) {
			return response.body();
		}

		if (response.statusCode() == 404) {
			throw new ArtifactNotFoundException(response.body(), artifactId);
		}

		LOG.debug("%s: HTTP %d fetching: %s".formatted(artifactId, response.statusCode(), uri));
		return null;
	}


}
