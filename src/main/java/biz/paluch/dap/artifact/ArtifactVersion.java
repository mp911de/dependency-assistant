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

import java.util.Optional;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;

/**
 * Comparable representation of an artifact version.
 *
 * <p>Numeric and release-train versions expose semantic version relationships.
 * {@link GitRef} represents an opaque ref and must be guarded through
 * {@link #canCompare(ArtifactVersion)} before its lexical
 * {@link Comparable#compareTo(Object)} result is treated as version precedence.
 * Wrappers retain the scheme and comparison behavior of their inner version.
 *
 * <p>{@link Object#toString()} returns the version string.
 *
 * @author Mark Paluch
 */
public interface ArtifactVersion extends Comparable<ArtifactVersion> {

	/**
	 * Create a release version from numeric components.
	 */
	static ArtifactVersion of(NumericVersionComponents version) {
		return new SemanticArtifactVersion(version);
	}

	/**
	 * Parse a numeric or release-train version, preserving a leading {@code v}.
	 * @throws IllegalArgumentException if the string cannot be parsed.
	 */
	static ArtifactVersion of(String version) {
		if (version.length() > 1 && version.charAt(0) == 'v' && Character.isDigit(version.charAt(1))) {
			return new PrefixedArtifactVersion("v", of(version.substring(1)));
		}
		return SemanticArtifactVersion.isVersion(version) ? SemanticArtifactVersion.of(version)
				: ReleaseTrainArtifactVersion.of(version);
	}

	/**
	 * Parse a version, or return an empty result if absent or unrecognized.
	 */
	static Optional<ArtifactVersion> from(@Nullable String version) {

		if (StringUtils.isEmpty(version)) {
			return Optional.empty();
		}
		try {
			return Optional.of(of(version));
		} catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	/**
	 * Create a version with the requested suffix notation.
	 * @param useModifierFormat whether to use hyphen notation for the suffix.
	 */
	static ArtifactVersion of(NumericVersionComponents version, boolean useModifierFormat) {
		return new SemanticArtifactVersion(version, useModifierFormat);
	}

	/**
	 * Parse a version from a Git tag, preserving its prefix for rendering.
	 * <p>For example, {@code assertj-build-3.27.7} compares as {@code 3.27.7}.
	 * Prefixed numeric versions take precedence over release-train names.
	 * @return an empty result if the tag is absent or has no recognized version.
	 */
	static Optional<ArtifactVersion> fromTag(@Nullable String tag) {

		if (StringUtils.isEmpty(tag)) {
			return Optional.empty();
		}

		ArtifactVersion prefixed = PrefixedArtifactVersion.parseTag(tag);
		return prefixed != null ? Optional.of(prefixed) : from(tag);
	}

	/**
	 * Return whether this version is comparable to and newer than the other
	 * version.
	 */
	default boolean isNewer(ArtifactVersion other) {
		return canCompare(other) && compareTo(other) > 0;
	}

	/**
	 * Return whether the other version is a newer minor in the same version line.
	 */
	boolean isNewerMinor(ArtifactVersion other);

	/**
	 * Return whether this version is comparable to and older than the other
	 * version.
	 */
	default boolean isOlder(ArtifactVersion other) {
		return canCompare(other) && compareTo(other) < 0;
	}

	/**
	 * Return whether both versions share a major line or release train.
	 */
	boolean hasSameMajor(ArtifactVersion other);

	/**
	 * Return whether both versions share a major/minor line or release train.
	 */
	boolean hasSameMajorMinor(ArtifactVersion other);

	/**
	 * Return whether both versions share a base version, ignoring qualifiers.
	 */
	boolean hasSameBaseVersion(ArtifactVersion other);

	boolean isSnapshotVersion();

	boolean isMilestoneVersion();

	boolean isReleaseCandidateVersion();

	/**
	 * Return whether this is a milestone or release candidate.
	 */
	default boolean isPreview() {
		return isMilestoneVersion() || isReleaseCandidateVersion();
	}

	/**
	 * Return whether this is a general-availability release.
	 */
	boolean isReleaseVersion();

	/**
	 * Return whether this is a service or bugfix release.
	 */
	boolean isBugFixVersion();

	/**
	 * Return the versioning scheme. Wrappers report their delegate's scheme.
	 */
	VersioningScheme scheme();

	/**
	 * Return whether semantic ordering is defined between these versions.
	 * <p>The versions must share a non-opaque scheme.
	 */
	default boolean canCompare(ArtifactVersion other) {
		return scheme() != VersioningScheme.OPAQUE && scheme() == other.scheme();
	}

	/**
	 * Return whether the versions compare as equal.
	 * <p>Comparison equivalence may differ from {@link Object#equals(Object) value
	 * identity}.
	 */
	@Contract("null -> false")
	default boolean matches(@Nullable ArtifactVersion other) {
		return other != null && compareTo(other) == 0;
	}

	/**
	 * Return a version string for documentation and popups.
	 */
	default String toDocumentationString() {
		return toString();
	}

	default boolean isWrapped() {
		return false;
	}

	default ArtifactVersion getVersion() {
		return this;
	}

	/**
	 * Return the innermost version, or this version if unwrapped.
	 */
	default ArtifactVersion unwrap() {

		ArtifactVersion version = this;
		while (version.isWrapped()) {
			version = version.getVersion();
		}
		return version;
	}


}
