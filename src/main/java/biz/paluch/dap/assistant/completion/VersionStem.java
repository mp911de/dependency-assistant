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

package biz.paluch.dap.assistant.completion;

import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * A partially typed version steering release proposals: the sanitized stem of a
 * completion prefix such as {@code 3.}, {@code 3.0-}, or {@code Hox}.
 *
 * <p>Trailing separators are stripped ({@code 3.0-} yields the stem
 * {@code 3.0}); a stripped trailing hyphen additionally signals
 * {@linkplain #isSuffixIntent() suffix intent}, asking for the pre-releases of
 * the stem's base version. Numeric stems bind at component boundaries
 * ({@code 3} matches {@code 3.5.12}, never {@code 30.1}); name stems such as
 * train-name fragments match by plain case-insensitive prefix.
 *
 * @author Mark Paluch
 * @see ReleaseProposals
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
	 * Derive a stem from the given completion prefix on a best-effort basis.
	 *
	 * @param prefix the raw completion prefix, may be {@literal null} or empty.
	 * @return the stem, or {@literal null} when the prefix carries no usable stem.
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
	 * Return whether the prefix asked for pre-releases of the stem's base version
	 * by ending with a hyphen (e.g. {@code 3.0-}).
	 *
	 * @return {@literal true} if the prefix signals suffix intent; {@literal false}
	 * otherwise.
	 */
	public boolean isSuffixIntent() {
		return suffixIntent;
	}

	/**
	 * Return whether the given version matches this stem, testing the rendered form
	 * and the unwrapped form so a numeric stem also steers prefixed or Git-wrapped
	 * versions.
	 *
	 * @param version the version to test.
	 * @return {@literal true} if the version starts with the stem, binding at a
	 * component boundary for numeric stems; {@literal false} otherwise.
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
