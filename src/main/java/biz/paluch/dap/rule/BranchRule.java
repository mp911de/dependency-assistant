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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Dependency rules that apply to one branch or project-version pattern.
 *
 * <p>A branch rule can inherit default artifact rules. Its own artifact rules
 * take precedence; an artifact they do not govern falls back to the inherited
 * defaults, still subject to this rule's upgrade-strategy limits.
 *
 * @author Mark Paluch
 */
public class BranchRule implements Predicate<String>, Comparable<BranchRule> {

	private final boolean fallback;

	private final String pattern;

	private final int specificity;

	private final Predicate<String> predicate;

	private final Collection<ArtifactRule> artifacts;

	private final Collection<ArtifactRule> defaultArtifacts;

	private final Set<UpgradeStrategy> upgradeStrategies;

	private BranchRule(boolean fallback, String pattern, Collection<ArtifactRule> artifacts,
			Collection<ArtifactRule> defaultArtifacts, Set<UpgradeStrategy> upgradeStrategies) {

		this.fallback = fallback;
		this.pattern = pattern;
		this.specificity = specificity(pattern);
		this.predicate = ArtifactPattern.glob(pattern);
		this.artifacts = artifacts;
		this.defaultArtifacts = defaultArtifacts;
		this.upgradeStrategies = upgradeStrategies;
	}

	/**
	 * Create a branch rule.
	 *
	 * @param pattern the branch or project-version pattern.
	 * @param artifacts the artifact rules.
	 * @param upgradeStrategies the supported upgrade strategies; empty for no
	 * limits.
	 * @return the branch rule.
	 */
	public static BranchRule of(String pattern, Collection<ArtifactRule> artifacts,
			Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(false, pattern, artifacts, List.of(), upgradeStrategies);
	}

	/**
	 * Create a fallback rule matching every branch, with default artifact
	 * dependency rules and upgrade-strategy limits. Artifacts without a matching
	 * artifact rule resolve to a present rule enforcing the upgrade-strategy
	 * limits.
	 *
	 * @param artifacts the default artifact dependency rules.
	 * @param upgradeStrategies the supported upgrade strategies; empty for no
	 * limits.
	 * @return the fallback branch rule.
	 */
	public static BranchRule fallback(Collection<ArtifactRule> artifacts, Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(true, "*", artifacts, List.of(), upgradeStrategies);
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

	/**
	 * Create a copy of this branch rule that inherits the given default artifact
	 * rules for artifacts not governed by its own artifact rules.
	 *
	 * @param defaultArtifacts the default artifact rules to inherit.
	 * @return the inheriting branch rule.
	 */
	BranchRule withDefaults(Collection<ArtifactRule> defaultArtifacts) {
		return defaultArtifacts.isEmpty() ? this
				: new BranchRule(this.fallback, this.pattern, this.artifacts, defaultArtifacts, this.upgradeStrategies);
	}

	BranchRule withUpgradeStrategies(Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(this.fallback, this.pattern, this.artifacts, this.defaultArtifacts, upgradeStrategies);
	}

	public Set<UpgradeStrategy> upgradeStrategies() {
		return this.upgradeStrategies;
	}

	/**
	 * Select the {@link DependencyRule} that applies to the given
	 * {@link ArtifactId}.
	 *
	 * <p>This rule's own artifact rules are consulted first, then the inherited
	 * default artifact rules; within each, the most specific matching
	 * {@link ArtifactRule} wins. Inherited rules remain subject to this rule's
	 * upgrade-strategy limits. Without any match, a fallback rule yields a present
	 * rule enforcing this branch's upgrade-strategy limits; otherwise
	 * {@link DependencyRule#absent()} is returned.
	 *
	 * @param artifactId the artifact to select a dependency rule for.
	 * @param branchName the current branch name, can be {@literal null} if the
	 * project is not versioned.
	 * @param projectVersion the project version, can be {@literal null} if not
	 * provided.
	 * @return the effective dependency rule.
	 */
	public DependencyRule select(Rules parentRules, ArtifactId artifactId, @Nullable String branchName,
			@Nullable ArtifactVersion projectVersion, boolean semanticUpgradingMode) {

		DependencyRule rule = selectRule(parentRules, this.artifacts, artifactId, branchName, projectVersion,
				semanticUpgradingMode);
		if (rule != null) {
			return rule;
		}

		rule = selectRule(parentRules, this.defaultArtifacts, artifactId, branchName, projectVersion,
				semanticUpgradingMode);
		if (rule != null) {
			return rule;
		}

		return this.fallback
				? new ResolvedDependencyRule(Generations.unconstrained(), "", this::supports, semanticUpgradingMode)
				: DependencyRule.absent();
	}

	private @Nullable DependencyRule selectRule(Rules parentRules, Collection<ArtifactRule> artifacts,
			ArtifactId artifactId, @Nullable String branchName, @Nullable ArtifactVersion projectVersion,
			boolean semanticUpgradingMode) {

		return artifacts.stream()
				.filter(it -> it.pattern().test(artifactId))
				.max(ArtifactRule::compareTo)
				.<DependencyRule>map(it -> {

					String name = it.name();
					if (StringUtils.isEmpty(name)) {
						DependencyRule dependencyRule = parentRules.resolve(artifactId, branchName, projectVersion);
						if (dependencyRule.isPresent()) {
							name = dependencyRule.getDependencyName();
						}
					}

					return new ResolvedDependencyRule(it.generations(), name, this::supports, semanticUpgradingMode);
				})
				.orElse(null);
	}

	@Override
	public boolean test(String value) {
		return this.predicate.test(value);
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
