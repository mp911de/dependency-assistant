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
 * A version with a prefix preserved for rendering, such as {@code v1.2.3}.
 * <p>The prefix does not affect comparison or equality.
 *
 * @author Mark Paluch
 */
class PrefixedArtifactVersion extends ArtifactVersionWrapper implements ArtifactVersion {

	/**
	 * Keep the longest version tail so qualifiers remain part of the version.
	 */
	private static final Pattern TAG = Pattern.compile("^(?<prefix>(?:.*?[-/])?v?)(?<version>\\d.*)$");

	private final String prefix;

	PrefixedArtifactVersion(String prefix, ArtifactVersion delegate) {
		super(delegate);
		this.prefix = prefix;
	}

	/**
	 * Parse a numeric tag while preserving its prefix.
	 * @return {@literal null} if no numeric version is recognized.
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
