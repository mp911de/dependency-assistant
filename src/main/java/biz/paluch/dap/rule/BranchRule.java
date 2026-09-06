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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import biz.paluch.dap.support.UpgradeStrategy;

/**
 * Artifact rules and upgrade-strategy limits selected by a branch or project
 * version pattern.
 *
 * <p>Natural ordering ranks exact patterns above wildcard patterns and the
 * match-all pattern. Artifact-rule inheritance and effective rule resolution
 * are owned by {@link DependencyRules}.
 *
 * @author Mark Paluch
 */
public class BranchRule implements Predicate<String>, Comparable<BranchRule> {

	private final boolean fallback;

	private final KnownPattern pattern;

	private final int specificity;

	private final Collection<ArtifactRule> artifacts;

	private final Set<UpgradeStrategy> upgradeStrategies;

	private BranchRule(boolean fallback, KnownPattern pattern, Collection<ArtifactRule> artifacts,
			Collection<UpgradeStrategy> upgradeStrategies) {

		this.fallback = fallback;
		this.pattern = pattern;
		this.specificity = specificity(pattern.getPattern());
		this.artifacts = List.copyOf(artifacts);
		this.upgradeStrategies = Set.copyOf(upgradeStrategies);
	}

	/**
	 * Create a rule for every branch. Both collections are copied.
	 *
	 * @param upgradeStrategies permitted strategies, or empty for no limits.
	 */
	public static BranchRule of(Collection<ArtifactRule> artifacts,
			Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(false, KnownPattern.ANY, artifacts, upgradeStrategies);
	}

	/**
	 * Create a rule for a branch or project-version pattern. Both collections are
	 * copied.
	 *
	 * @param upgradeStrategies permitted strategies, or empty for no limits.
	 */
	public static BranchRule of(String pattern, Collection<ArtifactRule> artifacts,
			Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(false, KnownPattern.of(pattern), artifacts, upgradeStrategies);
	}

	/**
	 * Create a fallback that retains branch governance even without a matching
	 * artifact rule. Both collections are copied.
	 *
	 * @param upgradeStrategies permitted strategies, or empty for no limits.
	 */
	public static BranchRule fallback(Collection<ArtifactRule> artifacts, Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(true, KnownPattern.ANY, artifacts, upgradeStrategies);
	}

	private static int specificity(String pattern) {
		if ("*".equals(pattern)) {
			return 0;
		}
		return pattern.contains("*") ? 1 : 2;
	}

	public boolean hasUpgradeStrategies() {
		return !this.upgradeStrategies.isEmpty();
	}

	/**
	 * Return whether the strategy is permitted. An unrestricted rule permits every
	 * strategy.
	 */
	public boolean supports(UpgradeStrategy upgradeStrategy) {
		return this.upgradeStrategies.isEmpty() || this.upgradeStrategies.contains(upgradeStrategy);
	}

	boolean isFallback() {
		return this.fallback;
	}

	Collection<ArtifactRule> artifacts() {
		return this.artifacts;
	}

	BranchRule withUpgradeStrategies(Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(this.fallback, this.pattern, this.artifacts, upgradeStrategies);
	}

	/**
	 * Return the immutable strategy limits, or an empty set for no limits.
	 */
	public Set<UpgradeStrategy> upgradeStrategies() {
		return this.upgradeStrategies;
	}

	@Override
	public boolean test(String value) {
		return this.pattern.test(value);
	}

	@Override
	public int compareTo(BranchRule o) {
		return Integer.compare(this.specificity, o.specificity);
	}

	@Override
	public String toString() {
		return "BranchRule{" +
				"pattern='" + pattern + '\'' +
				", artifacts=" + artifacts +
				", upgradeStrategies=" + upgradeStrategies +
				'}';
	}

}
