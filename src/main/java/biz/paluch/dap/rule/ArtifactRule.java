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

/**
 * Dependency rule for an artifact pattern.
 *
 * @author Mark Paluch
 */
public class ArtifactRule implements Comparable<ArtifactRule> {

	private final ArtifactPattern pattern;

	private final String name;

	private final Generations generations;

	private ArtifactRule(ArtifactPattern pattern, String name, Generations generations) {
		this.pattern = pattern;
		this.name = name;
		this.generations = generations;
	}

	/**
	 * Create an artifact rule.
	 *
	 * @param pattern the artifact pattern.
	 * @param generations the required generations, see
	 * {@link Generations#from(String...)}.
	 * @return the artifact rule.
	 * @see #of(String, String, Generations)
	 */
	public static ArtifactRule of(String pattern, Generations generations) {
		return new ArtifactRule(ArtifactPattern.of(pattern), "", generations);
	}

	/**
	 * Create a named artifact rule.
	 *
	 * @param pattern the artifact pattern.
	 * @param name the friendly display name.
	 * @param generations the required generations, see
	 * {@link Generations#from(String...)}.
	 * @return the artifact rule.
	 */
	public static ArtifactRule of(String pattern, String name, Generations generations) {
		return new ArtifactRule(ArtifactPattern.of(pattern), name, generations);
	}

	public ArtifactPattern pattern() {
		return this.pattern;
	}

	public String name() {
		return this.name;
	}

	public Generations generations() {
		return this.generations;
	}

	@Override
	public int compareTo(ArtifactRule o) {
		return this.pattern.compareTo(o.pattern);
	}

	@Override
	public String toString() {
		return "ArtifactRule{" +
		       "pattern=" + pattern +
		       ", name='" + name + '\'' +
		       ", generations=" + generations +
		       '}';
	}
}
