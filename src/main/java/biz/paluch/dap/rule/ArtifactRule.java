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

/**
 * An artifact pattern with permitted generations and an optional display name.
 *
 * @author Mark Paluch
 * @see DependencyRules
 */
public class ArtifactRule {

	private final ArtifactPattern pattern;

	private final String name;

	private final Generations generations;

	private ArtifactRule(ArtifactPattern pattern, String name, Generations generations) {
		this.pattern = pattern;
		this.name = name;
		this.generations = generations;
	}

	public static ArtifactRule of(String pattern, Generations generations) {
		return new ArtifactRule(ArtifactPattern.of(pattern), "", generations);
	}

	/**
	 * Create a named artifact rule. An empty name leaves the rule unnamed.
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
	public String toString() {
		return "ArtifactRule{" +
		       "pattern=" + pattern +
		       ", name='" + name + '\'' +
		       ", generations=" + generations +
		       '}';
	}
}
