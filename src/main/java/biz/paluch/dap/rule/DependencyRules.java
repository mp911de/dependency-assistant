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
 * Default dependency rules with branch-specific overrides.
 *
 * <p>Branch names take precedence over project versions for branch selection.
 * Project versions match in displayed and unwrapped form. Within a branch, the
 * most specific artifact pattern wins. Ties retain the first declared rule.
 *
 * <p>Unmatched artifacts inherit top-level rules under the branch's strategy
 * limits. With semantic upgrading enabled, a three-part project version with a
 * nonzero patch restricts otherwise unrestricted rules to patch and release
 * upgrades. Explicit branch strategies take precedence.
 *
 * @author Mark Paluch
 * @see ArtifactPattern
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

	public static Builder builder() {
		return new Builder();
	}

	public static BranchRuleBuilder branch(String pattern) {
		return new BranchRuleBuilder(pattern);
	}

	public static ArtifactRuleBuilder artifact(String pattern) {
		return new ArtifactRuleBuilder(pattern);
	}

	/**
	 * Resolve the rule for the artifact in its branch and project-version context.
	 *
	 * @param branchName the active branch, or {@code null} if unavailable.
	 * @param projectVersion the project version, or {@code null} if unavailable.
	 * @return the governing rule, or {@link DependencyRule#absent()} if none
	 * applies.
	 */
	public DependencyRule resolve(ArtifactId artifactId, @Nullable String branchName,
			@Nullable ArtifactVersion projectVersion) {
		return resolve(artifactId, branchName, projectVersion, false);
	}

	/**
	 * Resolve a rule with optional suppression of semantic upgrading.
	 *
	 * <p>Suppression exempts plugin declarations from dependency-oriented
	 * inference. Generation limits, names, and explicit strategy limits still
	 * apply.
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

	public static class Builder {

		private final List<ArtifactRule> artifacts = new ArrayList<>();

		private final List<BranchRule> branches = new ArrayList<>();

		private SemVerUpdating semVerUpdating = SemVerUpdating.INFERRED;

		private Builder() {
		}

		public Builder artifact(String pattern, String... generations) {
			this.artifacts.add(ArtifactRule.of(pattern, Generations.from(generations)));
			return this;
		}

		public Builder artifact(String pattern, Consumer<ArtifactRuleBuilder> customizer) {

			ArtifactRuleBuilder builder = DependencyRules.artifact(pattern);
			customizer.accept(builder);
			this.artifacts.add(builder.build());
			return this;
		}

		public Builder branch(String pattern, Consumer<BranchRuleBuilder> customizer) {

			BranchRuleBuilder builder = DependencyRules.branch(pattern);
			customizer.accept(builder);
			this.branches.add(builder.build());
			return this;
		}

		/**
		 * Control whether strategy limits are inferred from the project version.
		 */
		public Builder semVerUpdating(SemVerUpdating semVerUpdating) {
			this.semVerUpdating = semVerUpdating;
			return this;
		}

		public DependencyRules build() {
			return new DependencyRules(List.copyOf(this.artifacts), List.copyOf(this.branches), this.semVerUpdating);
		}

	}

	public static class BranchRuleBuilder {

		private final String pattern;

		private final List<ArtifactRule> artifacts = new ArrayList<>();

		private Set<UpgradeStrategy> upgradeStrategies = Set.of();

		private BranchRuleBuilder(String pattern) {
			this.pattern = pattern;
		}

		public BranchRuleBuilder artifact(String pattern, String... generations) {
			this.artifacts.add(ArtifactRule.of(pattern, Generations.from(generations)));
			return this;
		}

		public BranchRuleBuilder artifact(String pattern, Consumer<ArtifactRuleBuilder> customizer) {

			ArtifactRuleBuilder builder = DependencyRules.artifact(pattern);
			customizer.accept(builder);
			this.artifacts.add(builder.build());
			return this;
		}

		/**
		 * Limit the branch to these strategies. Empty means no explicit limits.
		 */
		public BranchRuleBuilder upgrades(UpgradeStrategy... upgradeStrategies) {
			EnumSet<UpgradeStrategy> strategies = EnumSet.noneOf(UpgradeStrategy.class);
			strategies.addAll(Arrays.asList(upgradeStrategies));
			this.upgradeStrategies = strategies;
			return this;
		}

		public BranchRule build() {
			return BranchRule.of(this.pattern, List.copyOf(this.artifacts),
					this.upgradeStrategies.isEmpty() ? Set.of() : EnumSet.copyOf(this.upgradeStrategies));
		}

	}

	public static class ArtifactRuleBuilder {

		private final String pattern;

		private String name = "";

		private Generations generations = Generations.unconstrained();

		private ArtifactRuleBuilder(String pattern) {
			this.pattern = pattern;
		}

		public ArtifactRuleBuilder name(String name) {
			this.name = name;
			return this;
		}

		public ArtifactRuleBuilder generation(String... generations) {
			return generation(Generations.from(generations));
		}

		public ArtifactRuleBuilder generation(Generations generations) {
			this.generations = generations;
			return this;
		}

		public ArtifactRule build() {
			return ArtifactRule.of(this.pattern, this.name, this.generations);
		}

	}

}
