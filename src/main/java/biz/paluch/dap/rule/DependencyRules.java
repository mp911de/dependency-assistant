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
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.NumericVersion;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Resolution model for default Artifact Rules and branch-specific overrides.
 *
 * <p>Resolution selects the most specific branch rule first by branch name,
 * then by the displayed or unwrapped project version. Within the selected
 * branch, the most specific matching artifact pattern wins; equally specific
 * patterns resolve to the first declared rule.
 *
 * <p>Branch rules inherit the top-level artifact rules: an artifact without a
 * matching branch-level artifact rule falls back to the top-level rules, still
 * subject to the branch's upgrade-strategy limits. When semver updating is
 * active, a three-segment project version with a non-zero patch segment limits
 * an otherwise unrestricted rule to patch and release upgrades. Explicit branch
 * upgrade strategies take precedence over that inference.
 *
 * @author Mark Paluch
 * @see BranchRule
 * @see ArtifactRule
 * @see DependencyfileService
 */
public class DependencyRules {

	private static final DependencyRules ABSENT = new DependencyRules(List.of(), List.of(), SemVerUpdating.DISABLED);

	private final Collection<ArtifactRule> artifacts;

	private final Collection<BranchRule> branches;

	private final SemVerUpdating semVerUpdating;

	private DependencyRules(Collection<ArtifactRule> artifacts, Collection<BranchRule> branches,
			SemVerUpdating semVerUpdating) {

		this.artifacts = List.copyOf(artifacts);
		this.branches = List.copyOf(branches);
		this.semVerUpdating = semVerUpdating;
	}

	static DependencyRules absent() {
		return ABSENT;
	}

	/**
	 * Create a builder for {@code DependencyRules}.
	 *
	 * @return a new {@code DependencyRules} builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a branch-rule builder.
	 *
	 * @param pattern the branch or project-version pattern.
	 * @return a new branch-rule builder.
	 */
	public static BranchRuleBuilder branch(String pattern) {
		return new BranchRuleBuilder(pattern);
	}

	/**
	 * Create an artifact-rule builder.
	 *
	 * @param pattern the artifact pattern.
	 * @return a new artifact-rule builder.
	 */
	public static ArtifactRuleBuilder artifact(String pattern) {
		return new ArtifactRuleBuilder(pattern);
	}

	/**
	 * Resolve the effective Dependency Rule for the given artifact and branch
	 * context.
	 *
	 * @param artifactId the artifact to resolve.
	 * @param branchName the active branch name, or {@literal null} when
	 * unavailable.
	 * @param projectVersion the project version used for branch matching and semver
	 * inference, or {@literal null} when unavailable.
	 * @return the governing dependency rule, or {@link DependencyRule#absent()}
	 * when no artifact rule applies.
	 */
	public DependencyRule resolve(ArtifactId artifactId, @Nullable String branchName,
			@Nullable ArtifactVersion projectVersion) {
		return resolve(artifactId, branchName, projectVersion, false);
	}

	/**
	 * Resolve a dependency rule for the given artifact, optionally suppressing
	 * semantic version upgrading.
	 *
	 * <p>When {@code suppressSemanticUpgrading} is {@literal true} the
	 * project-version derived (inferred) upgrade-strategy limits are skipped and
	 * {@link DependencyRule#isSemanticUpgradingEnabled()} reports {@literal false}.
	 * Generations, Artifact Display Names, and explicitly declared {@code upgrades}
	 * limits are unaffected. This allows plugin declarations to remain governed
	 * without applying dependency-oriented semver inference.
	 *
	 * @param artifactId the artifact to resolve.
	 * @param branchName the active branch name; can be {@literal null}.
	 * @param projectVersion the project version used for branch matching and semver
	 * inference, or {@literal null} when unavailable.
	 * @param suppressSemanticUpgrading whether to suppress inferred semantic
	 * upgrading.
	 * @return the resolved dependency rule, or an absent rule.
	 */
	DependencyRule resolve(ArtifactId artifactId, @Nullable String branchName,
			@Nullable ArtifactVersion projectVersion, boolean suppressSemanticUpgrading) {

		BranchRule branchRule = resolveBranchRule(branchName, projectVersion, suppressSemanticUpgrading);
		boolean semanticUpgradingMode = this.semVerUpdating != SemVerUpdating.DISABLED && !suppressSemanticUpgrading;
		ArtifactRule defaultRule = mostSpecific(this.artifacts, artifactId);
		ArtifactRule rule = mostSpecific(branchRule.artifacts(), artifactId);
		if (rule == null) {
			rule = defaultRule;
		}

		if (rule == null) {
			return branchRule.isFallback()
					? new ResolvedDependencyRule(Generations.unconstrained(), "", branchRule::supports,
							semanticUpgradingMode)
					: DependencyRule.absent();
		}

		String name = rule.name();
		if (StringUtils.isEmpty(name) && defaultRule != null) {
			name = defaultRule.name();
		}
		return new ResolvedDependencyRule(rule.generations(), name, branchRule::supports, semanticUpgradingMode);
	}

