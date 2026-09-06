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

package biz.paluch.dap.rule;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactVersion;

import org.springframework.util.Assert;

/**
 * A numeric version prefix identifying a development line. For example,
 * {@code 6.0} and {@code 6.0.x} identify the same line. Matching requires a
 * complete dot-separated prefix, so {@code 6} does not match {@code 60}.
 *
 * <p>Use {@link Generations} for the unconstrained {@code *} wildcard.
 *
 * @author Mark Paluch
 */
public class Generation implements Predicate<String> {

	private static final Pattern GENERATION_PATTERN = Pattern.compile("\\d+(\\.\\d+)*(\\.x)?");

	private final String generation;

	private Generation(String generation) {
		this.generation = normalize(generation);
	}

	/**
	 * Parse a numeric development line with an optional trailing {@code .x}.
	 *
	 * @throws IllegalArgumentException if the value is empty or not a numeric
	 * generation.
	 */
	public static Generation of(String generation) {
		Assert.hasText(generation, "Generation must not be empty");
		if (!GENERATION_PATTERN.matcher(generation).matches()) {
			throw new IllegalArgumentException(
					"Generation '%s' must be a numeric project generation such as '6', '6.0', '6.0.x', or '6.0.1'"
							.formatted(generation));
		}
		return new Generation(generation);
	}

	@Override
	public boolean test(String version) {
		return version.equals(this.generation) || version.startsWith(this.generation + ".");
	}

	/**
	 * Return a predicate that tests the innermost version, ignoring wrappers.
	 */
	public Predicate<ArtifactVersion> asVersionPredicate() {
		return version -> test(version.unwrap().toString());
	}

	/**
	 * Return the {@code .x} display form, such as {@code 6.0.x}.
	 */
	public String value() {
		return this.generation + ".x";
	}

	@Override
	public boolean equals(Object o) {

		if (!(o instanceof Generation that)) {
			return false;
		}
		return this.generation.equals(that.generation);
	}

	@Override
	public int hashCode() {
		return this.generation.hashCode();
	}

	@Override
	public String toString() {
		return this.generation;
	}

	private static String normalize(String value) {
		return value.endsWith(".x") ? value.substring(0, value.length() - 2) : value;
	}

}
