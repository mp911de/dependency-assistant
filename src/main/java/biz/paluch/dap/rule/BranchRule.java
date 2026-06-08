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
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.UpgradeStrategy;

/**
 * Dependency rules that apply to one branch or project-version pattern.
 *
 * @author Mark Paluch
 */
public class BranchRule implements Predicate<String>, Comparable<BranchRule> {

	private final boolean fallback;

	private final String pattern;

	private final ArtifactPattern comparisonPattern;

	private final Predicate<String> predicate;

	private final Collection<ArtifactRule> artifacts;

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
		this.upgradeStrategies = upgradeStrategies;
	}

	/**
	 * Create a branch rule without upgrade-strategy limits.
	 * @param pattern the branch or project-version pattern.
	 * @param artifacts the artifact rules.
	 */
	public static BranchRule of(String pattern, Collection<ArtifactRule> artifacts) {
		return new BranchRule(false, pattern, artifacts, Set.of());
	}

	/**
	 * Create an absent branch rule with default artifact dependency rules.
	 * @param artifacts the default artifact dependency rules.
	 */
	public static BranchRule absent(Collection<ArtifactRule> artifacts) {
		return new BranchRule(true, "*", it -> true, artifacts, Set.of());
	}

	/**
	 * Create an absent branch rule with default artifact dependency rules and
	 * upgrade-strategy limits.
	 * @param artifacts the default artifact dependency rules.
	 * @param upgradeStrategies the supported upgrade strategies.
	 */
	public static BranchRule absent(Collection<ArtifactRule> artifacts, Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(false, "*", it -> true, artifacts, upgradeStrategies);
	}

	/**
	 * Create an absent branch rule with default artifact dependency rules and
	 * upgrade-strategy limits.
	 *
	 * @param artifacts         the default artifact dependency rules.
	 * @param upgradeStrategies the supported upgrade strategies.
	 */
	public static BranchRule fallback(Collection<ArtifactRule> artifacts, Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(true, "*", it -> true, artifacts, upgradeStrategies);
	}

	/**
	 * Create a branch rule with upgrade-strategy limits.
	 * @param pattern the branch or project-version pattern.
	 * @param artifacts the artifact rules.
	 * @param upgradeStrategies the supported upgrade strategies.
	 */
	public static BranchRule of(String pattern, Collection<ArtifactRule> artifacts,
			Set<UpgradeStrategy> upgradeStrategies) {
		return new BranchRule(false, pattern, artifacts, upgradeStrategies);
	}

	/**
	 * Return whether this branch rule permits the given upgrade strategy.
	 * @param upgradeStrategy the upgrade strategy.
	 */
	public boolean supports(UpgradeStrategy upgradeStrategy) {
		return this.upgradeStrategies.isEmpty() || this.upgradeStrategies.contains(upgradeStrategy);
	}

	public Collection<ArtifactRule> artifacts() {
		return this.artifacts;
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
	 * @param artifactId the artifact Id to select a dependency rule.
	 * @return the effective dependency rule.
	 */
	public DependencyRule select(ArtifactId artifactId) {

		Optional<DependencyRule> dependencyRule = artifacts().stream()
				.filter(it -> it.pattern().test(artifactId))
				.max(ArtifactRule::compareTo)
				.<DependencyRule>map(it -> new ResolvedDependencyRule(it.generation(), this::supports));

		return dependencyRule.orElseGet(() -> {

			if (fallback) {
				return new FallbackDependencyRule();
			}

			return DependencyRule.absent();
		});
	}

	class FallbackDependencyRule implements DependencyRule {

		@Override
		public String getGeneration() {
			return "";
		}

		@Override
		public boolean isEnabled(UpgradeStrategy upgradeStrategy) {
			return BranchRule.this.supports(upgradeStrategy);
		}

		@Override
		public boolean isDefined() {
			return true;
		}

		@Override
		public boolean test(ArtifactVersion artifactVersion) {
			return true;
		}

	}
}
