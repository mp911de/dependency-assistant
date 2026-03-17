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
package biz.paluch.mavenupdater.dependencies;

import biz.paluch.mavenupdater.dependencies.xml.MavenMetadataProjection;
import biz.paluch.mavenupdater.dependencies.xml.XmlBeamProjectorFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Resolves available versions by fetching maven-metadata.xml from repositories.
 */
public class VersionResolver {

	private final List<String> repositoryUrls;

	private static final Pattern DIRECTORY_LISTING_LINE_REGEX = Pattern
			.compile("<a (?>[^>]+)>([^/]+)/</a>(?>\\s*)(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})(?>\\s*)(?>-)?");

	private static final DateTimeFormatter DIRECTORY_LISTING_DATE_FORMATTER = DateTimeFormatter
			.ofPattern("uuuu-MM-dd HH:mm");

	public VersionResolver(List<String> repositoryUrls) {
		this.repositoryUrls = repositoryUrls;
	}

	/**
	 * Returns version suggestions: same major.minor as current plus all newer versions. Excludes SNAPSHOTs. Release dates
	 * are parsed from the dependency directory listing (HTML).
	 */
	public List<VersionOption> getVersionSuggestions(String groupId, String artifactId,
			@Nullable ArtifactVersion currentVersion) {

		String path = groupId.replace(".", "/") + "/" + artifactId + "/";
		String metadataPath = path + "/maven-metadata.xml";

		Map<String, LocalDateTime> releaseDates = new HashMap<>();
		Set<ArtifactVersion> versions = new TreeSet<>(Comparator.reverseOrder());

		for (String baseUrl : repositoryUrls) {
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

			} catch (Exception ignored) {}
		}

		if (currentVersion != null) {
			versions.add(currentVersion);
		}
		List<VersionOption> result = new ArrayList<>();
		for (ArtifactVersion av : versions) {
			result.add(new VersionOption(av, releaseDates.get(av.toString())));
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
			String trimmed = v != null ? v.trim() : "";
			if (trimmed.endsWith("-SNAPSHOT") || trimmed.isEmpty()) {
				continue;
			}
			if (ArtifactVersion.isVersion(trimmed)) {
				result.add(ArtifactVersion.of(trimmed));
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
			Matcher match = DIRECTORY_LISTING_LINE_REGEX.matcher(line);
			if (!match.find()) {
				continue;
			}
			String version = match.group(1) != null ? match.group(1).trim() : null;
			String dateStr = match.group(2) != null ? match.group(2).trim() : null;
			if (version == null || dateStr == null) {
				continue;
			}
			try {
				result.put(version, LocalDateTime.from(DIRECTORY_LISTING_DATE_FORMATTER.parse(dateStr)));
			} catch (Exception ignored) {}
		}
		return result;
	}

	private static @Nullable String fetchUrl(URI uri) {
		try {

			HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(10_000);
			conn.setReadTimeout(10_000);
			conn.setRequestProperty("User-Agent", "Maven-Updater-Plugin/1.0");
			try (java.io.InputStream input = conn.getInputStream();
					java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(input))) {
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					sb.append(line).append('\n');
				}
				return sb.toString();
			}
		} catch (Exception e) {
			return null;
		}
	}

}
