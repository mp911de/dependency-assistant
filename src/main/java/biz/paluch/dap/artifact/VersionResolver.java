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

import biz.paluch.dap.artifact.xml.MavenMetadataProjection;
import biz.paluch.dap.artifact.xml.XmlBeamProjectorFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;

/**
 * Resolves available versions by fetching maven-metadata.xml from repositories.
 */
public class VersionResolver {

	private static final Logger LOG = Logger.getInstance(VersionResolver.class);

	private final List<RemoteRepository> repositories;

	private static final Pattern DIRECTORY_LISTING_PATTERN = Pattern
			.compile("<a (?>[^>]+)>([^/]+)/</a>(?>\\s*)(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})(?>\\s*)(?>-)?");

	private static final DateTimeFormatter DIRECTORY_LISTING_DATE_FORMATTER = DateTimeFormatter
			.ofPattern("uuuu-MM-dd HH:mm");

	private static final Pattern ARTIFACTORY_DIRECTORY_LISTING_PATTERN = Pattern
			.compile("<a (?>[^>]+)>([^/]+)/</a>(?>\\s*)(\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2})(?>\\s*)(?>-)?");

	private static final DateTimeFormatter DIRECTORY_LISTING_ARTIFACTORY_DATE_FORMATTER = DateTimeFormatter
			.ofPattern("dd-MMM-uuuu HH:mm", Locale.ENGLISH);

	public VersionResolver(List<RemoteRepository> repositoryUrls) {
		this.repositories = repositoryUrls;
	}

	/**
	 * Returns version suggestions: same major.minor as current plus all newer versions. Excludes SNAPSHOTs. Release dates
	 * are parsed from the dependency directory listing (HTML).
	 */
	public List<VersionOption> getVersionSuggestions(ArtifactId coordinate,
			ArtifactVersion currentVersion) {

		String path = coordinate.groupId().replace(".", "/") + "/" + coordinate.artifactId() + "/";
		String metadataPath = path + "maven-metadata.xml";

		Map<String, LocalDateTime> releaseDates = new HashMap<>();
		Set<ArtifactVersion> versions = new TreeSet<>(Comparator.reverseOrder());

		for (RemoteRepository repository : repositories) {
			String baseUrl = repository.url();
			String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
			URI metadataUri = URI.create(base).resolve(metadataPath);
			URI directoryUri = URI.create(base).resolve(path);
			try {
				String xml = fetchUrl(metadataUri);
				String directoryListing = fetchUrl(directoryUri);
				if (xml == null) {
					continue;
				}

				releaseDates.putAll(parseDirectoryListingDates(directoryListing));
				versions.addAll(parseAllVersionsFromMetadata(xml));

			} catch (Exception e) {
				LOG.debug("Skipping repository " + baseUrl + " for " + coordinate, e);
			}
		}

		if (currentVersion != null) {
			versions.add(currentVersion);
		}
		List<VersionOption> result = new ArrayList<>();
		for (ArtifactVersion av : versions) {
			result.add(new VersionOption(av, releaseDates.get(av.toString())));
		}
		result.sort(Comparator.<VersionOption> naturalOrder().reversed());
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
			String trimmed = v != null ? v.trim() : "";
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

		if (html == null) {
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
						result.put(version, LocalDateTime.from(DIRECTORY_LISTING_ARTIFACTORY_DATE_FORMATTER.parse(dateStr)));
					} catch (Exception e) {
						LOG.debug("Could not parse directory listing date for version " + version, e);
					}
				}
			}
		}
		return result;
	}

	private static @Nullable String fetchUrl(URI uri) {

		String url = uri.toASCIIString();
		try {
			return HttpRequests.request(url).connectTimeout(10_000).readTimeout(10_000).productNameAsUserAgent()
					.connect(request -> {
						try (var stream = request.getInputStream()) {
							return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
						}
					});
		} catch (IOException e) {
			LOG.debug("HTTP fetch failed: " + url, e);
			return null;
		}
	}

}
