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
import org.jspecify.annotations.Nullable;

/**
 * Parses a Gradle rich-version string into a concrete version anchor suitable
 * for dependency collection and upgrade suggestions.
 * <p>Supports {@code !!} strict shorthand, Maven/Gradle range syntax with
 * inclusive/exclusive bounds using {@code [}, {@code ]}, {@code (}, and
 * {@code )} delimiters, and dynamic version patterns that are left unresolved.
 *
 * @author Mark Paluch
 */
class GradleRichVersion {

	private static final String BANG_BANG = "!!";

	@Nullable
	private final ArtifactVersion concreteVersion;

	private GradleRichVersion(@Nullable ArtifactVersion concreteVersion) {
		this.concreteVersion = concreteVersion;
	}

	/**
	 * Parse a raw Gradle version string into a {@code GradleRichVersion}.
	 *
	 * @param raw the raw version string, must not be {@literal null}.
	 * @return the parsed rich version.
	 */
	static Optional<ArtifactVersion> parse(String raw) {
		if (isDynamic(raw)) {
			return Optional.empty();
		}

		String[] bangParts = raw.split(BANG_BANG, 2);
		String strictlyPart = bangParts[0];
		String preferPart = bangParts.length > 1 ? bangParts[1].trim() : "";

		if (!preferPart.isEmpty()) {
			ArtifactVersion prefer = ArtifactVersion.from(preferPart).orElse(null);
			if (prefer != null) {
				return Optional.of(prefer);
			}
		}

		if (!isRange(strictlyPart)) {
			return ArtifactVersion.from(strictlyPart);
		}

		return anchorFromRange(strictlyPart);
	}

	/**
	 * Return the concrete version anchor derived from the raw version string, if
	 * one can be determined.
	 *
	 * @return the concrete version, or empty if the version is dynamic or no anchor
	 * can be derived.
	 */
	Optional<ArtifactVersion> getConcreteVersion() {
		return Optional.ofNullable(concreteVersion);
	}

	private static boolean isDynamic(String raw) {
		return raw.endsWith(".+") || raw.equals("+") || raw.equals("latest.release")
				|| raw.equals("latest.integration");
	}

	private static boolean isRange(String s) {
		if (s.isEmpty()) {
			return false;
		}
		char first = s.charAt(0);
		char last = s.charAt(s.length() - 1);
		boolean lowerDelimiter = first == '[' || first == '(' || first == ']';
		boolean upperDelimiter = last == ']' || last == ')' || last == '[';
		return lowerDelimiter && upperDelimiter;
	}

	private static Optional<ArtifactVersion> anchorFromRange(String range) {

		String content = range.substring(1, range.length() - 1);
		int commaIndex = content.indexOf(',');
		if (commaIndex < 0) {
			return Optional.empty();
		}

		String lowerToken = content.substring(0, commaIndex).trim();
		String upperToken = content.substring(commaIndex + 1).trim();
		char lastChar = range.charAt(range.length() - 1);
		char firstChar = range.charAt(0);

		if (lastChar == ']') {
			Optional<ArtifactVersion> upper = ArtifactVersion.from(upperToken);
			if (upper.isPresent()) {
				return upper;
			}
		}

		if (firstChar == '[') {
			Optional<ArtifactVersion> lower = ArtifactVersion.from(lowerToken);
			if (lower.isPresent()) {
				return lower;
			}
		}

		return Optional.empty();
	}

}
