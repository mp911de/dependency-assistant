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

package biz.paluch.dap.gradle.wrapper;

import java.util.Comparator;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.gradle.GradleDistributionService;
import biz.paluch.dap.state.Cache;
import com.intellij.lang.properties.IProperty;
import org.jspecify.annotations.Nullable;

/**
 * Supported Gradle wrapper URL property.
 *
 * @author Mark Paluch
 */
enum WrapperProperty {

	/**
	 * {@literal distributionUrl} property.
	 */
	DISTRIBUTION("distributionUrl", "distributionSha256Sum", "9.5.1");

	static final ArtifactId GRADLE_DISTRIBUTION = GradleDistributionService.GRADLE_DISTRIBUTION;

	private static final WrapperProperty[] VALUES = values();

	private static final String DEFAULT_URL_BASE = "https://services.gradle.org/distributions/";

	private final String key;

	private final String shaKey;

	private final ArtifactVersion defaultVersion;

	WrapperProperty(String key, String shaKey, String defaultVersion) {
		this.key = key;
		this.shaKey = shaKey;
		this.defaultVersion = ArtifactVersion.of(defaultVersion);
	}

	static @Nullable WrapperProperty forKey(@Nullable String key) {

		for (WrapperProperty value : VALUES) {
			if (value.key().equals(key)) {
				return value;
			}
		}
		return null;
	}

	static boolean isWrapperProperty(IProperty property) {
		return forKey(property.getUnescapedKey()) != null;
	}

	static String[] propertyNames() {
		return new String[] {DISTRIBUTION.key()};
	}

	String key() {
		return key;
	}

	String shaKey() {
		return shaKey;
	}

	ArtifactId artifactId() {
		return GRADLE_DISTRIBUTION;
	}

	ArtifactVersion defaultVersion() {
		return defaultVersion;
	}

	String canonicalFileName(String version, @Nullable String preservedFlavor) {
		String flavor = isSupportedFlavor(preservedFlavor) ? preservedFlavor : "bin";
		return "gradle-%s-%s.zip".formatted(version, flavor);
	}

	boolean isCanonicalFileName(String fileName, String version) {
		return fileName.equals(canonicalFileName(version, "bin"))
				|| fileName.equals(canonicalFileName(version, "all"));
	}

	boolean isSupportedFlavor(@Nullable String flavor) {
		return "bin".equals(flavor) || "all".equals(flavor);
	}

	String defaultUrl(String version) {
		return DEFAULT_URL_BASE + canonicalFileName(version, "bin");
	}

	ArtifactRelease getLatestArtifactRelease(Cache cache) {
		return new ArtifactRelease(artifactId(), getLatestRelease(cache));
	}

	Release getLatestRelease(Cache cache) {
		return cache.getReleases(artifactId()).stream()
				.filter(Predicate.not(Release::isPreview)).max(Comparator.naturalOrder())
				.orElseGet(() -> Release.from(defaultVersion, null));
	}

}
