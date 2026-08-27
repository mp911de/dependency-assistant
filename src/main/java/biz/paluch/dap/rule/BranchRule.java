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
	 * Create a non-fallback rule that matches every branch and governs only the
	 * given artifacts.
	 *
	 * @param artifacts the artifact rules snapshotted by the returned rule.
	 * @param upgradeStrategies the supported upgrade strategies; empty for no
	 * limits. The set is snapshotted by the returned rule.
	 * @return the branch rule.
	 */
	public static BranchRule of(Collection<ArtifactRule> artifacts,
			Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(false, KnownPattern.ANY, artifacts, upgradeStrategies);
	}

	/**
	 * Create a non-fallback rule for the given branch or project-version pattern.
	 *
	 * @param pattern the branch or project-version pattern.
	 * @param artifacts the artifact rules snapshotted by the returned rule.
	 * @param upgradeStrategies the supported upgrade strategies; empty for no
	 * limits. The set is snapshotted by the returned rule.
	 * @return the branch rule.
	 */
	public static BranchRule of(String pattern, Collection<ArtifactRule> artifacts,
			Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(false, KnownPattern.of(pattern), artifacts, upgradeStrategies);
	}

	/**
	 * Create a fallback declaration matching every branch, with default artifact
	 * rules and upgrade-strategy limits. {@link DependencyRules} uses this marker
	 * to retain branch-level governance when no artifact rule matches.
	 *
	 * @param artifacts the default artifact dependency rules snapshotted by the
	 * returned rule.
	 * @param upgradeStrategies the supported upgrade strategies; empty for no
	 * limits. The set is snapshotted by the returned rule.
	 * @return the fallback branch rule.
	 */
	public static BranchRule fallback(Collection<ArtifactRule> artifacts, Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(true, KnownPattern.ANY, artifacts, upgradeStrategies);
	}

	/**
	 * Rank pattern specificity: exact patterns order highest, then wildcard
	 * patterns, then the match-all pattern.
	 */
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
	 * Return whether this branch rule permits the given upgrade strategy. A rule
	 * without upgrade-strategy limits permits every strategy.
	 *
	 * @param upgradeStrategy the upgrade strategy.
	 * @return {@literal true} if the strategy is permitted; {@literal false}
	 * otherwise.
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
	 * Return the explicitly configured or inferred upgrade-strategy limits.
	 *
	 * @return the strategy set retained by this rule. An empty set means no limits.
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
