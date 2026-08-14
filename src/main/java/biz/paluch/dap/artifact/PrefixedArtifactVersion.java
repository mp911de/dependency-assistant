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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * {@link ArtifactVersion} that wraps an inner version with a string prefix such
 * as {@code v} or a tag prefix such as {@code assertj-build-}.
 *
 * <p>The prefix is preserved in {@link #toString()} so that the original
 * version string round-trips correctly (e.g. {@code v1.2.3}).
 *
 * @author Mark Paluch
 * @see ArtifactVersion#isWrapped()
 */
class PrefixedArtifactVersion extends ArtifactVersionWrapper implements ArtifactVersion {

	/**
	 * Splits a tag into a prefix and a version literal: a lazy prefix ending at a
	 * hyphen or slash (so the leftmost digit-led boundary wins and the version tail
	 * stays longest), an optional {@code v} marker, and the digit-led version.
	 */
	private static final Pattern TAG = Pattern.compile("^(?<prefix>(?:.*?[-/])?v?)(?<version>\\d.*)$");

	private final String prefix;

	PrefixedArtifactVersion(String prefix, ArtifactVersion delegate) {
		super(delegate);
		this.prefix = prefix;
	}

	/**
	 * Parse a tag name carrying an optional prefix before its version literal (e.g.
	 * {@code assertj-build-3.27.7}, {@code release-v2.0.0}, {@code release/2.0.0},
	 * {@code v1.2.3}, or a plain version). Qualifier suffixes stay part of the
	 * version because the tag is cut where the version starts; the prefix keeps
	 * everything before the cut, including path-style segments, so
	 * {@link #toString()} round-trips the original tag.
	 * @param tag the tag name.
	 * @return the parsed version, or {@literal null} if the tag carries no semantic
	 * version literal.
	 */
	static @Nullable ArtifactVersion parseTag(String tag) {

		Matcher matcher = TAG.matcher(tag);
		if (!matcher.matches() || !SemanticArtifactVersion.isVersion(matcher.group("version"))) {
			return null;
		}

		ArtifactVersion version = ArtifactVersion.of(matcher.group("version"));
		String prefix = matcher.group("prefix");
		return prefix.isEmpty() ? version : new PrefixedArtifactVersion(prefix, version);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ArtifactVersion other)) {
			return false;
		}
		return getVersion().equals(other.getVersion());
	}

	@Override
	public int hashCode() {
		return getVersion().hashCode();
	}

	@Override
	public String toString() {
		return prefix + getVersion();
	}

}
