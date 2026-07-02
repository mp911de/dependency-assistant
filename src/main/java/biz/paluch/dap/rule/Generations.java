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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Versioned;
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
		this.generations = generations;

		List<String> values = generations.stream().map(Generation::value).toList();
		this.rendered = switch (values.size()) {
		case 0 -> "";
		case 1 -> values.get(0);
		case 2 -> values.get(0) + " or " + values.get(1);
		default -> String.join(", ", values.subList(0, values.size() - 1)) + ", or " + values.getLast();
		};
		this.versionPredicate = generations.isEmpty() ? Predicates.alwaysTrue()
				: version -> test(Versioned.of(version).unwrap().toString());
	}

	/**
	 * Create generations from project development lines.
	 *
	 * <p>Duplicates are dropped while the declared order is preserved. A list
	 * containing {@code *} collapses to the {@linkplain #unconstrained()
	 * unconstrained} instance.
	 *
	 * @param generations the project development lines; must not be
	 * {@literal null}.
	 * @return generations matching any of the given development lines;
	 * {@linkplain #unconstrained() unconstrained} when none are given.
	 * @throws IllegalArgumentException if an entry is not a valid generation, see
	 * {@link Generation#of(String)}.
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
	 * Return the unconstrained instance accepting every version.
	 *
	 * @return the shared unconstrained instance.
	 */
	public static Generations unconstrained() {
		return UNCONSTRAINED;
	}

	/**
	 * Return whether this object constrains versions to at least one generation.
	 *
	 * @return {@literal true} if at least one generation is listed;
	 * {@literal false} for the {@linkplain #unconstrained() unconstrained}
	 * instance.
	 */
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
	 * Return these generations as an {@link ArtifactVersion} predicate.
	 *
	 * <p>The returned predicate unwraps prefixed versions before testing the
	 * innermost version string against any listed generation.
	 *
	 * @return an {@link ArtifactVersion} predicate backed by these generations.
	 */
	public Predicate<ArtifactVersion> asVersionPredicate() {
		return this.versionPredicate;
	}

	/**
	 * Return the rendered generation value joining the normalized
	 * {@linkplain Generation#value() generation values}: {@code 3.2.x}, then
	 * {@code 3.2.x or 4.x}, then {@code 3.1.x, 3.2.x, or 4.x}.
	 *
	 * @return the rendered generation value; empty for the
	 * {@linkplain #unconstrained() unconstrained} instance.
	 */
	public String value() {
		return rendered;
	}

	/**
	 * Return the listed generations in declared order.
	 *
	 * @return the listed generations; empty for the {@linkplain #unconstrained()
	 * unconstrained} instance.
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
