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

import java.util.Optional;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;

/**
 * Common contract for semantic and release-train artifact versions.
 * <p>{@link Object#toString()} returns the version string.
 *
 * @author Mark Paluch
 */
public interface ArtifactVersion extends Comparable<ArtifactVersion> {

	/**
	 * Create a release version from numeric version components.
	 * @param version the numeric version components.
	 * @return an artifact version with release suffix.
	 */
	static ArtifactVersion of(NumericVersionComponents version) {
		return new SemanticArtifactVersion(version);
	}

	/**
	 * Parse the given version string.
	 *
	 * @param version the version string to parse.
	 * @return the parsed artifact version.
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
	 * Attempt to parse the given version string.
	 * @param version the version string to parse.
	 * @return the parsed artifact version, or an empty {@link Optional}.
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
	 * Create an artifact version using the requested suffix format.
	 * @param version the numeric version components.
	 * @param useModifierFormat whether to use hyphen notation for the suffix.
	 * @return an artifact version with the appropriate release suffix.
	 */
	static ArtifactVersion of(NumericVersionComponents version, boolean useModifierFormat) {
		return new SemanticArtifactVersion(version, useModifierFormat);
	}

	/**
	 * Return whether this version is strictly newer than the given version.
	 * @param other the version to compare with.
	 */
	boolean isNewer(ArtifactVersion other);

	/**
	 * Return whether the given version is a newer minor in the same version line.
	 * @param other the version to compare with.
	 */
	boolean isNewerMinor(ArtifactVersion other);

	/**
	 * Whether this version is strictly older than the given version.
	 */
	boolean isOlder(ArtifactVersion other);

	/**
	 * Return whether this version shares the same major line or release train.
	 * @param other the version to compare with.
	 */
	boolean hasSameMajor(ArtifactVersion other);

	/**
	 * Return whether this version shares the same major/minor line or release
	 * train.
	 * @param other the version to compare with.
	 */
	boolean hasSameMajorMinor(ArtifactVersion other);

	/**
	 * Return whether this version shares the same base version as the given
	 * version, ignoring any suffix or qualifier.
	 * @param other the version to compare with.
	 */
	boolean hasSameBaseVersion(ArtifactVersion other);

	/**
	 * Return whether this version is a snapshot.
	 */
	boolean isSnapshotVersion();

	/**
	 * Return whether this version is a milestone.
	 */
	boolean isMilestoneVersion();

	/**
	 * Return whether this version is a release candidate.
	 */
	boolean isReleaseCandidateVersion();

	/**
	 * Return whether this version is a preview release.
	 */
	boolean isPreview();

	/**
	 * Return whether this version is a general-availability release.
	 */
	boolean isReleaseVersion();

	/**
	 * Return whether this version is a service or bugfix release.
	 */
	boolean isBugFixVersion();

	/**
	 * Return the {@link VersioningScheme} this version belongs to.
	 *
	 * <p>The scheme is intrinsic to the version: it classifies the version's shape,
	 * independent of any other version. Wrapped versions report the scheme of the
	 * wrapped version.
	 *
	 * @return the versioning scheme; guaranteed to be not {@literal null}.
	 */
	VersioningScheme scheme();

	/**
	 * Return whether the given version can be compared with this one.
	 *
	 * <p>Two versions are comparable only when they share the same
	 * {@link VersioningScheme} and the scheme is not
	 * {@link VersioningScheme#OPAQUE}.
	 */
	default boolean canCompare(ArtifactVersion other) {
		return scheme() != VersioningScheme.OPAQUE && scheme() == other.scheme();
	}

	@Contract("null -> false")
	default boolean matches(@Nullable ArtifactVersion other) {
		return other != null && compareTo(other) == 0;
	}

	/**
	 * Return whether this version is wrapped.
	 * @return {@literal true} if this version carries a prefix; {@literal false}
	 * otherwise.
	 */
	default boolean isWrapped() {
		return false;
	}

	default ArtifactVersion getVersion() {
		return this;
	}


}
