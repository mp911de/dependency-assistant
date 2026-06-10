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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * A release that consists of a version and an optional release date.
 *
 * @author Mark Paluch
 */
public record Release(ArtifactVersion version,
		@Nullable LocalDateTime releaseDate) implements Comparable<Release>, VersionAware {

	/**
	 * Create a {@code Release} from a version string.
	 */
	public static Release of(String version) {
		return of(ArtifactVersion.of(version));
	}

	/**
	 * Create a {@code Release} without release date metadata.
	 */
	public static Release of(ArtifactVersion version) {
		return new Release(version, null);
	}

	/**
	 * Create a {@code Release} from a {@link ArtifactVersion} and release date.
	 */
	public static Release of(ArtifactVersion version, LocalDateTime date) {
		return new Release(version, date);
	}

	/**
	 * Create a {@code Release} from a version string and optional ISO date.
	 */
	public static Release from(String version, @Nullable String date) {
		return from(ArtifactVersion.of(version), date);
	}

	/**
	 * Create a {@code Release} from a {@link ArtifactVersion}.
	 */
	public static Release from(ArtifactVersion version) {
		return new Release(version, null);
	}

	/**
	 * Create a {@code Release} from a {@link ArtifactVersion} and optional ISO
	 * date.
	 */
	public static Release from(ArtifactVersion version, @Nullable String date) {
		return new Release(version, getReleaseDate(date));
	}

	/**
	 * Attempt to build a {@code Release} from a raw registry row.
	 *
	 * <p>Centralises the parse-or-skip path used by release-source adapters: a
	 * blank or unparseable {@code rawVersion} yields an empty result, a
	 * non-blank {@code sha} wraps the parsed version in a {@link GitVersion},
	 * and a non-null {@code date} is attached to the resulting release.
	 *
	 * @param rawVersion the raw version string as reported by the source; can
	 * be {@literal null} or blank.
	 * @param date the release date to attach; can be {@literal null}.
	 * @param sha the commit SHA backing the version; can be {@literal null} or
	 * blank, in which case the version is left unwrapped.
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
	 *
	 * @see LocalDateTime#parse
	 */
	public static @Nullable LocalDateTime getReleaseDate(@Nullable String date) {
		return StringUtils.hasText(date) ? LocalDateTime.of(LocalDate.parse(date), LocalTime.MIDNIGHT) : null;
	}

	/**
	 * Return whether this release is newer than the given release.
	 */
	public boolean isNewer(Release option) {
		return compareTo(option) > 0;
	}

	/**
	 * Return whether this release is newer than the given version.
	 */
	public boolean isNewer(ArtifactVersion version) {
		return this.version.isNewer(version);
	}

	/**
	 * Return whether this release is older than the given version.
	 */
	public boolean isOlder(ArtifactVersion version) {
		return this.version.isOlder(version);
	}

	/**
	 * Return whether this release belongs to the same major/minor line.
	 */
	public boolean hasSameMajorMinor(ArtifactVersion current) {
		return this.version.hasSameMajorMinor(current);
	}

	/**
	 * Return whether this release shares the same numeric version, ignoring any
	 * suffix or qualifier.
	 */
	public boolean hasSameBaseVersion(ArtifactVersion current) {
		return this.version.hasSameBaseVersion(current);
	}

	/**
	 * Return whether this release is a development (snapshot) version.
	 */
	public boolean isSnapshotVersion() {
		return this.version.isSnapshotVersion();
	}

	/**
	 * Return whether this release is a preview release.
	 */
	public boolean isPreview() {
		return this.version.isPreview();
	}

	/**
	 * Return whether this release is a general-availability release.
	 */
	public boolean isReleaseVersion() {
		return this.version.isReleaseVersion();
	}

	/**
	 * Return whether this release is a bugfix release.
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
	public String toString() {
		String string = version.toString();

		if (releaseDate != null) {
			string += " (" + releaseDate.toLocalDate() + ")";
		}
		return string;
	}

}
