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
import biz.paluch.dap.util.StringUtils;

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
 * <li>{@code *} to accept every version</li>
 * </ul>
 *
 * @author Mark Paluch
 */
public class Generation implements Predicate<String> {

	private static final Pattern GENERATION_PATTERN = Pattern.compile("\\d+(\\.\\d+)*(\\.x)?");

	private final String generation;

	private final Predicate<ArtifactVersion> versionPredicate;

	private Generation(String generation) {
		this.generation = normalize(generation);
		this.versionPredicate = version -> test(innermost(version).toString());
	}

	/**
	 * Create a generation from a project development line.
	 *
	 * <p>The supplied value is retained as a normalized prefix.
	 *
	 * @param generation the project development line; must not be {@literal null}.
	 * @return a generation for the project development line.
	 * @throws IllegalArgumentException if the value is empty or not a numeric
	 * project generation such as {@code 6}, {@code 6.0}, {@code 6.0.x}, or
	 * {@code 6.0.1}.
	 */
	public static Generation of(String generation) {
		Assert.hasText(generation, "Generation must not be empty");
		if (!"*".equals(generation) && !GENERATION_PATTERN.matcher(generation).matches()) {
			throw new IllegalArgumentException(
					"Generation '%s' must be a numeric project generation such as '6', '6.0', '6.0.x', or '6.0.1'"
							.formatted(generation));
		}
		return new Generation(generation);
	}

	@Override
	public boolean test(String version) {
		if (this.generation.equals("*")) {
			return true;
		}
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
		return this.versionPredicate;
	}

	/**
	 * Return the normalized generation value.
	 * @return the normalized generation value.
	 */
	public String value() {
		if (this.generation.equals("*") || StringUtils.isEmpty(this.generation)) {
			return "";
		}
		return this.generation + ".x";
	}

	@Override
	public String toString() {
		return this.generation;
	}

	private static String normalize(String value) {
		return value.endsWith(".x") ? value.substring(0, value.length() - 2) : value;
	}

	private static ArtifactVersion innermost(ArtifactVersion version) {
		ArtifactVersion candidate = version;
		while (candidate.isWrapped()) {
			candidate = candidate.getVersion();
		}
		return candidate;
	}

}
