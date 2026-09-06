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

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import biz.paluch.dap.util.DateUtils;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * A version with an optional release date.
 * <p>The version may carry hash metadata through {@link GitVersion}. Equality
 * uses the version and date presence, ignoring the actual date value.
 *
 * @author Mark Paluch
 */
public record Release(ArtifactVersion version,
		@Nullable LocalDateTime releaseDate) implements Comparable<Release>, Versioned {

	/**
	 * Parse an undated release.
	 * @throws IllegalArgumentException if the version cannot be parsed.
	 */
	public static Release of(String version) {
		return of(ArtifactVersion.of(version));
	}

	/**
	 * Create an undated release.
	 */
	public static Release of(ArtifactVersion version) {
		return new Release(version, null);
	}

	public static Release of(ArtifactVersion version, LocalDateTime date) {
		return new Release(version, date);
	}

	/**
	 * Parse a release with an optional ISO date.
	 * @param date an ISO date or date-time, or {@literal null} if unknown.
	 * @throws IllegalArgumentException if the version cannot be parsed.
	 * @throws DateTimeParseException if the date cannot be parsed.
	 */
	public static Release from(String version, @Nullable String date) {
		return from(ArtifactVersion.of(version), date);
	}

	/**
	 * Create an undated release.
	 */
	public static Release from(ArtifactVersion version) {
		return new Release(version, null);
	}

	/**
	 * Create a release with an optional ISO date.
	 * @param date an ISO date or date-time, or {@literal null} if unknown.
	 * @throws DateTimeParseException if the date cannot be parsed.
	 */
	public static Release from(ArtifactVersion version, @Nullable String date) {
		return new Release(version, parseReleaseDate(date));
	}

	/**
	 * Parse a registry entry, skipping absent or unrecognized versions.
	 * @param date the release date, or {@literal null} if unknown.
	 * @param sha the source hash, or {@literal null} or blank if unavailable.
	 */
	public static Optional<Release> tryFrom(@Nullable String rawVersion, @Nullable LocalDateTime date,
			@Nullable String sha) {

		return ArtifactVersion.from(rawVersion).map(parsed -> {
			ArtifactVersion version = StringUtils.hasText(sha) ? GitVersion.of(sha, parsed) : parsed;
			return new Release(version, date);
		});
	}

	/**
	 * Parse an ISO date or date-time. Date-only values use midnight, and a supplied
	 * offset is discarded.
	 * @return {@literal null} if the value is absent or blank.
	 * @throws DateTimeParseException if the date cannot be parsed.
	 */
	public static @Nullable LocalDateTime parseReleaseDate(@Nullable String date) {
		if (StringUtils.isEmpty(date)) {
			return null;
		}
		return DateUtils.parse(date);
	}

	@Override
	public boolean isVersioned() {
		return true;
	}


	/**
	 * Determine whether this release is eligible under the preview policy.
	 * <p>A non-preview version cannot advance to a preview. This check alone does
	 * not establish that the release is newer.
	 */
	public boolean isUpgradeCandidate(ArtifactVersion artifactVersion) {

		if (artifactVersion.matches(version())) {
			return true;
		}

		if (!artifactVersion.isPreview() && version().isPreview()) {
			return false;
		}

		return getVersion().isNewer(artifactVersion) || getVersion().isBugFixVersion()
				|| getVersion().isReleaseVersion();
	}

	public boolean isNewer(Release option) {
		return compareTo(option) > 0;
	}

	public boolean isNewer(ArtifactVersion version) {
		return this.version.isNewer(version);
	}

	public boolean isOlder(ArtifactVersion version) {
		return this.version.isOlder(version);
	}

	public boolean hasSameMajorMinor(ArtifactVersion current) {
		return this.version.hasSameMajorMinor(current);
	}

	public boolean hasSameBaseVersion(ArtifactVersion current) {
		return this.version.hasSameBaseVersion(current);
	}

	public boolean isSnapshotVersion() {
		return this.version.isSnapshotVersion();
	}

	public boolean isPreview() {
		return this.version.isPreview();
	}

	public boolean isReleaseVersion() {
		return this.version.isReleaseVersion();
	}

	public boolean isBugFixVersion() {
		return this.version.isBugFixVersion();
	}

	@Override
	public ArtifactVersion getVersion() {
		return version;
	}

	/**
	 * Compare versions within a shared scheme.
	 * <p>Cross-scheme comparisons use dates and version text as a fallback. Use
	 * {@link Releases} for artifact-level scheme precedence.
	 */
	@Override
	public int compareTo(Release o) {

		if (version.canCompare(o.version)) {
			return version.compareTo(o.version);
		}

		if (releaseDate != null && o.releaseDate != null) {
			int byDate = releaseDate.compareTo(o.releaseDate);
			if (byDate != 0) {
				return byDate;
			}
		}

		return version.toString().compareToIgnoreCase(o.version.toString());
	}


	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Release that)) {
			return false;
		}

		if (that.releaseDate == null && releaseDate != null || that.releaseDate != null && releaseDate == null) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(version, that.version);
	}

	@Override
	public int hashCode() {
		return version.hashCode();
	}

	@Override
	public String toString() {
		String string = version.toString();

		if (releaseDate != null) {
			string += " (" + releaseDate.toLocalDate() + ")";
		}
		return string;
	}

}
