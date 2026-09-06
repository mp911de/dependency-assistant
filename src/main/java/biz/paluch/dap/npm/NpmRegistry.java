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

package biz.paluch.dap.npm;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactNotFoundException;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.metadata.RepositoryUrl;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.util.DateUtils;
import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.ResponseTooLargeException;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.HttpRequests;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * Release source for the public NPM registry.
 * <p>Project metadata travels with {@link NpmReleases} so caching needs no
 * second request. Git-backed identities are handled by a separate release
 * source.
 *
 * @author Mark Paluch
 */
public class NpmRegistry implements ReleaseSource {

	public static final NpmRegistry NPM_REGISTRY = new NpmRegistry(
			"https://registry.npmjs.org/");

	private static final Logger LOG = Logger.getInstance(NpmRegistry.class);

	private static final String ACCEPT_HEADER = "application/json";

	private static final String ABBREVIATED_ACCEPT_HEADER = "application/vnd.npm.install-v1+json";

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

	/**
	 * Fetch package releases and project metadata.
	 * <p>Oversized documents are retried using the abbreviated registry response,
	 * which omits release dates, commit hashes, and project metadata.
	 *
	 * @throws ArtifactNotFoundException if the registry returns HTTP 404.
	 * @throws IOException if the document cannot be fetched or parsed.
	 */
	@Override
	public Sequence<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException {

		if (artifactId instanceof GitArtifactId) {
			return Sequence.empty();
		}

		String packageName = toPackageName(artifactId);
		URI uri = URI.create(registryBaseUrl + encodePackageName(packageName));
		indicator.checkCanceled();

		Sequence<Release> releases;
		try {
			releases = fetchReleases(artifactId, uri, ACCEPT_HEADER);
		} catch (ResponseTooLargeException e) {
			LOG.info("[%s][%s] Package document too large, retrying abbreviated: %s"
					.formatted(toString(artifactId), getId(), uri), e);
			releases = fetchReleases(artifactId, uri, ABBREVIATED_ACCEPT_HEADER);
		}
		return releases != null ? releases : Sequence.empty();
	}

	@Override
	public String toString(ArtifactId artifactId) {
		return NpmUtils.toString(artifactId);
	}