	/**
	 * Resolve the active branch rule, matching the branch name before the project
	 * version.
	 *
	 * <p>Without a matching branch rule, the returned rule carries the default
	 * Artifact Rules. When semver updating is active, a three-segment project
	 * version with a non-zero patch segment limits that rule to patch and release
	 * upgrades.
	 *
	 * @param branchName the active branch name; can be {@literal null}.
	 * @param projectVersion the project version; can be {@literal null}.
	 * @return the matching branch rule with effective upgrade-strategy limits, or a
	 * synthetic rule carrying the default Artifact Rules when no branch matches.
	 */
	BranchRule resolveBranchRule(@Nullable String branchName, @Nullable ArtifactVersion projectVersion) {
		return resolveBranchRule(branchName, projectVersion, false);
	}

	private BranchRule resolveBranchRule(@Nullable String branchName, @Nullable ArtifactVersion projectVersion,
			boolean suppressSemanticUpgrading) {

		boolean inferSemVer = this.semVerUpdating != SemVerUpdating.DISABLED && !suppressSemanticUpgrading;

		BranchRule branchRule = doResolveBranchRule(branchName, projectVersion);
		if (branchRule == null) {
			if (projectVersion != null && inferSemVer) {
				return BranchRule.fallback(this.artifacts, upgradeStrategies(projectVersion));
			}
			return BranchRule.of(this.artifacts, Set.of());
		}

		if (!inferSemVer || branchRule.hasUpgradeStrategies() || projectVersion == null) {
			return branchRule;
		}

		Set<UpgradeStrategy> upgradeStrategies = upgradeStrategies(projectVersion);
		return upgradeStrategies.isEmpty() ? branchRule : branchRule.withUpgradeStrategies(upgradeStrategies);
	}

	private @Nullable BranchRule doResolveBranchRule(@Nullable String branchName,
			@Nullable ArtifactVersion projectVersion) {
		if (StringUtils.hasText(branchName)) {
			BranchRule branchRule = selectBranchRule(branchName);
			if (branchRule != null) {
				return branchRule;
			}
		}
		if (projectVersion != null) {
			return selectBranchRule(projectVersion);
		}
		return null;
	}

	private @Nullable BranchRule selectBranchRule(String value) {
		return branches.stream().filter(it -> it.test(value)).max(BranchRule::compareTo).orElse(null);
	}

	private @Nullable BranchRule selectBranchRule(ArtifactVersion version) {

		ArtifactVersion unwrapped = version.unwrap();
		String displayVersion = version.toString();
		String innerMostVersion = unwrapped.toString();
		return branches.stream()
				.filter(it -> it.test(displayVersion) || it.test(innerMostVersion))
				.max(BranchRule::compareTo)
				.orElse(null);
	}

	private static @Nullable ArtifactRule mostSpecific(Collection<ArtifactRule> artifacts, ArtifactId artifactId) {

		ArtifactRule selected = null;
		for (ArtifactRule rule : artifacts) {
			if (rule.pattern().test(artifactId)
					&& (selected == null || rule.pattern().compareTo(selected.pattern()) > 0)) {
				selected = rule;
			}
		}
		return selected;
	}

	@Override
	public String toString() {
		return "DependencyRules{" +
		       "artifacts=" + artifacts +
		       ", branches=" + branches +
		       '}';
	}

	private static Set<UpgradeStrategy> upgradeStrategies(ArtifactVersion projectVersion) {

		ArtifactVersion candidate = projectVersion.unwrap();
		if (!(candidate instanceof NumericVersion numericVersion) || numericVersion.size() != 3) {
			return Set.of();
		}
		int[] parts = numericVersion.getParts();
		if (parts.length > 2 && parts[2] != 0) {
			return EnumSet.of(UpgradeStrategy.PATCH, UpgradeStrategy.RELEASE);
		}
		return Set.of();
	}

	/**
	 * Builder for {@link DependencyRules}.
	 */
	public static class Builder {

		private final List<ArtifactRule> artifacts = new ArrayList<>();

		private final List<BranchRule> branches = new ArrayList<>();

		private SemVerUpdating semVerUpdating = SemVerUpdating.INFERRED;

		private Builder() {
		}

		/**
		 * Add an artifact dependency rule.
		 *
		 * @param pattern the artifact pattern.
		 * @param generations the generation sources.
		 * @return this builder.
		 */
		public Builder artifact(String pattern, String... generations) {
			this.artifacts.add(ArtifactRule.of(pattern, Generations.from(generations)));
			return this;
		}

