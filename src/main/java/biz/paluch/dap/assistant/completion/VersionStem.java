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

package biz.paluch.dap.assistant.completion;

import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * A typed prefix that steers version completion.
 * <p>Numeric stems match component boundaries: 3 matches 3.5, but not 30.1.
 * Named stems match case-insensitive prefixes.
 *
 * @author Mark Paluch
 */
class VersionStem {

	private static final Pattern NUMERIC_STEM = Pattern.compile("v?\\d+(\\.\\d+)*");

	private final String stem;

	private final boolean suffixIntent;

	private VersionStem(String stem, boolean suffixIntent) {
		this.stem = stem;
		this.suffixIntent = suffixIntent;
	}

	/**
	 * Derive a stem, or return {@literal null} if the prefix has none.
	 */
	public static @Nullable VersionStem from(@Nullable String prefix) {

		if (StringUtils.isEmpty(prefix)) {
			return null;
		}

		String candidate = prefix.trim();
		boolean suffixIntent = candidate.endsWith("-");

		int end = candidate.length();
		while (end > 0 && isSeparator(candidate.charAt(end - 1))) {
			end--;
		}

		if (end == 0) {
			return null;
		}
		return new VersionStem(candidate.substring(0, end), suffixIntent);
	}

	private static boolean isSeparator(char c) {
		return c == '.' || c == '-';
	}

	/**
	 * Return whether a trailing hyphen requests previews, as in {@code 3.0-}.
	 */
	public boolean isSuffixIntent() {
		return suffixIntent;
	}

	/**
	 * Match the rendered or unwrapped version against this stem.
	 */
	public boolean matches(ArtifactVersion version) {

		if (matches(version.toString())) {
			return true;
		}

		ArtifactVersion unwrapped = version.unwrap();
		return unwrapped != version && matches(unwrapped.toString());
	}

	private boolean matches(String text) {

		if (text.equalsIgnoreCase(stem)) {
			return true;
		}
		if (!text.regionMatches(true, 0, stem, 0, stem.length())) {
			return false;
		}

		if (NUMERIC_STEM.matcher(stem).matches()) {
			return isSeparator(text.charAt(stem.length()));
		}
		return true;
	}

	@Override
	public String toString() {
		return stem;
	}

}
