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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

import biz.paluch.dap.util.StringUtils;
import biz.paluch.dap.xml.MavenMetadataProjection;
import biz.paluch.dap.xml.XmlBeamProjectorFactory;
import com.intellij.credentialStore.Credentials;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.JdkProxyProvider;
import com.intellij.util.net.ProxyAuthentication;
import org.jspecify.annotations.Nullable;

/**
 * Release source that fetches releases from a remote Maven repository.
 *
 * @author Mark Paluch
 */
public class RemoteRepositoryReleaseSource implements ReleaseSource {

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

	private static final long MAX_RESPONSE_BODY_BYTES = 5 * 1024 * 1024;

	private static final int MAX_REDIRECT_HOPS = 10;

	private static final String USER_AGENT = getUserAgent();

	private final RemoteRepository repository;

	public RemoteRepositoryReleaseSource(RemoteRepository repository) {
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
		URI repositoryBaseUri = URI.create(base).normalize();
		URI metadataUri = repositoryBaseUri.resolve(metadataPath);
		URI directoryUri = repositoryBaseUri.resolve(path);
		String xml = fetchUrl(artifactId, metadataUri, repository.credentials(), true, repositoryBaseUri);
		String directoryListing = fetchUrl(artifactId, directoryUri, repository.credentials(), false,
				repositoryBaseUri);

		if (StringUtils.isEmpty(xml)) {
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
			boolean failOnNotFound, URI repositoryBaseUri) {

		String url = uri.toASCIIString();
		try {
			if (credentials != null) {
				return fetchWithCredentialBinding(artifactId, uri, credentials, failOnNotFound, repositoryBaseUri);
			}
			return fetchWithStandardRedirects(artifactId, uri, failOnNotFound);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.debug("HTTP fetch interrupted: " + url, e);
			return null;
		} catch (UncheckedIOException e) {
			LOG.debug("HTTP fetch failed: " + url, e.getCause() != null ? e.getCause() : e);
			return null;
		} catch (IOException e) {
			LOG.debug("HTTP fetch failed: " + url, e);
			return null;
		}
	}

	private static @Nullable String fetchWithStandardRedirects(ArtifactId artifactId, URI uri, boolean failOnNotFound)
			throws IOException, InterruptedException {

		HttpClient client = HttpClient.newBuilder() //
				.proxy(JdkProxyProvider.getInstance().getProxySelector()) //
				.authenticator(new RepositoryAuthenticator()) //
				.connectTimeout(Duration.ofSeconds(10)) //
				.followRedirects(HttpClient.Redirect.NORMAL) //
				.build();

		HttpRequest request = HttpRequest.newBuilder(uri).header("User-Agent", USER_AGENT)
				.timeout(Duration.ofSeconds(10))
				.GET().build();

		HttpResponse<String> response = client.send(request, cappedUtf8BodyHandler());

		if (response.statusCode() >= 200 && response.statusCode() < 300) {
			return response.body();
		}

		if (failOnNotFound && response.statusCode() == 404) {
			throw new ArtifactNotFoundException(uri + ": HTTP Status 404", artifactId);
		}
		LOG.debug("HTTP " + response.statusCode() + " fetching: " + uri);
		return null;
	}

	private static @Nullable String fetchWithCredentialBinding(ArtifactId artifactId, URI uri,
			RepositoryCredentials credentials, boolean failOnNotFound, URI repositoryBaseUri)
			throws IOException, InterruptedException {

		HttpClient client = HttpClient.newBuilder() //
				.proxy(JdkProxyProvider.getInstance().getProxySelector()) //
				.authenticator(new RepositoryAuthenticator()) //
				.connectTimeout(Duration.ofSeconds(10)) //
				.followRedirects(HttpClient.Redirect.NEVER) //
				.build();

		URI current = uri;
		for (int hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {

			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(current).header("User-Agent", USER_AGENT)
					.timeout(Duration.ofSeconds(10)).GET();

			if (repositoryCredentialHostMatches(repositoryBaseUri, current)) {
				requestBuilder.header("Authorization", basicAuthHeader(credentials));
			}

			HttpResponse<String> response = client.send(requestBuilder.build(), cappedUtf8BodyHandler());
			int status = response.statusCode();

			if (status >= 200 && status < 300) {
				return response.body();
			}

			if (failOnNotFound && status == 404) {
				throw new ArtifactNotFoundException(current + ": HTTP Status 404", artifactId);
			}

			if (status >= 300 && status < 400) {
				Optional<String> location = response.headers().firstValue("location");
				if (location.isEmpty()) {
					LOG.debug("HTTP " + status + " without Location header fetching: " + current);
					return null;
				}
				URI next = current.resolve(URI.create(location.get().trim()));
				if (!repositoryCredentialHostMatches(repositoryBaseUri, next)) {
					LOG.debug("Refusing cross-host redirect from " + current + " to " + next);
					return null;
				}
				current = next;
				continue;
			}

			LOG.debug("HTTP " + status + " fetching: " + current);
			return null;
		}

		LOG.debug("Too many redirects fetching: " + uri);
		return null;
	}

	private static BodyHandler<String> cappedUtf8BodyHandler() {

		return responseInfo -> BodySubscribers.mapping(BodySubscribers.ofInputStream(), in -> {
			try {
				return readUtf8StreamCapped(in, MAX_RESPONSE_BODY_BYTES);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	private static String readUtf8StreamCapped(InputStream in, long maxBytes) throws IOException {

		try (in) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[16384];
			long total = 0;
			while (true) {
				int n = in.read(buf);
				if (n == -1) {
					break;
				}
				total += n;
				if (total > maxBytes) {
					throw new IOException("Response body exceeds maximum size");
				}
				out.write(buf, 0, n);
			}
			return out.toString(StandardCharsets.UTF_8);
		}
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

	private static class RepositoryAuthenticator extends Authenticator {

		private final ProxyAuthentication proxyAuthentication = ProxyAuthentication.getInstance();

		RepositoryAuthenticator() {
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

		return userAgent;
	}

}
