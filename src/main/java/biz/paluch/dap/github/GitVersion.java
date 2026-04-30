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

package biz.paluch.dap.github;

import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * {@link ArtifactVersion} implementation for GitHub Actions workflow refs.
 *
 * <p>A {@code GitVersion} pairs a resolved commit SHA with a delegate
 * {@link ArtifactVersion} used for ordering and display.
 *
 * @author Mark Paluch
 */
class GitVersion implements ArtifactVersion {

	private final @Nullable String sha;

	private final ArtifactVersion version;

	/**
	 * Create a new {@code GitVersion}.
	 * @param sha the full 40-character SHA-1, or {@code null} when unavailable.
	 * @param version the version used for comparison and display.
	 */
	private GitVersion(@Nullable String sha, ArtifactVersion version) {
		this.sha = sha;
		this.version = version;
	}

	/**
	 * Create a {@code GitVersion} with an SHA.
	 * @param sha the character SHA.
	 * @param version the normalized delegate version.
	 * @return the version.
	 */
	public static GitVersion of(@Nullable String sha, ArtifactVersion version) {
		return new GitVersion(sha, version);
	}

	/**
	 * Create a {@code GitVersion} without a SHA (tag-only resolution).
	 * @param version the normalized delegate version.
	 * @return the version.
	 */
	public static GitVersion of(ArtifactVersion version) {
		return new GitVersion(null, version);
	}

	/**
	 * Return the resolved SHA-1 commit hash, or {@code null} if unavailable.
	 */
	@Nullable
	public String getSha() {
		return sha;
	}

	/**
	 * Return the resolved SHA-1 commit hash, or {@code null} if unavailable.
	 */
	@Nullable
	public String getShortSha() {
		if (StringUtils.hasText(sha) && sha.length() > 7) {
			return sha.substring(0, 8);
		}
		return sha;
	}

	/**
	 * Return the normalized delegate version used for comparison and display.
	 */
	public ArtifactVersion getVersion() {
		return version;
	}

	@Override
	public boolean canCompare(ArtifactVersion version) {
		return version instanceof GitVersion;
	}

	@Override
	public boolean hasSameMajorMinor(ArtifactVersion other) {
		return version.hasSameMajorMinor(other.getVersion());
	}

	@Override
	public boolean hasSameMajor(ArtifactVersion other) {
		return version.hasSameMajor(other.getVersion());
	}

	@Override
	public boolean isNewer(ArtifactVersion other) {
		return version.isNewer(other.getVersion());
	}

	@Override
	public boolean isNewerMinor(ArtifactVersion other) {
		return version.isNewerMinor(other.getVersion());
	}

	@Override
	public boolean isOlder(ArtifactVersion other) {
		return version.isOlder(other.getVersion());
	}

	@Override
	public boolean isSnapshotVersion() {
		return version.isSnapshotVersion();
	}

	@Override
	public boolean isMilestoneVersion() {
		return version.isMilestoneVersion();
	}

	@Override
	public boolean isReleaseCandidateVersion() {
		return version.isReleaseCandidateVersion();
	}

	@Override
	public boolean isPreview() {
		return version.isPreview();
	}

	@Override
	public boolean isReleaseVersion() {
		return version.isReleaseVersion();
	}

	@Override
	public boolean isBugFixVersion() {
		return version.isBugFixVersion();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>For another {@code GitVersion}, the comparison delegates to the wrapped
	 * {@link ArtifactVersion} so that GitHub-only release lists sort consistently.
	 *
	 * <p>For non-{@code GitVersion} arguments this method throws
	 * {@link ClassCastException}. Returning {@code 0} for incomparable types would
	 * silently mis-merge sorted lists; throwing makes the incompatibility loud and
	 * matches the {@link Comparable} contract that orderings be total within a
	 * compared type.
	 */
	@Override
	public int compareTo(ArtifactVersion other) {
		return version.compareTo(other.getVersion());
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GitVersion that)) {
			return false;
		}
		return Objects.equals(sha, that.sha) && Objects.equals(version, that.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(sha, version);
	}

	/**
	 * Return a string suitable for documentation containing the version and
	 * {@link #getShortSha() short SHA} if present.
	 */
	public String toDocumentationString() {

		if (StringUtils.hasText(sha)) {
			return "%s (%s)".formatted(this, getShortSha());
		}

		return toString();
	}

	@Override
	public String toString() {
		return version.toString();
	}


}
