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

package biz.paluch.dap.maven.wrapper;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RemoteRepositoryReleaseSource;
import biz.paluch.dap.artifact.RepositoryCredentials;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.util.PropertyUtils;
import biz.paluch.dap.util.StringUtils;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * Supported Maven wrapper URL property.
 *
 * <p>Each value connects the property key in
 * {@code .mvn/wrapper/maven-wrapper.properties} with the artifact whose version
 * is encoded in the URL.
 *
 * @author Mark Paluch
 */
enum WrapperProperty {

	DISTRIBUTION("distributionUrl", "distributionSha256Sum", ArtifactId.of("org.apache.maven", "apache-maven"),
			"org/apache/maven", "apache-maven-%s-bin", "zip", Set.of("tar.gz", "zip"), "3.9.15"),

	WRAPPER("wrapperUrl", "wrapperSha256Sum", ArtifactId.of("org.apache.maven.wrapper", "maven-wrapper"),
			"org/apache/maven/wrapper", "maven-wrapper-%s", "jar", Set.of("jar"), "3.3.4");

	private static final WrapperProperty[] VALUES = values();

	private final String key;

	private final String shaKey;

	private final ArtifactId artifactId;

	private final String canonicalGroupPath;

	private final String baseFileName;

	private final String defaultExtension;

	private final Set<String> supportedExtensions;

	private final ArtifactVersion defaultVersion;

	WrapperProperty(String key, String shaKey, ArtifactId artifactId, String canonicalGroupPath,
			String baseFileName, String defaultExtension, Set<String> supportedExtensions, String defaultVersion) {

		Assert.isTrue(supportedExtensions.contains(defaultExtension),
				"%s's supportedExtensions does not contain the default extension %s".formatted(key, defaultExtension));
		this.key = key;
		this.shaKey = shaKey;
		this.artifactId = artifactId;
		this.canonicalGroupPath = canonicalGroupPath;
		this.baseFileName = baseFileName;
		this.defaultExtension = defaultExtension;
		this.supportedExtensions = supportedExtensions;
		this.defaultVersion = ArtifactVersion.of(defaultVersion);
	}

	/**
	 * Return whether the property is a supported wrapper URL property.
	 * @param property the property to inspect.
	 * @return {@code true} if the property key is supported.
	 */
	static boolean isWrapperProperty(IProperty property) {
		return forKey(property.getUnescapedKey()) != null;
	}

	/**
	 * Return the {@link WrapperProperty} that matches the given property key, or
	 * {@literal null} when the key is unknown.
	 * @param key the property key to inspect; can be {@literal null}.
	 * @return the matching {@link WrapperProperty}, or {@literal null}.
	 */
	static @Nullable WrapperProperty forKey(@Nullable String key) {

		for (WrapperProperty value : VALUES) {
			if (value.key().equals(key)) {
				return value;
			}
		}
		return null;
	}

	/**
	 * Return supported property names.
	 * @return the supported property keys.
	 */
	public static String[] propertyNames() {
		return new String[] {
				DISTRIBUTION.key(), WRAPPER.key()
		};
	}

	String key() {
		return key;
	}

	String shaKey() {
		return shaKey;
	}

	ArtifactId artifactId() {
		return artifactId;
	}

	String canonicalGroupPath() {
		return canonicalGroupPath;
	}

	String canonicalArtifactId() {
		return artifactId.artifactId();
	}

	String defaultExtension() {
		return defaultExtension;
	}

	/**
	 * Return the canonical file name for this wrapper property, the given version,
	 * and an optional preserved extension.
	 *
	 * <p>If {@code preservedExtension} is one of the supported extensions for this
	 * property, it is honored; otherwise the default extension is used.
	 * @param version the canonical version; must not be {@literal null}.
	 * @param preservedExtension the extension to preserve, can be {@literal null}.
	 * @return the canonical file name.
	 */
	public String canonicalFileName(String version, @Nullable String preservedExtension) {
		String extension = preservedExtension != null && supportedExtensions.contains(preservedExtension)
				? preservedExtension
				: defaultExtension;
		return baseFileName.formatted(version) + '.' + extension;
	}

