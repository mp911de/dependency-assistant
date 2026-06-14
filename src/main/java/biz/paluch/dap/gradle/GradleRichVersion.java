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
import org.jspecify.annotations.Nullable;

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
	static Optional<ArtifactVersion> parse(@Nullable String raw) {
		if (!StringUtils.hasText(raw) || isDynamic(raw)) {
			return Optional.empty();
		}

		BangBangVersion bangBang = BangBangVersion.parse(raw);
		if (bangBang != null) {
			return bangBang.anchor();
		}

		VersionRange range = VersionRange.parse(raw);
		if (range != null) {
			return range.anchor();
		}
		return hasRangeSyntax(raw) ? Optional.empty() : ArtifactVersion.from(raw.trim());
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
		if (range != null) {
			return range.update(newVersion);
		}
		return hasRangeSyntax(raw) ? raw : newVersion;
	}

	private static boolean isDynamic(String raw) {
		String trimmed = raw.trim();
		return trimmed.endsWith(".+") || trimmed.equals("+") || trimmed.equals("latest.release")
				|| trimmed.equals("latest.integration");
	}

	private static boolean hasRangeSyntax(String raw) {
		return raw.contains("[") || raw.contains("]") || raw.contains("(") || raw.contains(")") || raw.contains(",");
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

		Optional<ArtifactVersion> anchor() {
			VersionRange range = VersionRange.parse(this.strictly);
			Optional<ArtifactVersion> strict = Optional.empty();
			if (range != null) {
				strict = range.anchor();
			} else {
				if (hasRangeSyntax(this.strictly)) {
					return Optional.empty();
				}
				strict = ArtifactVersion.from(this.strictly.trim());
				if (strict.isEmpty()) {
					return Optional.empty();
				}
			}

			return StringUtils.hasText(this.prefer) ? ArtifactVersion.from(this.prefer.trim()) : strict;
		}

	}

	private record VersionRange(String prefix, char start, String lowerBound, String upperBound, char end,
			String suffix) {

		static @Nullable VersionRange parse(String raw) {
			int leadingWhitespace = leadingWhitespace(raw);
			int trailingWhitespace = trailingWhitespace(raw);
			String trimmed = raw.substring(leadingWhitespace, raw.length() - trailingWhitespace);

			if (!hasRangeDelimiters(trimmed) || trimmed.length() < 3) {
				return null;
			}

			String content = trimmed.substring(1, trimmed.length() - 1);
			int comma = content.indexOf(',');
			if (comma == -1 || comma != content.lastIndexOf(',')) {
				return null;
			}

			String lowerBound = content.substring(0, comma);
			String upperBound = content.substring(comma + 1);
			if (!StringUtils.hasText(lowerBound) && !StringUtils.hasText(upperBound)) {
				return null;
			}
			if (!isVersionBound(lowerBound) || !isVersionBound(upperBound)) {
				return null;
			}

			return new VersionRange(raw.substring(0, leadingWhitespace), trimmed.charAt(0),
					lowerBound, upperBound, trimmed.charAt(trimmed.length() - 1),
					raw.substring(raw.length() - trailingWhitespace));
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
				String updatedLower = replaceTrimmed(this.lowerBound, newVersion);
				if (crossesUpperBound(newVersion)) {
					return this.prefix + this.start + updatedLower + ","
							+ replaceTrimmed(this.upperBound, newVersion) + ']' + this.suffix;
				}
				return this.prefix + this.start + updatedLower + "," + this.upperBound + this.end + this.suffix;
			}

			return toString();
		}

		/**
		 * Whether {@code newVersion} reaches or exceeds the exclusive upper bound,
		 * which would invert the range if only the lower bound were replaced.
		 */
		private boolean crossesUpperBound(String newVersion) {
			if (this.end == ']' || !StringUtils.hasText(this.upperBound)) {
				return false;
			}

			Optional<ArtifactVersion> upper = ArtifactVersion.from(this.upperBound.trim());
			Optional<ArtifactVersion> target = ArtifactVersion.from(newVersion.trim());
			if (upper.isEmpty() || target.isEmpty() || !target.get().canCompare(upper.get())) {
				return false;
			}

			return !target.get().isOlder(upper.get());
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

		private static boolean isVersionBound(String value) {
			String trimmed = value.trim();
			return trimmed.isEmpty() || ArtifactVersion.from(trimmed).isPresent();
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
