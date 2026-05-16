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
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RemoteRepositoryReleaseSource;
import biz.paluch.dap.artifact.RepositoryCredentials;
import biz.paluch.dap.util.StringUtils;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

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
			"org/apache/maven", "apache-maven-%s-bin", "tar.gz", Set.of("tar.gz", "zip")),

	WRAPPER("wrapperUrl", "wrapperSha256Sum", ArtifactId.of("org.apache.maven.wrapper", "maven-wrapper"),
			"org/apache/maven/wrapper", "maven-wrapper-%s", "jar", Set.of("jar"));

	private static final WrapperProperty[] VALUES = values();

	private final String key;

	private final String shaKey;

	private final ArtifactId artifactId;

	private final String canonicalGroupPath;

	private final String baseFileName;

	private final String defaultExtension;

	private final Set<String> supportedExtensions;

	WrapperProperty(String key, String shaKey, ArtifactId artifactId, String canonicalGroupPath,
			String baseFileName, String defaultExtension, Set<String> supportedExtensions) {
		this.key = key;
		this.shaKey = shaKey;
		this.artifactId = artifactId;
		this.canonicalGroupPath = canonicalGroupPath;
		this.baseFileName = baseFileName;
		this.defaultExtension = defaultExtension;
		this.supportedExtensions = supportedExtensions;
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
	 * Try to parse the property against every supported wrapper URL property and
	 * return the first match.
	 * @param property the property to parse.
	 * @return the parsed wrapper entry, or {@code null} if no supported wrapper
	 * property matches or the value cannot be parsed.
	 */
	public static @Nullable WrapperEntry parse(IProperty property) {

		for (WrapperProperty wp : WrapperProperty.values()) {
			WrapperEntry entry = wp.parseProperty((PropertyImpl) property);
			if (entry != null) {
				return entry;
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

	/**
	 * Return whether the raw property text contains an unescaped trailing backslash
	 * followed by a CR/LF the Java PropertyFile line-continuation idiom. Such
	 * values are silently rejected by the parser.
	 * @param rawText the raw PSI text of a {@code PropertyValueImpl}.
	 */
	static boolean containsLineContinuation(String rawText) {

		int index = 0;
		while (index < rawText.length()) {

			char c = rawText.charAt(index);
			if (c != '\\') {
				index++;
				continue;
			}

			int run = 0;
			int i = index;
			while (i < rawText.length() && rawText.charAt(i) == '\\') {
				run++;
				i++;
			}
			if (run % 2 == 1 && i < rawText.length()) {
				char next = rawText.charAt(i);
				if (next == '\n' || next == '\r') {
					return true;
				}
			}
			index = i;
		}
		return false;
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

	/**
	 * Return the canonical slash-separated Maven group path for this wrapper
	 * property.
	 * @return the canonical group path.
	 */
	String canonicalGroupPath() {
		return canonicalGroupPath;
	}

	/**
	 * Return the canonical artifact id for this wrapper property.
	 * @return the canonical artifact id.
	 */
	String canonicalArtifactId() {
		return artifactId.artifactId();
	}

	/**
	 * Return the canonical file name for this wrapper property, the given version,
	 * and an optional preserved extension.
	 *
	 * <p>If {@code preservedExtension} is one of the supported extensions for this
	 * property, it is honoured; otherwise the default extension is used.
	 * @param version the canonical version; must not be {@literal null}.
	 * @param preservedExtension the extension to preserve, can be {@literal null}.
	 * @return the canonical file name.
	 */
	String canonicalFileName(String version, @Nullable String preservedExtension) {
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
	boolean isCanonicalFileName(String fileName, String version) {
		String base = baseFileName.formatted(version);
		for (String extension : supportedExtensions) {
			if (fileName.equals(base + '.' + extension)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Parse the property as this wrapper property.
	 * @param property the property to parse.
	 * @return the parsed wrapper entry, or {@code null} if the property does not
	 * match this wrapper property.
	 */
	public @Nullable WrapperEntry parseProperty(PropertyImpl property) {

		PropertyValueImpl value = MavenWrapperUtils.findPropertyValue(property);
		if (!key().equals(property.getUnescapedKey()) || value == null
				|| containsLineContinuation(value.getText())) {
			return null;
		}

		String decoded = property.getUnescapedValue();
		if (StringUtils.isEmpty(decoded)) {
			return null;
		}

		try {
			URI uri = URI.create(decoded);
			RepositoryCredentials credentials = parseCredentials(property.getProject(), uri);
			RemoteRepository repository = parseRemoteRepository(uri, credentials);

			Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(decoded);
			if (!matcher.find()) {
				return null;
			}

			String pathVersion = matcher.group("version1");
			String fileVersion = matcher.group("version2");
			return new WrapperEntry(this, property, value, repository, pathVersion, fileVersion);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private RemoteRepository parseRemoteRepository(URI uri, @Nullable RepositoryCredentials credentials) {

		URI defaultMaven = URI.create(RemoteRepository.mavenCentral().url());
		if (credentials == null && RemoteRepositoryReleaseSource.hasSameBaseUri(uri, defaultMaven)) {
			return RemoteRepository.mavenCentral();
		}

		return new RemoteRepository(MavenWrapperUtils.REPOSITORY_ID, uri.toASCIIString(), credentials);
	}

	private @Nullable RepositoryCredentials parseCredentials(Project project, URI uri) {

		if (!TrustedProjects.isProjectTrusted(project)) {
			return null;
		}

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
