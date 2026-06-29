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

package biz.paluch.dap.rule;

import java.util.function.Predicate;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Versioned;

import org.springframework.util.Assert;

/**
 * Value object representing a project development generation.
 *
 * <p>A generation is expressed as a literal version prefix. Supported inputs
 * are:
 * <ul>
 * <li>major lines such as {@code 6}</li>
 * <li>minor lines such as {@code 6.0} or {@code 6.0.x}</li>
 * <li>exact versions such as {@code 6.0.1}</li>
 * </ul>
 *
 * <p>The {@code *} wildcard is not a generation; it is handled by
 * {@link Generations#from(String...)} collapsing to the
 * {@linkplain Generations#unconstrained() unconstrained} instance.
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
	 * Create a generation from a project development line.
	 *
	 * <p>The supplied value is retained as a normalized prefix.
	 *
	 * @param generation the project development line.
	 * @return a generation for the project development line.
	 * @throws IllegalArgumentException if the value is empty or not a numeric
	 * project generation such as {@code 6}, {@code 6.0}, {@code 6.0.x}, or
	 * {@code 6.0.1}.
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
	 * Return this generation as an {@link ArtifactVersion} predicate.
	 *
	 * <p>The returned predicate unwraps prefixed versions before testing the
	 * innermost version string.
	 *
	 * @return an {@link ArtifactVersion} predicate backed by this generation.
	 */
	public Predicate<ArtifactVersion> asVersionPredicate() {
		return version -> test(Versioned.of(version).unwrap().toString());
	}

	/**
	 * Return the generation in its {@code .x} display form, such as {@code 6.0.x}.
	 *
	 * @return the generation prefix suffixed with {@code .x}.
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
