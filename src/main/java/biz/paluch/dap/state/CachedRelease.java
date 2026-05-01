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

package biz.paluch.dap.state;

import java.time.LocalDateTime;
import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.util.StringUtils;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jspecify.annotations.Nullable;

/**
 * Persistent representation of a cached artifact release.
 * <p>The serialized form stores only the release version and an optional
 * ISO-8601 date string. Conversion back to the domain {@link Release} type is
 * performed lazily and memoized for repeated access within the same JVM
 * instance.
 *
 * @author Mark Paluch
 */
@Tag("release")
public class CachedRelease {

	@Attribute
	private String version;

	@Attribute
	private @Nullable String date;

	@Attribute
	private @Nullable String sha;

	private volatile @Nullable Release release;

	/**
	 * Create an empty release entry for XML deserialization.
	 */
	public CachedRelease() {
	}

	/**
	 * Create a release entry with the given serialized values.
	 *
	 * @param version the release version.
	 * @param date the optional release date in ISO-8601 local-date form.
	 */
	public CachedRelease(String version, @Nullable String date) {
		this.version = version;
		this.date = date;
	}

	/**
	 * Create a release entry with the given serialized values including a commit
	 * SHA.
	 *
	 * @param version the release version.
	 * @param date the optional release date in ISO-8601 local-date form.
	 * @param sha the full 40-character SHA-1 commit hash, or {@code null}.
	 */
	public CachedRelease(String version, @Nullable String date, @Nullable String sha) {
		this.version = version;
		this.date = date;
		this.sha = sha;
	}

	/**
	 * Create a cached representation of the given release.
	 *
	 * @param release the domain release to convert.
	 * @return the corresponding cached release representation.
	 */
	public static CachedRelease from(Release release) {
		if (release.version() instanceof GitVersion gitVersion) {
			return CachedRelease.from(gitVersion.getVersion(), release.releaseDate(), gitVersion.getSha());
		}
		return from(release.version(), release.releaseDate());
	}

	/**
	 * Create a cached representation of the given release.
	 *
	 * @param version
	 * @param releaseDate
	 * @return the corresponding cached release representation.
	 */
	public static CachedRelease from(ArtifactVersion version, @Nullable LocalDateTime releaseDate) {
		if (releaseDate != null) {
			return new CachedRelease(version.toString(), releaseDate.toLocalDate().toString());
		}
		return new CachedRelease(version.toString(), null);
	}

	/**
	 * Create a cached release representation.
	 *
	 * @param version
	 * @param releaseDate
	 * @param sha
	 * @return the corresponding cached release representation.
	 */
	public static CachedRelease from(ArtifactVersion version, @Nullable LocalDateTime releaseDate,
			@Nullable String sha) {
		if (releaseDate != null) {
			return new CachedRelease(version.toString(), releaseDate.toLocalDate().toString(), sha);
		}
		return new CachedRelease(version.toString(), null, sha);
	}

	/**
	 * Return this entry as a domain {@link Release}.
	 * <p>The returned instance is memoized after the first conversion.
	 *
	 * @return the corresponding release.
	 */
	@Transient
	public Release toRelease() {

		Release cachedRelease = this.release;
		if (cachedRelease == null) {

			ArtifactVersion version = ArtifactVersion.of(version());
			if (StringUtils.hasText(sha())) {
				version = GitVersion.of(sha(), version);
			}

			cachedRelease = Release.from(version, date());
			this.release = cachedRelease;
		}
		return cachedRelease;
	}

	/**
	 * Return the serialized release version.
	 *
	 * @return the release version.
	 */
	@Attribute
	public String version() {
		return version;
	}

	/**
	 * Return the serialized release date.
	 *
	 * @return the optional ISO-8601 local-date string.
	 */
	@Attribute
	public @Nullable String date() {
		return date;
	}

	/**
	 * Return the full SHA-1 commit hash, or {@code null} if not stored.
	 *
	 * @return the commit SHA-1, or {@code null}.
	 */
	@Attribute
	public @Nullable String sha() {
		return sha;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		CachedRelease that = (CachedRelease) obj;
		return Objects.equals(this.version, that.version) && Objects.equals(this.date, that.date)
				&& Objects.equals(this.sha, that.sha);
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, date, sha);
	}

	@Override
	public String toString() {
		return "Release[" + "version=" + version + ", " + "date=" + date + ", sha=" + sha + ']';
	}

}
