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

package biz.paluch.dap.maven.wrapper;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.state.Cache;
import com.intellij.lang.properties.IProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Supported Maven Wrapper URL property.
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

	static boolean isWrapperProperty(IProperty property) {
		return forKey(property.getUnescapedKey()) != null;
	}

	/**
	 * Return the matching property kind, or {@literal null} if the key is absent or
	 * unknown.
	 */
	static @Nullable WrapperProperty forKey(@Nullable String key) {

		for (WrapperProperty value : VALUES) {
			if (value.key().equals(key)) {
				return value;
			}
		}
		return null;
	}

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
	 * Return the canonical filename for the version.
	 * @param preservedExtension a supported extension to retain, or {@literal null}
	 * for the default. Unsupported extensions also use the default.
	 */
	public String canonicalFileName(String version, @Nullable String preservedExtension) {
		String extension = preservedExtension != null && supportedExtensions.contains(preservedExtension)
				? preservedExtension
				: defaultExtension;
		return baseFileName.formatted(version) + '.' + extension;
	}

	/**
	 * Test an exact filename match against the supported archive extensions.
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
	 * Return the lowercase supported extension, ignoring filename case.
	 * @return {@literal null} if no supported extension matches.
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
	 * Return this artifact with {@link #getLatestRelease(Cache)}.
	 */
	public ArtifactRelease getLatestArtifactRelease(Cache cache) {
		return new ArtifactRelease(artifactId(), getLatestRelease(cache));
	}

	/**
	 * Return the latest cached non-preview release, or the built-in fallback.
	 */
	public Release getLatestRelease(Cache cache) {
		return cache.getReleases(artifactId())
				.stream()
				.filter(Predicate.not(Release::isPreview)).max(Comparator.naturalOrder())
				.orElseGet(() -> Release.from(defaultVersion, null));
	}



}
