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
 * A release consisting of a version and an optional release date.
 *
 * <p>The version may carry source-provided hash metadata through
 * {@link GitVersion}. Equality uses the version and whether a date is present;
 * the concrete date value does not participate.
 *
 * @author Mark Paluch
 * @param version the released version.
 * @param releaseDate the release date, may be {@code null}.
 */
public record Release(ArtifactVersion version,
		@Nullable LocalDateTime releaseDate) implements Comparable<Release>, Versioned {

	/**
	 * Create a {@code Release} from a version string.
	 * @param version the version string.
	 * @return an undated release.
	 * @throws IllegalArgumentException if the version cannot be parsed.
	 */
	public static Release of(String version) {
		return of(ArtifactVersion.of(version));
	}

	/**
	 * Create a {@code Release} without release date metadata.
	 * @param version the artifact version.
	 * @return an undated release.
	 */
	public static Release of(ArtifactVersion version) {
		return new Release(version, null);
	}

	/**
	 * Create a {@code Release} from a {@link ArtifactVersion} and release date.
	 * @param version the artifact version.
	 * @param date the release date.
	 * @return the dated release.
	 */
	public static Release of(ArtifactVersion version, LocalDateTime date) {
		return new Release(version, date);
	}

	/**
	 * Create a {@code Release} from a version string and optional ISO date.
	 * @param version the version string.
	 * @param date the ISO date or date-time, or {@literal null}.
	 * @return the release.
	 * @throws IllegalArgumentException if the version cannot be parsed.
	 * @throws DateTimeParseException if the date cannot be parsed.
	 */
	public static Release from(String version, @Nullable String date) {
		return from(ArtifactVersion.of(version), date);
	}

	/**
	 * Create a {@code Release} from a {@link ArtifactVersion}.
	 * @param version the artifact version.
	 * @return an undated release.
	 */
	public static Release from(ArtifactVersion version) {
		return new Release(version, null);
	}

	/**
	 * Create a {@code Release} from a {@link ArtifactVersion} and optional ISO
	 * date.
	 * @param version the artifact version.
	 * @param date the ISO date or date-time, or {@literal null}.
	 * @return the release.
	 * @throws DateTimeParseException if the date cannot be parsed.
	 */
	public static Release from(ArtifactVersion version, @Nullable String date) {
		return new Release(version, parseReleaseDate(date));
	}

	/**
	 * Attempt to build a {@code Release} from a raw registry row.
	 *
	 * <p>Centralises the parse-or-skip path used by release-source adapters: a
	 * blank or unparseable {@code rawVersion} yields an empty result, a non-blank
	 * {@code sha} wraps the parsed version in a {@link GitVersion}, and a non-null
	 * {@code date} is attached to the resulting release.
	 *
	 * @param rawVersion the raw version string as reported by the source; can be
	 * {@literal null} or blank.
	 * @param date the release date to attach; can be {@literal null}.
	 * @param sha the source-provided hash backing the version; can be
	 * {@literal null} or blank, in which case the version is left unwrapped.
	 * @return the parsed release, or {@link Optional#empty()} when
	 * {@code rawVersion} is blank or cannot be parsed.
	 */
	public static Optional<Release> tryFrom(@Nullable String rawVersion, @Nullable LocalDateTime date,
			@Nullable String sha) {

		return ArtifactVersion.from(rawVersion).map(parsed -> {
			ArtifactVersion version = StringUtils.hasText(sha) ? GitVersion.of(sha, parsed) : parsed;
			return new Release(version, date);
		});
	}

	/**
	 * Parse the release date from an ISO string.
	 * <p>Accepts both ISO-8601 local date-time strings (e.g.
	 * {@code "2024-01-15T10:30"}) and legacy ISO-8601 local date strings (e.g.
	 * {@code "2024-01-15"}). Legacy date-only strings are interpreted as midnight.
	 *
	 * @param date the ISO-8601 date or date-time string, or {@literal null}.
	 * @return the parsed date-time, or {@literal null} if {@code date} is blank.
	 * @throws DateTimeParseException if a non-blank value is neither an ISO local
	 * date nor an ISO local date-time.
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
	 * Return whether this release is newer than the given release.
	 * @param option the release to compare with.
	 * @return {@code true} if this release is newer.
	 */
	public boolean isNewer(Release option) {
		return compareTo(option) > 0;
	}

	/**
	 * Return whether this release is newer than the given version.
	 * @param version the version to compare with.
	 * @return {@code true} if this release is newer.
	 */
	public boolean isNewer(ArtifactVersion version) {
		return this.version.isNewer(version);
	}

	/**
	 * Return whether this release is older than the given version.
	 * @param version the version to compare with.
	 * @return {@code true} if this release is older.
	 */
	public boolean isOlder(ArtifactVersion version) {
		return this.version.isOlder(version);
	}

	/**
	 * Return whether this release belongs to the same major/minor line.
	 * @param current the version to compare with.
	 * @return {@code true} if both versions share a major/minor line.
	 */
	public boolean hasSameMajorMinor(ArtifactVersion current) {
		return this.version.hasSameMajorMinor(current);
	}

	/**
	 * Return whether this release shares the same numeric version, ignoring any
	 * suffix or qualifier.
	 * @param current the version to compare with.
	 * @return {@code true} if both versions share a base version.
	 */
	public boolean hasSameBaseVersion(ArtifactVersion current) {
		return this.version.hasSameBaseVersion(current);
	}

	/**
	 * Return whether this release is a development (snapshot) version.
	 * @return {@code true} if this is a snapshot release.
	 */
	public boolean isSnapshotVersion() {
		return this.version.isSnapshotVersion();
	}

	/**
	 * Return whether this release is a preview release.
	 * @return {@code true} if this is a preview release.
	 */
	public boolean isPreview() {
		return this.version.isPreview();
	}

	/**
	 * Return whether this release is a general-availability release.
	 * @return {@code true} if this is a general-availability release.
	 */
	public boolean isReleaseVersion() {
		return this.version.isReleaseVersion();
	}

	/**
	 * Return whether this release is a bugfix release.
	 * @return {@code true} if this is a bugfix release.
	 */
	public boolean isBugFixVersion() {
		return this.version.isBugFixVersion();
	}

	@Override
	public ArtifactVersion getVersion() {
		return version;
	}

	/**
	 * Compare this release with the given release.
	 *
	 * <p>Releases sharing a {@link VersioningScheme} compare by version. Across
	 * schemes the result is a deterministic, non-authoritative tiebreak (release
	 * date, then version text) that keeps distinct releases distinct in sorted
	 * collections; the authoritative cross-scheme order is owned by
	 * {@link Releases}.
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
