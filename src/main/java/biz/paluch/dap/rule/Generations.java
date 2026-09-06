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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactVersion;
import com.intellij.openapi.util.Predicates;

/**
 * Value object representing the permitted {@link Generation generations} for
 * one governed dependency.
 *
 * <p>A version {@linkplain #test(String) passes} when it falls within any
 * listed generation (union semantics). Order carries no matching meaning.
 *
 * @author Mark Paluch
 * @see Generation
 */
public class Generations implements Predicate<String> {

	private static final Generations UNCONSTRAINED = new Generations(List.of());

	private final List<Generation> generations;

	private final String rendered;

	private final Predicate<ArtifactVersion> versionPredicate;

	private Generations(List<Generation> generations) {
		this.generations = List.copyOf(generations);

		List<String> values = generations.stream().map(Generation::value).toList();
		this.rendered = switch (values.size()) {
		case 0 -> "";
		case 1 -> values.get(0);
		case 2 -> values.get(0) + " or " + values.get(1);
		default -> String.join(", ", values.subList(0, values.size() - 1)) + ", or " + values.getLast();
		};
		this.versionPredicate = generations.isEmpty() ? Predicates.alwaysTrue()
				: version -> test(version.unwrap().toString());
	}

	/**
	 * Parse permitted development lines, preserving declaration order and removing
	 * duplicates. Empty input or {@code *} means unconstrained.
	 *
	 * @throws IllegalArgumentException if a generation cannot be parsed.
	 * @see Generation#of(String)
	 */
	public static Generations from(String... generations) {
		List<Generation> result = new ArrayList<>(generations.length);
		for (String generation : generations) {
			if ("*".equals(generation)) {
				return UNCONSTRAINED;
			}
			Generation parsed = Generation.of(generation);
			if (!result.contains(parsed)) {
				result.add(parsed);
			}
		}
		return result.isEmpty() ? UNCONSTRAINED : new Generations(result);
	}

	/**
	 * Return generations accepting every version.
	 */
	public static Generations unconstrained() {
		return UNCONSTRAINED;
	}

	public boolean isConstrained() {
		return !this.generations.isEmpty();
	}

	@Override
	public boolean test(String version) {

		if (!isConstrained()) {
			return true;
		}

		for (Generation generation : generations) {
			if (generation.test(version)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return a predicate that tests the innermost version against the permitted
	 * lines.
	 */
	public Predicate<ArtifactVersion> asVersionPredicate() {
		return this.versionPredicate;
	}

	/**
	 * Return the display text, such as {@code 3.2.x or 4.x}, or empty if
	 * unconstrained.
	 */
	public String value() {
		return rendered;
	}

	/**
	 * Return the immutable generations in declaration order, or empty if
	 * unconstrained.
	 */
	public List<Generation> list() {
		return this.generations;
	}

	@Override
	public boolean equals(Object o) {

		if (!(o instanceof Generations that)) {
			return false;
		}
		return this.generations.equals(that.generations);
	}

	@Override
	public int hashCode() {
		return this.generations.hashCode();
	}

	@Override
	public String toString() {
		return !isConstrained() ? "*" : value();
	}

}