		/**
		 * Add a configured artifact dependency rule.
		 *
		 * @param pattern the artifact pattern.
		 * @param customizer customizes the artifact rule.
		 * @return this builder.
		 */
		public Builder artifact(String pattern, Consumer<ArtifactRuleBuilder> customizer) {

			ArtifactRuleBuilder builder = DependencyRules.artifact(pattern);
			customizer.accept(builder);
			this.artifacts.add(builder.build());
			return this;
		}

		/**
		 * Configure a branch rule.
		 *
		 * @param pattern the branch or project-version pattern.
		 * @param customizer customizes the branch rule.
		 * @return this builder.
		 */
		public Builder branch(String pattern, Consumer<BranchRuleBuilder> customizer) {

			BranchRuleBuilder builder = DependencyRules.branch(pattern);
			customizer.accept(builder);
			this.branches.add(builder.build());
			return this;
		}

		/**
		 * Set the semver updating mode that controls whether upgrade strategy limits
		 * are derived from the project version.
		 *
		 * @param semVerUpdating the semver updating mode.
		 * @return this builder.
		 */
		public Builder semVerUpdating(SemVerUpdating semVerUpdating) {
			this.semVerUpdating = semVerUpdating;
			return this;
		}

		/**
		 * Build the {@code DependencyRules}.
		 *
		 * @return the dependency rules.
		 */
		public DependencyRules build() {
			return new DependencyRules(List.copyOf(this.artifacts), List.copyOf(this.branches), this.semVerUpdating);
		}

	}

	/**
	 * Builder for {@link BranchRule}.
	 */
	public static class BranchRuleBuilder {

		private final String pattern;

		private final List<ArtifactRule> artifacts = new ArrayList<>();

		private Set<UpgradeStrategy> upgradeStrategies = Set.of();

		private BranchRuleBuilder(String pattern) {
			this.pattern = pattern;
		}

		/**
		 * Add an artifact rule to this branch rule.
		 *
		 * @param pattern the artifact pattern.
		 * @param generations the generation sources.
		 * @return this builder.
		 */
		public BranchRuleBuilder artifact(String pattern, String... generations) {
			this.artifacts.add(ArtifactRule.of(pattern, Generations.from(generations)));
			return this;
		}

		/**
		 * Add a configured artifact rule to this branch rule.
		 *
		 * @param pattern the artifact pattern.
		 * @param customizer customizes the artifact rule.
		 * @return this builder.
		 */
		public BranchRuleBuilder artifact(String pattern, Consumer<ArtifactRuleBuilder> customizer) {

			ArtifactRuleBuilder builder = DependencyRules.artifact(pattern);
			customizer.accept(builder);
			this.artifacts.add(builder.build());
			return this;
		}

		/**
		 * Limit this branch rule to the given upgrade strategies.
		 *
		 * @param upgradeStrategies the supported upgrade strategies.
		 * @return this builder.
		 */
		public BranchRuleBuilder upgrades(UpgradeStrategy... upgradeStrategies) {
			EnumSet<UpgradeStrategy> strategies = EnumSet.noneOf(UpgradeStrategy.class);
			strategies.addAll(Arrays.asList(upgradeStrategies));
			this.upgradeStrategies = strategies;
			return this;
		}

		/**
		 * Build a branch rule.
		 *
		 * @return the branch rule.
		 */
		public BranchRule build() {
			return BranchRule.of(this.pattern, List.copyOf(this.artifacts),
					this.upgradeStrategies.isEmpty() ? Set.of() : EnumSet.copyOf(this.upgradeStrategies));
		}

	}

	/**
	 * Builder for {@link ArtifactRule}.
	 */
	public static class ArtifactRuleBuilder {

		private final String pattern;

		private String name = "";

		private Generations generations = Generations.unconstrained();

		private ArtifactRuleBuilder(String pattern) {
			this.pattern = pattern;
		}

		/**
		 * Set the Artifact Display Name.
		 *
		 * @param name the Artifact Display Name.
		 * @return this builder.
		 */
		public ArtifactRuleBuilder name(String name) {
			this.name = name;
			return this;
		}

		/**
		 * Set the generations.
		 *
		 * @param generations the generation sources.
		 * @return this builder.
		 */
		public ArtifactRuleBuilder generation(String... generations) {
			return generation(Generations.from(generations));
		}

		/**
		 * Set the generations.
		 *
		 * @param generations the generations.
		 * @return this builder.
		 */
		public ArtifactRuleBuilder generation(Generations generations) {
			this.generations = generations;
			return this;
		}

		/**
		 * Build an artifact rule.
		 *
		 * @return the artifact rule.
		 */
		public ArtifactRule build() {
			return ArtifactRule.of(this.pattern, this.name, this.generations);
		}

	}

}
