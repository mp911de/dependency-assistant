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
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
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

	private final ArtifactPattern comparisonPattern;

	private final Predicate<String> predicate;

	private final Collection<ArtifactRule> artifacts;

	private final Collection<ArtifactRule> defaultArtifacts;

	private final Set<UpgradeStrategy> upgradeStrategies;

	private BranchRule(boolean fallback, String pattern, Collection<ArtifactRule> artifacts, Set<UpgradeStrategy> upgradeStrategies) {
		this(fallback, pattern, glob(pattern), artifacts, upgradeStrategies);
	}

	private BranchRule(boolean fallback, String pattern, Predicate<String> predicate, Collection<ArtifactRule> artifacts,
			Set<UpgradeStrategy> upgradeStrategies) {

		this.fallback = fallback;
		this.pattern = pattern;
		this.comparisonPattern = ArtifactPattern.of(pattern);
		this.predicate = predicate;
		this.artifacts = artifacts;
		this.defaultArtifacts = List.of();
		this.upgradeStrategies = upgradeStrategies;
	}

	private BranchRule(BranchRule original, Collection<ArtifactRule> defaultArtifacts) {

		this.fallback = original.fallback;
		this.pattern = original.pattern;
		this.comparisonPattern = original.comparisonPattern;
		this.predicate = original.predicate;
		this.artifacts = original.artifacts;
		this.defaultArtifacts = defaultArtifacts;
		this.upgradeStrategies = original.upgradeStrategies;
	}

	/**
	 * Create a branch rule without upgrade-strategy limits.
	 *
	 * @param pattern the branch or project-version pattern.
	 * @param artifacts the artifact rules.
	 * @return the branch rule.
	 */
	public static BranchRule of(String pattern, Collection<ArtifactRule> artifacts) {
		return new BranchRule(false, pattern, artifacts, Set.of());
	}

	/**
	 * Create an absent branch rule with default artifact dependency rules.
	 * Artifacts without a matching artifact rule resolve to a present fallback
	 * rule.
	 *
	 * @param artifacts the default artifact dependency rules.
	 * @return the branch rule.
	 * @see #of(Collection, Set)
	 */
	public static BranchRule of(Collection<ArtifactRule> artifacts) {
		return new BranchRule(true, "*", it -> true, artifacts, Set.of());
	}

	/**
	 * Create an absent branch rule with default artifact dependency rules and
	 * upgrade-strategy limits. Artifacts without a matching artifact rule
	 * resolve to {@link DependencyRule#absent()}.
	 *
	 * @param artifacts the default artifact dependency rules.
	 * @param upgradeStrategies the supported upgrade strategies.
	 * @return the branch rule.
	 * @see #semanticFallback(Collection, Set)
	 */
	public static BranchRule of(Collection<ArtifactRule> artifacts, Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(false, "*", it -> true, artifacts, upgradeStrategies);
	}

	/**
	 * Create a semantic versioning fallback rule with artifact dependency rules and
	 * upgrade-strategy limits. This rule applies semantic version upgrade rules
	 * (i.e. patch upgrades for patch releases, minor upgrades for minor releases).
	 * Artifacts without a matching artifact rule resolve to a present fallback
	 * rule enforcing the upgrade-strategy limits.
	 *
	 * @param artifacts the default artifact dependency rules.
	 * @param upgradeStrategies the supported upgrade strategies.
	 * @return the fallback branch rule.
	 */
	public static BranchRule semanticFallback(Collection<ArtifactRule> artifacts,
			Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(true, "*", it -> true, artifacts, upgradeStrategies);
	}

	/**
	 * Create a branch rule with upgrade-strategy limits.
	 *
	 * @param pattern the branch or project-version pattern.
	 * @param artifacts the artifact rules.
	 * @param upgradeStrategies the supported upgrade strategies.
	 * @return the branch rule.
	 */
	public static BranchRule of(String pattern, Collection<ArtifactRule> artifacts,
			Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(false, pattern, artifacts, upgradeStrategies);
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
		return defaultArtifacts.isEmpty() ? this : new BranchRule(this, defaultArtifacts);
	}

	public Set<UpgradeStrategy> upgradeStrategies() {
		return this.upgradeStrategies;
	}

	@Override
	public boolean test(String value) {
		return this.predicate.test(value);
	}

	@Override
	public int compareTo(BranchRule o) {
		return this.comparisonPattern.compareTo(o.comparisonPattern);
	}

	@Override
	public String toString() {
		return "BranchRule{" +
		       "pattern='" + pattern + '\'' +
		       ", comparisonPattern=" + comparisonPattern +
		       ", predicate=" + predicate +
		       ", artifacts=" + artifacts +
		       ", upgradeStrategies=" + upgradeStrategies +
		       '}';
	}

	private static Predicate<String> glob(String pattern) {

		Pattern compiled = Pattern.compile(Pattern.quote(pattern).replace("*", "\\E.*\\Q"));
		return compiled.asMatchPredicate();
	}

	/**
	 * Select the {@link DependencyRule} that applies to the given {@link ArtifactId}.
	 *
	 * <p>This rule's own artifact rules are consulted first, then the inherited
	 * default artifact rules; within each, the most specific matching
	 * {@link ArtifactRule} wins. Inherited rules remain subject to this rule's
	 * upgrade-strategy limits. Without any match, a fallback rule yields a present
	 * rule enforcing this branch's upgrade-strategy limits; otherwise
	 * {@link DependencyRule#absent()} is returned.
	 *
	 * @param artifactId the artifact to select a dependency rule for.
	 * @return the effective dependency rule; never {@literal null}.
	 */
	public DependencyRule select(ArtifactId artifactId) {

		DependencyRule rule = selectRule(this.artifacts, artifactId);
		if (rule != null) {
			return rule;
		}

		rule = selectRule(this.defaultArtifacts, artifactId);
		if (rule != null) {
			return rule;
		}

		return this.fallback ? new FallbackDependencyRule() : DependencyRule.absent();
	}

	private @Nullable DependencyRule selectRule(Collection<ArtifactRule> artifacts, ArtifactId artifactId) {

		return artifacts.stream()
				.filter(it -> it.pattern().test(artifactId))
				.max(ArtifactRule::compareTo)
				.<DependencyRule>map(it -> {

					String name = it.name();
					if (StringUtils.isEmpty(name) && artifacts != defaultArtifacts) {
						DependencyRule dependencyRule = selectRule(defaultArtifacts, artifactId);
						if (dependencyRule != null && dependencyRule.isPresent()) {
							name = dependencyRule.getDependencyName();
						}
					}

					return new ResolvedDependencyRule(it.generations(), name, this::supports);
				})
				.orElse(null);
	}

	class FallbackDependencyRule implements DependencyRule {

		@Override
		public boolean isPresent() {
			return true;
		}

		@Override
		public Generations getGenerations() {
			return Generations.unconstrained();
		}

		@Override
		public String getDependencyName() {
			return "";
		}

		@Override
		public boolean isEnabled(UpgradeStrategy upgradeStrategy) {
			return BranchRule.this.supports(upgradeStrategy);
		}

		@Override
		public boolean test(ArtifactVersion artifactVersion) {
			return true;
		}

		@Override
		public @Nullable Release suggestRemediation(Releases releases) {
			return null;
		}
	}
}
