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

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * A release that consists of a version and an optional release date.
 */
public record Release(ArtifactVersion version,
		@Nullable LocalDateTime releaseDate) implements Comparable<Release>, HasVersion {

	public static Release of(String version) {
		return of(ArtifactVersion.of(version));
	}

	public static Release of(ArtifactVersion version) {
		return new Release(version, null);
	}

	public static Release from(String version, @Nullable String date) {
		return new Release(ArtifactVersion.of(version),
				StringUtils.hasText(date) ? LocalDateTime.of(LocalDate.parse(date), LocalTime.MIDNIGHT) : null);
	}

	@Override
	public ArtifactVersion getVersion() {
		return version;
	}

	@Override
	public int compareTo(Release o) {

		if (version.canCompare(o.version)) {
			return version.compareTo(o.version);
		}

		if (releaseDate != null && o.releaseDate != null) {
			return releaseDate.compareTo(o.releaseDate);
		}

		return 0;
	}

	@Override
	public String toString() {
		String string = version.toString();

		if (releaseDate != null) {
			string += " (" + releaseDate.toLocalDate() + ")";
		}
		return string;
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

	public boolean isBugFixVersion() {
		return this.version.isBugFixVersion();
	}

	public boolean isReleaseVersion() {
		return this.version.isReleaseVersion();
	}

	public boolean isPreview() {
		return this.version.isPreview();
	}

}
