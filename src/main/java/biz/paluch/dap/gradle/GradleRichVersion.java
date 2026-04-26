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

package biz.paluch.dap.gradle;

import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.util.StringUtils;

/**
 * Utilities for Gradle rich-version declarations.
 *
 * @author Mark Paluch
 */
class GradleRichVersion {

	private static final String BANG_BANG = "!!";

	private GradleRichVersion() {
	}

	/**
	 * Derive a concrete version anchor from a raw Gradle rich-version declaration.
	 */
	static Optional<ArtifactVersion> parse(String raw) {
		if (isDynamic(raw)) {
			return Optional.empty();
		}

		BangBangVersion bangBang = BangBangVersion.parse(raw);
		if (bangBang != null && StringUtils.hasText(bangBang.prefer())) {
			Optional<ArtifactVersion> preferred = ArtifactVersion.from(bangBang.prefer().trim());
			if (preferred.isPresent()) {
				return preferred;
			}
		}

		String candidate = bangBang != null ? bangBang.strictly() : raw;
		VersionRange range = VersionRange.parse(candidate);
		return (range != null ? range.anchor() : ArtifactVersion.from(candidate.trim()));
	}

	/**
	 * Rewrite a raw Gradle rich-version declaration to use {@code newVersion} while
	 * preserving supported rich-version syntax.
	 */
	static String update(String raw, String newVersion) {
		if (StringUtils.isEmpty(raw) || isDynamic(raw)) {
			return raw;
		}

		BangBangVersion bangBang = BangBangVersion.parse(raw);
		if (bangBang != null) {
			return bangBang.update(newVersion);
		}

		VersionRange range = VersionRange.parse(raw);
		return (range != null ? range.update(newVersion) : newVersion);
	}

	private static boolean isDynamic(String raw) {
		String trimmed = raw.trim();
		return trimmed.endsWith(".+") || trimmed.equals("+") || trimmed.equals("latest.release")
				|| trimmed.equals("latest.integration");
	}

	private record BangBangVersion(String strictly, String prefer) {

		static BangBangVersion parse(String raw) {
			int bangBang = raw.indexOf(BANG_BANG);
			if (bangBang == -1) {
				return null;
			}
			return new BangBangVersion(raw.substring(0, bangBang), raw.substring(bangBang + BANG_BANG.length()));
		}

		String update(String newVersion) {
			if (StringUtils.hasText(this.prefer)) {
				return this.strictly + BANG_BANG + replaceTrimmed(this.prefer, newVersion);
			}
			return newVersion + BANG_BANG;
		}

	}

	private record VersionRange(String prefix, char start, String lowerBound, String upperBound, char end,
			String suffix) {

		static VersionRange parse(String raw) {
			int leadingWhitespace = leadingWhitespace(raw);
			int trailingWhitespace = trailingWhitespace(raw);
			String trimmed = raw.substring(leadingWhitespace, raw.length() - trailingWhitespace);

			if (!hasRangeDelimiters(trimmed)) {
				return null;
			}

			String content = trimmed.substring(1, trimmed.length() - 1);
			int comma = content.indexOf(',');
			if (comma == -1) {
				return null;
			}

			return new VersionRange(raw.substring(0, leadingWhitespace), trimmed.charAt(0),
					content.substring(0, comma), content.substring(comma + 1),
					trimmed.charAt(trimmed.length() - 1), raw.substring(raw.length() - trailingWhitespace));
		}

		Optional<ArtifactVersion> anchor() {
			if (this.end == ']') {
				Optional<ArtifactVersion> upper = ArtifactVersion.from(this.upperBound.trim());
				if (upper.isPresent()) {
					return upper;
				}
			}

			if (this.start == '[') {
				Optional<ArtifactVersion> lower = ArtifactVersion.from(this.lowerBound.trim());
				if (lower.isPresent()) {
					return lower;
				}
			}

			return Optional.empty();
		}

		String update(String newVersion) {
			if (this.end == ']' && StringUtils.hasText(this.upperBound)) {
				return this.prefix + this.start + this.lowerBound + "," + replaceTrimmed(this.upperBound, newVersion)
						+ this.end + this.suffix;
			}

			if (this.start == '[' && StringUtils.hasText(this.lowerBound)) {
				return this.prefix + this.start + replaceTrimmed(this.lowerBound, newVersion) + "," + this.upperBound
						+ this.end + this.suffix;
			}

			return toString();
		}

		@Override
		public String toString() {
			return this.prefix + this.start + this.lowerBound + "," + this.upperBound + this.end + this.suffix;
		}

		private static boolean hasRangeDelimiters(String value) {
			if (!StringUtils.hasText(value)) {
				return false;
			}
			char first = value.charAt(0);
			char last = value.charAt(value.length() - 1);
			boolean lowerDelimiter = first == '[' || first == '(' || first == ']';
			boolean upperDelimiter = last == ']' || last == ')' || last == '[';
			return lowerDelimiter && upperDelimiter;
		}

	}

	private static String replaceTrimmed(String value, String replacement) {
		int leadingWhitespace = leadingWhitespace(value);
		int trailingWhitespace = trailingWhitespace(value);
		return value.substring(0, leadingWhitespace) + replacement
				+ value.substring(value.length() - trailingWhitespace);
	}

	private static int leadingWhitespace(String value) {
		int index = 0;
		while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
			index++;
		}
		return index;
	}

	private static int trailingWhitespace(String value) {
		int index = value.length() - 1;
		while (index >= 0 && Character.isWhitespace(value.charAt(index))) {
			index--;
		}
		return value.length() - index - 1;
	}

}
