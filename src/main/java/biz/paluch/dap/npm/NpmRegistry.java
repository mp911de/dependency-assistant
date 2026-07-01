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
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactNotFoundException;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.util.HttpClientUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.HttpRequests;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * {@link ReleaseSource} that fetches release metadata from the public NPM
 * registry at {@code https://registry.npmjs.org/}.
 *
 * @author Mark Paluch
 */
public class NpmRegistry implements ReleaseSource {

	/**
	 * Singleton release source bound to the public NPM registry.
	 */
	public static final NpmRegistry NPM_REGISTRY = new NpmRegistry(
			"https://registry.npmjs.org/");

	private static final Logger LOG = Logger.getInstance(NpmRegistry.class);

	private static final String ACCEPT_HEADER = "application/json";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final String registryBaseUrl;

	private final URI uri;

	NpmRegistry(String registryBaseUrl) {
		this.registryBaseUrl = registryBaseUrl.endsWith("/") ? registryBaseUrl : registryBaseUrl + "/";
		this.uri = URI.create(registryBaseUrl);
	}

	@Override
	public String getId() {
		return "NpmRegistry[%s]".formatted(uri.getHost());
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException {

		if (artifactId instanceof GitArtifactId && NpmUtils.GITHUB_AVAILABLE) {
			return List.of();
		}

		String packageName = toPackageName(artifactId);
		URI uri = URI.create(registryBaseUrl + encodePackageName(packageName));
		indicator.checkCanceled();

			String body = fetchUrl(artifactId, uri);
			if (body == null || body.isEmpty()) {
				return List.of();
			}
			return parseReleases(body);
	}

	@Override
	public String toString(ArtifactId artifactId) {
		return NpmUtils.toString(artifactId);
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

	private @Nullable String fetchUrl(ArtifactId artifactId, URI uri) throws IOException {
		try {
			return HttpClientUtil.fetchUrl(uri, requestBuilder -> requestBuilder.accept(ACCEPT_HEADER));
		} catch (HttpRequests.HttpStatusException e) {
			if (e.getStatusCode() == 404) {
				LOG.debug("[%s][%s] HTTP Status %d: %s".formatted(toString(artifactId), getId(),
						e.getStatusCode(), uri), e);
				throw new ArtifactNotFoundException(e.getMessage(), artifactId);
			}
			LOG.warn("[%s][%s] HTTP Status %d: %s".formatted(toString(artifactId), getId(), e.getStatusCode(),
					uri), e);
			return null;
		}
	}

	protected List<Release> parseReleases(String body) throws IOException {

		JsonNode root = MAPPER.readTree(body);
		JsonNode versions = root.path("versions");
		JsonNode time = root.path("time");

		if (!versions.isObject()) {
			return List.of();
		}

		List<Release> result = new ArrayList<>();
		for (Map.Entry<String, JsonNode> property : versions.properties()) {
			String versionString = property.getKey();
			JsonNode version = property.getValue();
			JsonNode gitHead = version.get("gitHead");
			String sha = gitHead != null ? gitHead.asText(null) : null;
			LocalDateTime releaseDate = parseReleaseDate(time.path(versionString).asText(null));

			Release.tryFrom(versionString, releaseDate, sha).ifPresent(result::add);
		}
		return result;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof NpmRegistry that)) {
			return false;
		}
		if (!ObjectUtils.nullSafeEquals(registryBaseUrl, that.registryBaseUrl)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(uri, that.uri);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(
				registryBaseUrl, uri);
	}

	@Override
	public String toString() {
		return getId();
	}

	private static String toPackageName(ArtifactId artifactId) {

		String groupId = artifactId.groupId();
		String name = artifactId.artifactId();
		if (groupId.equals(name)) {
			return name;
		}
		return groupId + "/" + name;
	}

	private static @Nullable LocalDateTime parseReleaseDate(@Nullable String publishedAt) {

		if (publishedAt == null || publishedAt.isEmpty()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(publishedAt).toLocalDateTime();
		} catch (RuntimeException ignored) {
			return null;
		}
	}

}
