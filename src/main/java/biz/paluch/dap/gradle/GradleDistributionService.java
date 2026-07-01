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

package biz.paluch.dap.gradle;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactNotFoundException;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.HttpRequests;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * {@link ReleaseSource} for Gradle distribution archives.
 *
 * <p>The Gradle distribution is represented through the synthetic coordinate
 * {@code org.gradle:gradle}. Stable releases are read from Gradle's JSON
 * version feed and carry the published distribution checksum as release sha
 * metadata.
 *
 * @author Mark Paluch
 */
public class GradleDistributionService implements ReleaseSource {

	/**
	 * Synthetic artifact coordinate for the Gradle distribution.
	 */
	public static final ArtifactId GRADLE_DISTRIBUTION = ArtifactId.of("org.gradle", "gradle");

	/**
	 * Shared Gradle distribution release source.
	 */
	public static final GradleDistributionService INSTANCE = new GradleDistributionService();

	private static final Logger LOG = Logger.getInstance(GradleDistributionService.class);

	private static final URI VERSIONS_ALL = URI.create("https://services.gradle.org/versions/all");

	private static final String ACCEPT_HEADER = "application/json";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final DateTimeFormatter BUILD_TIME_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMddHHmmssZ");

	private final VersionsFetcher fetcher;

	private GradleDistributionService() {
		this(GradleDistributionService::fetchUrl);
	}

	public GradleDistributionService(VersionsFetcher fetcher) {
		this.fetcher = fetcher;
	}

	@Override
	public String getId() {
		return "GradleDistributionService[%s]".formatted(VERSIONS_ALL.getHost());
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException {

		if (!GRADLE_DISTRIBUTION.equals(artifactId)) {
			return List.of();
		}

		indicator.checkCanceled();

		try {
			String body = fetcher.fetch(VERSIONS_ALL);
			indicator.checkCanceled();
			if (!StringUtils.hasText(body)) {
				return List.of();
			}
			return parseReleases(body, indicator);
		} catch (HttpRequests.HttpStatusException e) {
			if (e.getStatusCode() == 404) {
				LOG.debug("[%s][%s] HTTP Status %d: %s".formatted(artifactId, getId(),
						e.getStatusCode(), VERSIONS_ALL), e);
				throw new ArtifactNotFoundException(e.getMessage(), artifactId);
			}
			LOG.warn("[%s][%s] HTTP Status %d: %s".formatted(artifactId, getId(),
					e.getStatusCode(), VERSIONS_ALL), e);
			throw e;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GradleDistributionService that)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(fetcher, that.fetcher);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHashCode(fetcher);
	}

	@Override
	public String toString() {
		return getId();
	}

	private static List<Release> parseReleases(String body, ProgressIndicator indicator) throws IOException {

		JsonNode root = MAPPER.readTree(body);
		if (!root.isArray()) {
			return List.of();
		}

		List<Release> result = new ArrayList<>();
		for (JsonNode entry : root) {
			indicator.checkCanceled();

			if (isSkipped(entry)) {
				continue;
			}

			String version = text(entry, "version");
			if (!StringUtils.hasText(version)) {
				continue;
			}

			LocalDateTime releaseDate = parseBuildTime(text(entry, "buildTime"));
			String checksum = text(entry, "checksum");

			ArtifactVersion.from(version).map(it -> Release.of(GitVersion.of(checksum, it), releaseDate))
					.ifPresent(result::add);
		}

		return result;
	}

	private static boolean isSkipped(JsonNode entry) {
		return entry.path("snapshot").asBoolean(false) || entry.path("nightly").asBoolean(false)
				|| entry.path("broken").asBoolean(false) || StringUtils.hasText(text(entry, "rcFor"))
				|| StringUtils.hasText(text(entry, "milestoneFor"));
	}

	private static @Nullable String text(JsonNode node, String field) {

		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		return value.asText(null);
	}

	private static @Nullable LocalDateTime parseBuildTime(@Nullable String buildTime) {

		if (!StringUtils.hasText(buildTime)) {
			return null;
		}

		try {
			return OffsetDateTime.parse(buildTime, BUILD_TIME_FORMATTER).toLocalDateTime();
		} catch (RuntimeException e) {
			LOG.debug("Could not parse Gradle distribution build time " + buildTime, e);
			return null;
		}
	}

	private static @Nullable String fetchUrl(URI uri) throws IOException {
		return HttpClientUtil.fetchUrl(uri, requestBuilder -> requestBuilder.accept(ACCEPT_HEADER));
	}

	@FunctionalInterface
	public interface VersionsFetcher {

		@Nullable
		String fetch(URI uri) throws IOException;

	}

}
