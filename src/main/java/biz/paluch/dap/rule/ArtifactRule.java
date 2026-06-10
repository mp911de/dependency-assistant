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

	private final Generation generation;

	private ArtifactRule(ArtifactPattern pattern, String name, Generation generation) {
		this.pattern = pattern;
		this.name = name;
		this.generation = generation;
	}

	/**
	 * Create an artifact rule.
	 *
	 * @param pattern the artifact pattern.
	 * @param generation the required generation, see {@link Generation#of(String)}.
	 * @return the artifact rule.
	 * @see #of(String, String, String)
	 */
	public static ArtifactRule of(String pattern, String generation) {
		return new ArtifactRule(ArtifactPattern.of(pattern), "", Generation.of(generation));
	}

	/**
	 * Create a named artifact rule.
	 *
	 * @param pattern the artifact pattern.
	 * @param name the friendly display name.
	 * @param generation the required generation, see {@link Generation#of(String)}.
	 * @return the artifact rule.
	 */
	public static ArtifactRule of(String pattern, String name, String generation) {
		return new ArtifactRule(ArtifactPattern.of(pattern), name, Generation.of(generation));
	}

	public ArtifactPattern pattern() {
		return this.pattern;
	}

	public String name() {
		return this.name;
	}

	public Generation generation() {
		return this.generation;
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
		       ", generation=" + generation +
		       '}';
	}
}
