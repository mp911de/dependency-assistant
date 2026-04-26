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

import java.util.Objects;

import biz.paluch.dap.artifact.Release;
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
	 * Create a cached representation of the given release.
	 *
	 * @param release the domain release to convert.
	 * @return the corresponding cached release representation.
	 */
	public static CachedRelease from(Release release) {
		if (release.releaseDate() != null) {
			return new CachedRelease(release.version().toString(), release.releaseDate().toLocalDate().toString());
		}
		return new CachedRelease(release.version().toString(), null);
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
			cachedRelease = Release.from(version(), date());
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

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		var that = (CachedRelease) obj;
		return Objects.equals(this.version, that.version) && Objects.equals(this.date, that.date);
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, date);
	}

	@Override
	public String toString() {
		return "Release[" + "version=" + version + ", " + "date=" + date + ']';
	}

}
