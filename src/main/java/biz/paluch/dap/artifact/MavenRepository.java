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

package biz.paluch.dap.artifact;

import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.MavenMetadataProjection;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.XmlBeamProjectorFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Release source backed by a remote Maven repository.
 *
 * <p>The source reads {@code maven-metadata.xml} and uses a repository
 * directory listing, when available, to attach release dates. When metadata is
 * available, versions ending in {@code -SNAPSHOT} and unparseable versions are
 * omitted. Invalid Maven coordinates fail with
 * {@link ArtifactNotFoundException} before any request is made.
 *
 * <p>HTTP Basic credentials are supplied in response to a server challenge and
 * remain limited to the repository scheme, host, effective port, and base path,
 * including after redirects.
 *
 * @author Mark Paluch
 */
public class MavenRepository implements ReleaseSource {

	/**
	 * Release source for Maven Central.
	 */
	public static final MavenRepository MAVEN_CENTRAL = new MavenRepository(
			RemoteRepository.mavenCentral());

	private static final Pattern ARTIFACT_ID = Pattern.compile("^(?!\\.{1,2}$)[a-z0-9_.-]+$", Pattern.CASE_INSENSITIVE);

	private static final Pattern GROUP_ID = Pattern.compile("^[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*$",
			Pattern.CASE_INSENSITIVE);

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
	 * @param repository the remote Maven repository.
	 */
	public MavenRepository(RemoteRepository repository) {
		this.repository = repository;
	}

	@Override
	public String getId() {
		return "MavenRepository[%s@%s]".formatted(repository.getId(), repository.getUrl().getHost());
	}

	@Override
	public Sequence<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException {

		validate(artifactId);

		String path = artifactId.groupId().replace(".", "/") + "/" + artifactId.artifactId() + "/";
		URI repositoryBaseUri = repository.getUrl().normalize();
		URI directoryUri = repositoryBaseUri.resolve(path);
		URI metadataUri = repositoryBaseUri.resolve(path + "maven-metadata.xml");

		indicator.checkCanceled();
		Map<String, LocalDateTime> releaseDates = fetchReleaseDates(artifactId, directoryUri);
		Collection<String> versions;

		try {
			String metadata = fetchMetadata(artifactId, metadataUri);
			if (metadata == null || metadata.isEmpty()) {
				return Sequence.empty();
			}
			versions = parseReleaseVersions(metadata);
		} catch (ArtifactNotFoundException e) {
			if (releaseDates.isEmpty()) {
				throw e;
			}
			versions = releaseDates.keySet();
		}

		Set<Release> releases = new TreeSet<>(Comparator.reverseOrder());
		for (String version : versions) {
			Release.tryFrom(version, releaseDates.get(version), null).ifPresent(releases::add);
		}

		return Sequence.of(releases);
	}

	private List<String> parseReleaseVersions(String xml) {

		MavenMetadataProjection projection = XmlBeamProjectorFactory.INSTANCE.projectXMLString(xml,
				MavenMetadataProjection.class);

		List<String> result = new ArrayList<>();
		for (String version : projection.getVersions()) {
			String candidate = version.trim();
			if (candidate.isEmpty() || candidate.endsWith("-SNAPSHOT")) {
				continue;
			}
			if (SemanticArtifactVersion.isVersion(candidate)
					|| ReleaseTrainArtifactVersion.isReleaseTrainVersion(candidate)) {
				result.add(candidate);
			}
		}

		return result;
	}

	private Map<String, LocalDateTime> fetchReleaseDates(ArtifactId artifactId, URI uri) {

		try {
			Map<String, LocalDateTime> releaseDates = HttpClientUtil.fetchUrl(uri,
					this::configureAuthentication, request -> {
						String dateHeader = request.getConnection().getHeaderField("Date");
						String body = HttpClientUtil.readUtf8StreamCapped(request);
						return parseDirectoryListing(body, dateHeader);
					});
			return releaseDates != null ? releaseDates : Map.of();
		} catch (IOException e) {
			LOG.debug("%s: Failed to fetch release dates from %s".formatted(artifactId, uri), e);
			return Map.of();
		}
	}

	private RequestBuilder configureAuthentication(RequestBuilder requestBuilder) {

		RepositoryCredentials credentials = repository.credentials();
		if (credentials == null) {
			return requestBuilder;
		}

		URI repositoryBaseUri = repository.getUrl().normalize();
		return requestBuilder.tuner(connection -> {
			URI target = URI.create(connection.getURL().toString()).normalize();
			if (!(connection instanceof HttpURLConnection http) || !isWithinRepository(repositoryBaseUri, target)) {
				return;
			}

			http.setAuthenticator(new Authenticator() {

				@Override
				protected @Nullable PasswordAuthentication getPasswordAuthentication() {
					if (getRequestorType() != RequestorType.SERVER
							|| !"basic".equalsIgnoreCase(getRequestingScheme())) {
						return null;
					}
					return new PasswordAuthentication(credentials.username(), credentials.password().toCharArray());
				}

			});
		});
	}

