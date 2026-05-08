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
import java.net.http.HttpResponse;
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
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.StringUtils;
import biz.paluch.dap.xml.MavenMetadataProjection;
import biz.paluch.dap.xml.XmlBeamProjectorFactory;
import com.intellij.openapi.diagnostic.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Release source that fetches releases from a remote Maven repository.
 *
 * @author Mark Paluch
 */
public class RemoteRepositoryReleaseSource implements ReleaseSource {

	/**
	 * Release source for Maven Central.
	 */
	public static RemoteRepositoryReleaseSource MAVEN_CENTRAL = new RemoteRepositoryReleaseSource(
			RemoteRepository.mavenCentral());

	private static final Logger LOG = Logger.getInstance(RemoteRepositoryReleaseSource.class);

	private static final Pattern DIRECTORY_LISTING_PATTERN = Pattern
			.compile("<a (?>[^>]+)>([^/]+)/</a>(?>\\s*)(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})(?>\\s*)(?>-)?");

	private static final DateTimeFormatter DIRECTORY_LISTING_DATE_FORMATTER = DateTimeFormatter
			.ofPattern("uuuu-MM-dd HH:mm");

	private static final Pattern ARTIFACTORY_DIRECTORY_LISTING_PATTERN = Pattern
			.compile("<a (?>[^>]+)>([^/]+)/</a>(?>\\s*)(\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2})(?>\\s*)(?>-)?");

	private static final DateTimeFormatter DIRECTORY_LISTING_ARTIFACTORY_DATE_FORMATTER = DateTimeFormatter
			.ofPattern("dd-MMM-uuuu HH:mm", Locale.ENGLISH);

	private static final int MAX_REDIRECT_HOPS = 10;

	private final RemoteRepository repository;

	/**
	 * Create a release source backed by the given repository.
	 */
	public RemoteRepositoryReleaseSource(RemoteRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId) {

		String path = artifactId.groupId().replace(".", "/") + "/" + artifactId.artifactId() + "/";
		String metadataPath = path + "maven-metadata.xml";

		Set<ArtifactVersion> versions = new TreeSet<>(Comparator.reverseOrder());

		String baseUrl = repository.url();
		String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
		URI repositoryBaseUri = URI.create(base).normalize();
		URI metadataUri = repositoryBaseUri.resolve(metadataPath);
		URI directoryUri = repositoryBaseUri.resolve(path);
		String xml = fetchUrl(artifactId, metadataUri, repository.credentials(), true, repositoryBaseUri);
		String directoryListing = fetchUrl(artifactId, directoryUri, repository.credentials(), false,
				repositoryBaseUri);

		if (StringUtils.isEmpty(xml)) {
			return List.of();
		}

		Map<String, LocalDateTime> releaseDates = parseDirectoryListingDates(directoryListing);
		versions.addAll(parseAllVersionsFromMetadata(xml));

		List<Release> result = new ArrayList<>();
		for (ArtifactVersion av : versions) {
			result.add(new Release(av, releaseDates.get(av.toString())));
		}

		return result;
	}