	/**
	 * Return whether {@code fileName} is one of the canonical file names for this
	 * wrapper property at the given version.
	 * @param fileName the file name to check; must not be {@literal null}.
	 * @param version the canonical version; must not be {@literal null}.
	 * @return {@literal true} if the file name is canonical.
	 */
	public boolean isCanonicalFileName(String fileName, String version) {
		String base = baseFileName.formatted(version);
		for (String extension : supportedExtensions) {
			if (fileName.equals(base + '.' + extension)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return the supported extension if the {@code fileName} ends with a supported
	 * extension, or {@literal null} otherwise.
	 * @param fileName the file name to inspect.
	 * @return the supported extension if the {@code fileName} ends with a supported
	 * extension, or {@literal null} otherwise.
	 */
	public @Nullable String getSupportedExtension(String fileName) {

		String lower = fileName.toLowerCase(Locale.ROOT);
		for (String supportedExtension : supportedExtensions) {
			if (lower.endsWith("." + supportedExtension)) {
				return supportedExtension;
			}
		}

		return null;
	}

	/**
	 * Parse the property as this wrapper property.
	 * @param property the property to parse.
	 * @return the parsed wrapper entry, or {@literal null} if the property does not
	 * match this wrapper property.
	 */
	public @Nullable WrapperEntry parseProperty(PropertyImpl property) {

		PropertyValueImpl value = PropertyUtils.findPropertyValue(property);
		if (!key().equals(property.getUnescapedKey()) || value == null
				|| PropertyUtils.containsLineContinuation(value.getText())) {
			return null;
		}

		String decoded = property.getUnescapedValue();
		if (StringUtils.isEmpty(decoded)) {
			return null;
		}

		URI uri;
		try {
			uri = URI.create(decoded);
		} catch (IllegalArgumentException malformed) {
			return null;
		}

		RemoteRepository repository;
		if (TrustedProjects.isProjectTrusted(property.getProject())) {
			RepositoryCredentials credentials = parseCredentials(uri);
			repository = parseRemoteRepository(uri, credentials);
		} else {
			repository = RemoteRepository.mavenCentral();
		}

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(decoded);
		if (!matcher.find()) {
			return null;
		}

		String pathVersion = matcher.group("version1");
		String fileVersion = matcher.group("version2");
		return new WrapperEntry(this, property, value, repository, pathVersion, fileVersion);
	}

	/**
	 * Return the latest {@link ArtifactRelease}. Returns a default release if
	 * {@link Cache} does not contain any release for the artifact.
	 * @param cache the state cache to query for releases; must not be
	 * {@literal null}.
	 * @return the latest release.
	 */
	public ArtifactRelease getLatestArtifactRelease(Cache cache) {
		return new ArtifactRelease(artifactId(), getLatestRelease(cache));
	}

	/**
	 * Return the latest {@link Release}. Returns a default release if {@link Cache}
	 * does not contain any release for the artifact.
	 * @param cache the state cache to query for releases; must not be
	 * {@literal null}.
	 * @return the latest release.
	 */
	public Release getLatestRelease(Cache cache) {
		return cache.getReleases(artifactId())
				.stream()
				.filter(Predicate.not(Release::isPreview)).max(Comparator.naturalOrder())
				.orElseGet(() -> Release.from(defaultVersion, null));
	}

	RemoteRepository parseRemoteRepository(URI uri, @Nullable RepositoryCredentials credentials) {
		URI defaultMaven = URI.create(RemoteRepository.mavenCentral().url());
		if (credentials == null && RemoteRepositoryReleaseSource.hasSameBaseUri(uri, defaultMaven)) {
			return RemoteRepository.mavenCentral();
		}
		return new RemoteRepository(MavenWrapperUtils.REPOSITORY_ID, uri.toASCIIString(), credentials);
	}

	private @Nullable RepositoryCredentials parseCredentials(URI uri) {

		String userInfo = uri.getUserInfo();
		if (StringUtils.hasText(userInfo)) {
			int colon = userInfo.indexOf(':');
			if (colon >= 0) {
				String user = URLDecoder.decode(userInfo.substring(0, colon), StandardCharsets.UTF_8);
				String pass = URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8);
				return new RepositoryCredentials(MavenWrapperUtils.REPOSITORY_ID, user, pass, List.of(uri));
			}
			String user = URLDecoder.decode(userInfo, StandardCharsets.UTF_8);
			return new RepositoryCredentials(MavenWrapperUtils.REPOSITORY_ID, user, "", List.of(uri));
		}
		return null;
	}

}