	private @Nullable String fetchMetadata(ArtifactId artifactId, URI uri) throws IOException {

		try {
			return HttpClientUtil.fetchUrl(uri, this::configureAuthentication);
		} catch (HttpRequests.HttpStatusException e) {
			if (e.getStatusCode() == 404) {
				LOG.debug("[%s][%s] HTTP Status %d: %s".formatted(artifactId, getId(),
						e.getStatusCode(), uri), e);
				throw new ArtifactNotFoundException("%s: HTTP Status 404".formatted(uri), artifactId);
			}
			LOG.warn("[%s][%s] HTTP Status %d: %s".formatted(artifactId, getId(),
					e.getStatusCode(), uri), e);
			return null;
		}
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof MavenRepository that && repository.equals(that.repository);
	}

	@Override
	public int hashCode() {
		return repository.hashCode();
	}

	@Override
	public String toString() {
		return getId();
	}

	private static Map<String, LocalDateTime> parseDirectoryListing(String body, @Nullable String dateHeader) {

		Map<String, LocalDateTime> result = new HashMap<>();
		ZoneOffset serverOffset = getServerZoneOffset(dateHeader);

		body.lines().forEach(line -> {
			if (!collectReleaseDate(line, DIRECTORY_LISTING_PATTERN, DIRECTORY_LISTING_DATE_FORMATTER, serverOffset,
					result)) {
				collectReleaseDate(line, ARTIFACTORY_DIRECTORY_LISTING_PATTERN,
						DIRECTORY_LISTING_ARTIFACTORY_DATE_FORMATTER, serverOffset, result);
			}
		});

		return result;
	}

	/**
	 * Record the release date of the listing entry in {@code line} when the line
	 * follows the given listing format.
	 *
	 * @return {@literal true} if the line is an entry of the given format.
	 */
	private static boolean collectReleaseDate(String line, Pattern format, DateTimeFormatter dateFormatter,
			ZoneOffset serverOffset, Map<String, LocalDateTime> target) {

		Matcher match = format.matcher(line);
		if (!match.find()) {
			return false;
		}

		String version = match.group(1).trim();
		try {
			target.put(version, parseTimestamp(match.group(2).trim(), dateFormatter, serverOffset));
		} catch (DateTimeParseException e) {
			LOG.debug("Could not parse directory listing date for version %s".formatted(version), e);
		}
		return true;
	}

	private static LocalDateTime parseTimestamp(String timestamp, DateTimeFormatter formatter,
			ZoneOffset serverOffset) {

		return LocalDateTime.parse(timestamp, formatter).atOffset(serverOffset)
				.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
	}

	private static ZoneOffset getServerZoneOffset(@Nullable String dateHeader) {

		if (dateHeader == null || dateHeader.isBlank()) {
			return ZoneOffset.UTC;
		}
		try {
			return ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME).getOffset();
		} catch (DateTimeParseException e) {
			LOG.debug("Could not parse HTTP Date header '%s', assuming UTC".formatted(dateHeader), e);
			return ZoneOffset.UTC;
		}
	}

	/**
	 * Return whether {@code target} shares scheme, host, effective port, and base
	 * path with the repository URL, so that repository credentials may be offered.
	 */
	static boolean isWithinRepository(URI repositoryBaseUri, URI target) {

		String scheme = repositoryBaseUri.getScheme();
		if (scheme == null || !scheme.equalsIgnoreCase(target.getScheme())
				|| !HttpClientUtil.hasSameBaseUri(repositoryBaseUri, target)) {
			return false;
		}

		String basePath = repositoryBaseUri.getPath();
		String targetPath = target.getPath();
		return basePath == null || basePath.isEmpty() || targetPath != null && targetPath.startsWith(basePath);
	}

	/**
	 * Reject coordinates that are not valid Maven identifiers or could escape the
	 * repository path.
	 *
	 * @throws ArtifactNotFoundException if the group or artifact identifier is
	 * invalid.
	 */
	static void validate(ArtifactId artifactId) {

		if (!ARTIFACT_ID.matcher(artifactId.artifactId()).matches()) {
			throw new ArtifactNotFoundException("Invalid artifactId", artifactId);
		}

		if (!GROUP_ID.matcher(artifactId.groupId()).matches()) {
			throw new ArtifactNotFoundException("Invalid groupId", artifactId);
		}
	}

}
