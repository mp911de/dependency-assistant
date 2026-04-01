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

/**
 * Interface representing an artifact version. Supports semantic versions (e.g. {@code 1.2.3.RELEASE}, {@code 2.0.0-M1})
 * and release-train versions (e.g. {@code Aluminium-M1}, {@code Bismuth-SR1}).
 *
 * @author Mark Paluch
 */
public interface ArtifactVersion extends Comparable<ArtifactVersion>, HasVersion {

	/**
	 * Create a release version from a numeric {@link NumericVersionComponents} (e.g. {@code 1.2.3} →
	 * {@code 1.2.3.RELEASE}).
	 *
	 * @param version the major.minor.bugfix version, must not be {@code null}
	 * @return an artifact version with release suffix
	 */
	static ArtifactVersion of(NumericVersionComponents version) {
		return new SemanticArtifactVersion(version);
	}

	/**
	 * Parse a version string into an {@link ArtifactVersion}. Accepts semantic versions (e.g. {@code 1.2.3.RELEASE},
	 * {@code 2.0.0-M1}) and release-train versions (e.g. {@code Aluminium-M1}, {@code Bismuth-SR1}).
	 *
	 * @param version the version string to parse, must not be {@code null} or empty
	 * @return the parsed artifact version
	 * @throws IllegalArgumentException if the string cannot be parsed as a supported version format
	 */
	static ArtifactVersion of(String version) {
		return SemanticArtifactVersion.isVersion(version) ? SemanticArtifactVersion.of(version)
				: biz.paluch.dap.artifact.ReleaseTrainArtifactVersion.of(version);
	}

	/**
	 * Create an artifact version from a numeric {@link NumericVersionComponents} with optional modifier format. When
	 * {@code useModifierFormat} is {@code true}, the version is rendered with a hyphen (e.g. {@code 1.2.3-SNAPSHOT});
	 * otherwise with a dot (e.g. {@code 1.2.3.BUILD-SNAPSHOT}).
	 *
	 * @param version the major.minor.bugfix version, must not be {@code null}
	 * @param useModifierFormat whether to use hyphen between version and suffix
	 * @return an artifact version with the appropriate release suffix
	 */
	static ArtifactVersion of(NumericVersionComponents version, boolean useModifierFormat) {
		return new SemanticArtifactVersion(version, useModifierFormat);
	}

	/**
	 * Whether this version is strictly newer than the given version (e.g. higher version number, or same train with a
	 * later suffix).
	 *
	 * @param other the version to compare with, must not be {@code null}
	 * @return {@code true} if this version is newer than {@code other}
	 */
	boolean isNewer(ArtifactVersion other);

	/**
	 * Whether the given version is a newer minor (or equivalent) within the same major or release train. For semantic
	 * versions this means same major, higher minor. For release-train versions, same train name and this version is
	 * older.
	 *
	 * @param other the version to compare with, must not be {@code null}
	 * @return {@code true} if {@code other} is a newer minor in the same line
	 */
	boolean isNewerMinor(ArtifactVersion other);

	/**
	 * Whether this version is strictly older than the given version.
	 */
	boolean isOlder(ArtifactVersion other);

	/**
	 * Whether this version shares the same major.minor (semantic) or release train name (release-train) as the given
	 * version.
	 *
	 * @param other the version to compare with, must not be {@code null}
	 * @return {@code true} if both are in the same major.minor or train
	 */
	boolean hasSameMajorMinor(ArtifactVersion other);

	/**
	 * Whether this version shares the same major (semantic) or release train name (release-train) as the given version.
	 * For semantic versions this is the major number only; for release-train versions, the train name.
	 *
	 * @param other the version to compare with, must not be {@code null}
	 * @return {@code true} if both have the same major or train
	 */
	boolean hasSameMajor(ArtifactVersion other);

	/**
	 * Whether this version is a snapshot (e.g. {@code 1.2.3-SNAPSHOT}, {@code 1.2.3.BUILD-SNAPSHOT}).
	 *
	 * @return {@code true} if this version is a snapshot.
	 */
	boolean isSnapshotVersion();

	/**
	 * Whether this version is a milestone (e.g. {@code M1}, {@code M2}, or alpha/beta qualifiers).
	 *
	 * @return {@code true} if this version is a milestone.
	 */
	boolean isMilestoneVersion();

	/**
	 * Whether this version is a release candidate (e.g. {@code RC1}, {@code RC2}).
	 *
	 * @return {@code true} if this version is a release candidate.
	 */
	boolean isReleaseCandidateVersion();

	/**
	 * Whether this version is a preview release: a milestone or release candidate, but not yet GA.
	 *
	 * @return {@code true} if this version is a milestone or release candidate.
	 */
	boolean isPreview();

	/**
	 * Whether this version is a general-availability release (e.g. {@code RELEASE}, {@code 1.2.3} without milestone/RC
	 * suffix, or release-train {@code Train-RELEASE}).
	 *
	 * @return {@code true} if this version is a release (non-preview, non-snapshot)
	 */
	boolean isReleaseVersion();

	/**
	 * Whether this version is a service/bugfix release (e.g. {@code 1.2.1} or {@code Train-SR1}).
	 *
	 * @return {@code true} if this version is a bugfix or service release.
	 */
	boolean isBugFixVersion();

	/**
	 * Return whether the given version can be compared with this one (whether objects are semantically comparable).
	 */
	default boolean canCompare(ArtifactVersion version) {
		return getClass().equals(version.getClass());
	}

	@Override
	default ArtifactVersion getVersion() {
		return this;
	}

}