	private List<ArtifactVersion> parseAllVersionsFromMetadata(String xml) {

		MavenMetadataProjection projection = XmlBeamProjectorFactory.INSTANCE.projectXMLString(xml,
				MavenMetadataProjection.class);

		List<String> versions = projection.getVersions();
		if (versions == null) {
			return List.of();
		}

		List<ArtifactVersion> result = new ArrayList<>();
		for (String v : versions) {
			String trimmed = StringUtils.hasText(v) ? v.trim() : "";
			if (trimmed.endsWith("-SNAPSHOT") || trimmed.isEmpty()) {
				continue;
			}
			if (SemanticArtifactVersion.isVersion(trimmed)) {
				result.add(SemanticArtifactVersion.of(trimmed));
			} else if (ReleaseTrainArtifactVersion.isReleaseTrainVersion(trimmed)) {
				result.add(ReleaseTrainArtifactVersion.of(trimmed));
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
						LOG.debug("Could not parse directory listing date for version " + version, e);
					}
				}
			}
		}

		return result;
	}

	private static @Nullable String fetchUrl(ArtifactId artifactId, URI uri,
			@Nullable RepositoryCredentials credentials, boolean failOnNotFound, URI repositoryBaseUri) {

		String url = uri.toASCIIString();
		try {
			if (credentials != null) {
				return fetchWithCredentialBinding(artifactId, uri, credentials, failOnNotFound, repositoryBaseUri);
			}
			return fetchWithStandardRedirects(artifactId, uri, failOnNotFound);
		} catch (InterruptedException e) {
			LOG.debug("%s: HTTP fetch interrupted: %s".formatted(artifactId, url), e);
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException e) {
			throw new UncheckedIOException("%s: Failed to fetch %s".formatted(artifactId, url), e);
		}
	}

	private static @Nullable String fetchWithStandardRedirects(ArtifactId artifactId, URI uri, boolean failOnNotFound)
			throws IOException, InterruptedException {

		HttpResponse<String> response = HttpClientUtil.sendRequest(it -> it.GET().uri(uri),
				HttpClientUtil.cappedUtf8BodyHandler());

		if (HttpClientUtil.hasBody(response)) {
			return response.body();
		}

		if (failOnNotFound && response.statusCode() == 404) {
			throw new ArtifactNotFoundException("%s: HTTP Status 404".formatted(uri), artifactId);
		}

		LOG.debug("%s: HTTP %d fetching: %s".formatted(artifactId, response.statusCode(), uri));
		return null;
	}

	private static @Nullable String fetchWithCredentialBinding(ArtifactId artifactId, URI uri,
			RepositoryCredentials credentials, boolean failOnNotFound, URI repositoryBaseUri)
			throws IOException, InterruptedException {

		URI current = uri;
		for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {

			URI uriToUse = current;
			HttpResponse<String> response = HttpClientUtil.sendRequest(it -> {
				it.GET();
				if (repositoryCredentialHostMatches(repositoryBaseUri, uriToUse)) {
					it.header("Authorization", basicAuthHeader(credentials));
				}
			}, HttpClientUtil.cappedUtf8BodyHandler());
			int status = response.statusCode();

			if (HttpClientUtil.hasBody(response)) {
				return response.body();
			}

			if (failOnNotFound && status == 404) {
				throw new ArtifactNotFoundException(current + ": HTTP Status 404", artifactId);
			}

			if (status >= 300 && status < 400) {
				Optional<String> location = response.headers().firstValue("location");
				if (location.isEmpty()) {
					LOG.debug(
							"%s: HTTP %d without Location header fetching: %s".formatted(artifactId, status, current));
					return null;
				}
				URI next = current.resolve(URI.create(location.get().trim()));
				if (!repositoryCredentialHostMatches(repositoryBaseUri, next)) {
					LOG.debug("%s: Refusing cross-host redirect from %s to %s".formatted(artifactId, current, next));
					return null;
				}
				current = next;
				continue;
			}

			LOG.debug("%s: HTTP %d fetching: %s".formatted(artifactId, status, current));
			return null;
		}

		LOG.debug("%s: Too many redirects fetching: %s".formatted(artifactId, uri));
		return null;
	}

	private static boolean repositoryCredentialHostMatches(URI repositoryBase, URI requestTarget) {

		String baseHost = repositoryBase.getHost();
		String targetHost = requestTarget.getHost();
		if (baseHost == null || targetHost == null) {
			return false;
		}
		if (!baseHost.equalsIgnoreCase(targetHost)) {
			return false;
		}
		return effectivePort(repositoryBase) == effectivePort(requestTarget);
	}

	private static int effectivePort(URI uri) {

		int port = uri.getPort();
		if (port != -1) {
			return port;
		}
		String scheme = uri.getScheme();
		if ("https".equalsIgnoreCase(scheme)) {
			return 443;
		}
		if ("http".equalsIgnoreCase(scheme)) {
			return 80;
		}
		return -1;
	}

	private static String basicAuthHeader(RepositoryCredentials credentials) {

		String raw = credentials.username() + ":" + credentials.password();
		return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

}
