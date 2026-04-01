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
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

import org.springframework.util.StringUtils;

import com.intellij.credentialStore.Credentials;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.JdkProxyProvider;
import com.intellij.util.net.ProxyAuthentication;

/**
 * Release source that fetches releases from a Maven repository.
 *
 * @author Mark Paluch
 */
public class MavenRepositoryReleaseSource implements ReleaseSource {

	private static final Logger LOG = Logger.getInstance(MavenRepositoryReleaseSource.class);

	private static final Pattern DIRECTORY_LISTING_PATTERN = Pattern
			.compile("<a (?>[^>]+)>([^/]+)/</a>(?>\\s*)(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})(?>\\s*)(?>-)?");

	private static final DateTimeFormatter DIRECTORY_LISTING_DATE_FORMATTER = DateTimeFormatter
			.ofPattern("uuuu-MM-dd HH:mm");

	private static final Pattern ARTIFACTORY_DIRECTORY_LISTING_PATTERN = Pattern
			.compile("<a (?>[^>]+)>([^/]+)/</a>(?>\\s*)(\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2})(?>\\s*)(?>-)?");

	private static final DateTimeFormatter DIRECTORY_LISTING_ARTIFACTORY_DATE_FORMATTER = DateTimeFormatter
			.ofPattern("dd-MMM-uuuu HH:mm", Locale.ENGLISH);

	private static final String USER_AGENT = getUserAgent();

	private final RemoteRepository repository;

	public MavenRepositoryReleaseSource(RemoteRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId) {

		String path = artifactId.groupId().replace(".", "/") + "/" + artifactId.artifactId() + "/";
		String metadataPath = path + "maven-metadata.xml";

		Map<String, LocalDateTime> releaseDates = new HashMap<>();
		Set<ArtifactVersion> versions = new TreeSet<>(Comparator.reverseOrder());

		String baseUrl = repository.url();
		String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
		URI metadataUri = URI.create(base).resolve(metadataPath);
		URI directoryUri = URI.create(base).resolve(path);
		String xml = fetchUrl(artifactId, metadataUri, repository.credentials(), true);
		String directoryListing = fetchUrl(artifactId, directoryUri, repository.credentials(), false);

		if (!StringUtils.hasText(xml)) {
			return List.of();
		}

		releaseDates.putAll(parseDirectoryListingDates(directoryListing));
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

		if (!StringUtils.hasText(html)) {
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

	private static @Nullable String fetchUrl(ArtifactId artifactId, URI uri, @Nullable RepositoryCredentials credentials,
			boolean failOnNotFound) {

		String url = uri.toASCIIString();
		try {
			HttpClient client = HttpClient.newBuilder() //
					.proxy(JdkProxyProvider.getInstance().getProxySelector()) //
					.authenticator(new RepositoryAuthenticator(credentials)) //
					.connectTimeout(Duration.ofSeconds(10)) //
					.followRedirects(HttpClient.Redirect.NORMAL) //
					.build();

			HttpRequest request = HttpRequest.newBuilder(uri).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(10))
					.GET().build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				return response.body();
			}

			if (failOnNotFound && response.statusCode() == 404) {
				throw new ArtifactNotFoundException(uri + ": HTTP Status 404", artifactId);
			}
			LOG.debug("HTTP " + response.statusCode() + " fetching: " + url);
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.debug("HTTP fetch interrupted: " + url, e);
			return null;
		} catch (IOException e) {
			LOG.debug("HTTP fetch failed: " + url, e);
			return null;
		}
	}

	private static class RepositoryAuthenticator extends Authenticator {

		private final @Nullable RepositoryCredentials credentials;
		private final ProxyAuthentication proxyAuthentication = ProxyAuthentication.getInstance();

		RepositoryAuthenticator(@Nullable RepositoryCredentials credentials) {
			this.credentials = credentials;
		}

		@Override
		protected PasswordAuthentication getPasswordAuthentication() {

			if (getRequestorType() == RequestorType.PROXY) {

				Credentials knownAuthentication = proxyAuthentication.getKnownAuthentication(getRequestingHost(),
						getRequestingPort());

				if (knownAuthentication == null || knownAuthentication.getUserName() == null) {
					return null;
				}
				return new PasswordAuthentication(knownAuthentication.getUserName(),
						knownAuthentication.getPassword() != null ? knownAuthentication.getPassword().toCharArray() : new char[0]);
			}

			if (getRequestorType() == RequestorType.SERVER && credentials != null) {
				return new PasswordAuthentication(credentials.username(), credentials.password().toCharArray());
			}

			return null;
		}

	}

	private static String getUserAgent() {

		String userAgent;
		Application app = ApplicationManager.getApplication();
		if (app != null && !app.isDisposed()) {
			String productName = ApplicationNamesInfo.getInstance().getFullProductName();
			String version = ApplicationInfo.getInstance().getBuild().asStringWithoutProductCode();
			userAgent = productName + '/' + version;
		} else {
			userAgent = "IntelliJ";
		}

		String currentBuildUrl = System.getenv("BUILD_URL");
		if (currentBuildUrl != null) {
			userAgent += " (" + currentBuildUrl + ")";
		}

		return userAgent;
	}

}