	/**
	 * Encode a validated package name for a registry path, retaining the scope/name
	 * separator.
	 */
	static String encodePackageName(String packageName) {

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

	private @Nullable Sequence<Release> fetchReleases(ArtifactId artifactId, URI uri, String acceptHeader)
			throws IOException {
		try {
			return HttpClientUtil.fetchUrl(uri, requestBuilder -> requestBuilder.accept(acceptHeader),
					request -> {
						try (InputStream body = HttpClientUtil.capped(request.getInputStream(),
								HttpClientUtil.MAX_RESPONSE_BODY_BYTES);
								JsonParser parser = MAPPER.createParser(body)) {
							return parseReleases(parser);
						}
					});
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

	Sequence<Release> parseReleases(String body) throws IOException {

		try (JsonParser parser = MAPPER.createParser(body)) {
			return parseReleases(parser);
		}
	}

	private Sequence<Release> parseReleases(JsonParser parser) throws IOException {

		JsonNode root = readRetainedFields(parser);
		JsonNode versions = root.path("versions");
		JsonNode time = root.path("time");

		if (!versions.isObject()) {
			return Sequence.empty();
		}

		List<Release> result = new ArrayList<>();
		for (Map.Entry<String, JsonNode> property : versions.properties()) {
			String versionString = property.getKey();
			JsonNode version = property.getValue();
			JsonNode gitHead = version.get("gitHead");
			String sha = gitHead != null ? gitHead.asText(null) : null;
			String versionTime = time.path(versionString).asText(null);
			LocalDateTime releaseDate = StringUtils.hasText(versionTime) ? DateUtils.parse(versionTime) : null;

			Release.tryFrom(versionString, releaseDate, sha).ifPresent(result::add);
		}
		return new NpmReleases(result, getProjectMetadata(root));
	}

	private static ObjectNode readRetainedFields(JsonParser parser) throws IOException {

		ObjectNode root = MAPPER.createObjectNode();
		if (parser.nextToken() != JsonToken.START_OBJECT) {
			return root;
		}

		while (parser.nextToken() == JsonToken.FIELD_NAME) {
			String field = parser.currentName();
			parser.nextToken();

			switch (field) {
			case "versions" -> {
				if (parser.currentToken() == JsonToken.START_OBJECT) {
					root.set(field, readRetainedVersions(parser));
				} else {
					parser.skipChildren();
				}
			}
			case "time", "dist-tags", "repository", "bugs" -> root.set(field, parser.readValueAsTree());
			default -> parser.skipChildren();
			}
		}
		return root;
	}

	/**
	 * Read the {@code versions} object with the parser positioned at its opening
	 * brace.
	 */
	private static ObjectNode readRetainedVersions(JsonParser parser) throws IOException {

		ObjectNode versions = MAPPER.createObjectNode();
		while (parser.nextToken() == JsonToken.FIELD_NAME) {
			String version = parser.currentName();
			parser.nextToken();
			versions.set(version, readRetainedVersion(parser));
		}
		return versions;
	}

	/**
	 * Read a single version document with the parser positioned at its value, which
	 * need not be an object.
	 */
	private static ObjectNode readRetainedVersion(JsonParser parser) throws IOException {

		ObjectNode retained = MAPPER.createObjectNode();
		if (parser.currentToken() != JsonToken.START_OBJECT) {
			parser.skipChildren();
			return retained;
		}

		while (parser.nextToken() == JsonToken.FIELD_NAME) {
			String field = parser.currentName();
			parser.nextToken();

			switch (field) {
			case "gitHead", "repository", "bugs" -> retained.set(field, parser.readValueAsTree());
			default -> parser.skipChildren();
			}
		}
		return retained;
	}

	/**
	 * Prefer usable metadata from the latest version over package-level copies.
	 */
	private static CachedMetadata getProjectMetadata(JsonNode root) {

		String latest = root.path("dist-tags").path("latest").asText("");
		JsonNode latestVersion = root.path("versions").path(latest);

		String repositoryUrl = selectRepositoryUrl(latestVersion, root);
		String issueTrackerUrl = selectIssueTrackerUrl(latestVersion, root);

		return CachedMetadata.of(repositoryUrl, issueTrackerUrl, null, null);
	}

	private static @Nullable String selectRepositoryUrl(JsonNode version, JsonNode root) {

		String selected = parseableRepositoryUrl(version.path("repository"));
		return selected != null ? selected : parseableRepositoryUrl(root.path("repository"));
	}

	private static @Nullable String parseableRepositoryUrl(JsonNode repository) {

		String declared = repository.isTextual() ? repository.asText()
				: repository.path("url").asText(repository.path("path").asText(null));
		return declared != null && RepositoryUrl.parse(declared) != null ? declared : null;
	}

	private static @Nullable String selectIssueTrackerUrl(JsonNode version, JsonNode root) {

		String selected = declaredTrackerUrl(version.path("bugs"));
		return selected != null ? selected : declaredTrackerUrl(root.path("bugs"));
	}

	private static @Nullable String declaredTrackerUrl(JsonNode bugs) {

		String declared = bugs.isTextual() ? bugs.asText() : bugs.path("url").asText(null);
		return declared != null && isAbsoluteHttpUrl(declared) ? declared : null;
	}

	private static boolean isAbsoluteHttpUrl(String url) {

		URI candidate;
		try {
			candidate = new URI(url.trim());
		} catch (URISyntaxException e) {
			return false;
		}

		String scheme = candidate.getScheme();
		if (!HttpClientUtil.isBrowsable(candidate)) {
			return false;
		}

		// A doubled scheme such as http://http://... parses as host "http" with an
		// empty port, so an authority with a trailing colon marks a malformed URL.
		String authority = candidate.getAuthority();
		return candidate.getHost() != null && authority != null && !authority.endsWith(":");
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

}
