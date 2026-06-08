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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.StringUtils;
import biz.paluch.dap.xml.MavenMetadataProjection;
import biz.paluch.dap.xml.XmlBeamProjectorFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.HttpRequests;
import org.jspecify.annotations.Nullable;

/**
 * Release source that fetches releases from a remote Maven repository.
 *
 * @author Mark Paluch
 */
public class MavenRepository implements ReleaseSource {

	/**
	 * Release source for Maven Central.
	 */
	public static final MavenRepository MAVEN_CENTRAL = new MavenRepository(
			RemoteRepository.mavenCentral());

	private static final Logger LOG = Logger.getInstance(MavenRepository.class);

	private static final Pattern DIRECTORY_LISTING_PATTERN = Pattern
			.compile("<a (?>[^>]+)>([^/]+)/</a>(?>\\s*)(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})(?>\\s*)(?>-)?");

	private static final DateTimeFormatter DIRECTORY_LISTING_DATE_FORMATTER = DateTimeFormatter
			.ofPattern("uuuu-MM-dd HH:mm");

	private static final Pattern ARTIFACTORY_DIRECTORY_LISTING_PATTERN = Pattern
			.compile("<a (?>[^>]+)>([^/]+)/</a>(?>\\s*)(\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2})(?>\\s*)(?>-)?");

	private static final DateTimeFormatter DIRECTORY_LISTING_ARTIFACTORY_DATE_FORMATTER = DateTimeFormatter
			.ofPattern("dd-MMM-uuuu HH:mm", Locale.ENGLISH);

	private final RemoteRepository repository;

	/**
	 * Create a release source backed by the given repository.
	 */
	public MavenRepository(RemoteRepository repository) {
		this.repository = repository;
	}

	public RemoteRepository getRepository() {
		return repository;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MavenRepository that)) {
			return false;
		}
		return Objects.equals(repository, that.repository);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(repository);
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) {

		String path = artifactId.groupId().replace(".", "/") + "/" + artifactId.artifactId() + "/";
		String metadataPath = path + "maven-metadata.xml";

		String baseUrl = repository.url();
		String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
		URI repositoryBaseUri = URI.create(base).normalize();
		URI metadataUri = repositoryBaseUri.resolve(metadataPath);
		URI directoryUri = repositoryBaseUri.resolve(path);
		indicator.checkCanceled();
		String xml = fetchUrl(artifactId, metadataUri, repository.credentials(), true, repositoryBaseUri);
		indicator.checkCanceled();
		String directoryListing = fetchUrl(artifactId, directoryUri, repository.credentials(), false,
				repositoryBaseUri);

		if (StringUtils.isEmpty(xml)) {
			return List.of();
		}

		Map<String, LocalDateTime> releaseDates = parseDirectoryListingDates(directoryListing);
		Set<Release> releases = new TreeSet<>(Comparator.reverseOrder());
		for (String rawVersion : parseReleaseVersions(xml)) {
			Release.tryFrom(rawVersion, releaseDates.get(rawVersion), null).ifPresent(releases::add);
		}

		return new ArrayList<>(releases);
	}

	private List<String> parseReleaseVersions(String xml) {

		MavenMetadataProjection projection = XmlBeamProjectorFactory.INSTANCE.projectXMLString(xml,
				MavenMetadataProjection.class);

		List<String> versions = projection.getVersions();
		if (versions == null) {
			return List.of();
		}

		List<String> result = new ArrayList<>();
		for (String v : versions) {
			String trimmed = StringUtils.hasText(v) ? v.trim() : "";
			if (trimmed.endsWith("-SNAPSHOT") || trimmed.isEmpty()) {
				continue;
			}
			if (SemanticArtifactVersion.isVersion(trimmed)
					|| ReleaseTrainArtifactVersion.isReleaseTrainVersion(trimmed)) {
				result.add(trimmed);
			}
		}

		return result;
	}

	private static Map<String, LocalDateTime> parseDirectoryListingDates(@Nullable String html) {

		Map<String, LocalDateTime> result = new HashMap<>();

		if (StringUtils.isEmpty(html)) {
			return result;
		}

		for (String line : html.lines().toList()) {

			Matcher match = DIRECTORY_LISTING_PATTERN.matcher(line);

			if (match.find()) {
				String version = match.group(1) != null ? match.group(1).trim() : null;
				String dateStr = match.group(2) != null ? match.group(2).trim() : null;
				if (version != null && dateStr != null) {
					try {
						result.put(version, LocalDateTime.from(DIRECTORY_LISTING_DATE_FORMATTER.parse(dateStr)));
					} catch (Exception e) {
						LOG.debug("Could not parse directory listing date for version " + version, e);
					}
				}
				continue;
			}

			match = ARTIFACTORY_DIRECTORY_LISTING_PATTERN.matcher(line);

			if (match.find()) {

				String version = match.group(1) != null ? match.group(1).trim() : null;
				String dateStr = match.group(2) != null ? match.group(2).trim() : null;

				if (version != null && dateStr != null) {
					try {
						result.put(version,
								LocalDateTime.from(DIRECTORY_LISTING_ARTIFACTORY_DATE_FORMATTER.parse(dateStr)));
					} catch (Exception e) {
						LOG.debug("Could not parse directory listing date for version %s".formatted(version), e);
					}
				}
			}
		}

		return result;
	}

	private static @Nullable String fetchUrl(ArtifactId artifactId, URI uri,
			@Nullable RepositoryCredentials credentials, boolean failOnNotFound, URI repositoryBaseUri) {

		try {
			return HttpClientUtil.fetchUrl(uri, requestBuilder -> {
				return requestBuilder.tuner(connection -> {
					if (credentials != null
							&& hasSameBaseUri(repositoryBaseUri, URI.create(connection.getURL()
									.toString()))) {
						connection.addRequestProperty("Authorization", basicAuthHeader(credentials));
					}
				});
			});
		} catch (HttpRequests.HttpStatusException e) {
			if (failOnNotFound && e.getStatusCode() == 404) {
				throw new ArtifactNotFoundException("%s: HTTP Status 404".formatted(uri), artifactId);
			}
			LOG.debug("%s: HTTP %d fetching: %s".formatted(artifactId, e.getStatusCode(), uri), e);
			return null;
		} catch (IOException e) {
			throw new UncheckedIOException("%s: Failed to fetch %s: %s".formatted(artifactId, uri, e.getMessage()), e);
		}
	}

	public static boolean hasSameBaseUri(URI repositoryBase, URI requestTarget) {

		String baseHost = repositoryBase.getHost();
		String targetHost = requestTarget.getHost();
		if (baseHost == null || targetHost == null) {
			return false;
		}
		if (!baseHost.equalsIgnoreCase(targetHost)) {
			return false;
		}
		return HttpClientUtil.getEffectivePort(repositoryBase) == HttpClientUtil.getEffectivePort(requestTarget);
	}

	private static String basicAuthHeader(RepositoryCredentials credentials) {

		String raw = credentials.username() + ":" + credentials.password();
		return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

}
