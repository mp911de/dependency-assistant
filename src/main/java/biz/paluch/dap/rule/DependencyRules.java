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
import biz.paluch.dap.artifact.UpgradeStrategy;
import org.jspecify.annotations.Nullable;

/**
 * Aggregate of artifact dependency rules and branch rules.
 *
 * <p>Resolution selects a branch rule first by branch name, then by project
 * version; without a match, the top-level artifact rules apply with
 * upgrade-strategy limits derived from the project version. Within a branch,
 * the most specific artifact pattern wins.
 *
 * <p>Branch rules inherit the top-level artifact rules: an artifact without a
 * matching branch-level artifact rule falls back to the top-level rules, still
 * subject to the branch's upgrade-strategy limits.
 *
 * @author Mark Paluch
 * @see BranchRule
 * @see ArtifactRule
 * @see RuleService
 */
public class DependencyRules implements Rules {

	private final List<ArtifactRule> artifacts;

	private final List<BranchRule> branches;

	private DependencyRules(Collection<ArtifactRule> artifacts, Collection<BranchRule> branches) {
		this.artifacts = List.copyOf(artifacts);
		this.branches = branches.stream().map(branch -> branch.withDefaults(this.artifacts)).toList();
	}

	/**
	 * Create a builder for {@code DependencyRules}.
	 * @return a new {@code DependencyRules} builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a branch-rule builder.
	 * @param pattern the branch or project-version pattern.
	 * @return a new branch-rule builder.
	 */
	public static BranchRuleBuilder branch(String pattern) {
		return new BranchRuleBuilder(pattern);
	}

	/**
	 * Create an artifact-rule builder.
	 * @param pattern the artifact pattern.
	 * @return a new artifact-rule builder.
	 */
	public static ArtifactRuleBuilder artifact(String pattern) {
		return new ArtifactRuleBuilder(pattern);
	}

	/**
	 * Create {@code DependencyRules} from artifact dependency rules.
	 *
	 * @param artifacts the artifact dependency rules.
	 * @return the dependency rules.
	 */
	public static DependencyRules of(Collection<ArtifactRule> artifacts) {
		return new DependencyRules(artifacts, List.of());
	}

	/**
	 * Create {@code DependencyRules} from artifact dependency rules and branch rules.
	 *
	 * @param artifacts the artifact dependency rules.
	 * @param branches the branch rules.
	 * @return the dependency rules.
	 */
	public static DependencyRules of(Collection<ArtifactRule> artifacts, Collection<BranchRule> branches) {
		return new DependencyRules(artifacts, branches);
	}

	/**
	 * Resolve a dependency rule for the given artifact.
	 * @param artifactId the artifact id.
	 * @param branchName the active branch name.
	 * @param projectVersion the project version.
	 * @return the resolved dependency rule, or an absent rule.
	 */
	@Override
	public DependencyRule resolve(ArtifactId artifactId, @Nullable String branchName,
			@Nullable ArtifactVersion projectVersion) {

		BranchRule branchRule = resolveBranchRule(branchName, projectVersion);
		return branchRule.select(artifactId);
	}

	/**
	 * Resolve the active branch rule, matching the branch name before the
	 * project version.
	 *
	 * <p>Without a matching branch rule, a semantic fallback rule is created
	 * whose upgrade-strategy limits derive from the project version: service
	 * releases (patch segment other than zero) permit only patch and release
	 * upgrades.
	 *
	 * @param branchName the active branch name; can be {@literal null}.
	 * @param projectVersion the project version; can be {@literal null}.
	 * @return the resolved branch rule, or an absent branch rule; never
	 * {@literal null}.
	 */
	public BranchRule resolveBranchRule(@Nullable String branchName, @Nullable ArtifactVersion projectVersion) {

		BranchRule branchRule = selectBranchRule(branchName);
		if (branchRule != null) {
			return branchRule;
		}
		branchRule = selectBranchRule(projectVersion);
		if (branchRule != null) {
			return branchRule;
		}
		if (projectVersion != null) {
			return BranchRule.semanticFallback(this.artifacts, upgradeStrategies(projectVersion));
		}
		return BranchRule.of(this.artifacts, upgradeStrategies(projectVersion));
	}

	private @Nullable BranchRule selectBranchRule(@Nullable String value) {

		if (value == null) {
			return null;
		}
		return branches.stream().filter(it -> it.test(value)).max(BranchRule::compareTo).orElse(null);
	}

	private @Nullable BranchRule selectBranchRule(@Nullable ArtifactVersion version) {

		if (version == null) {
			return null;
		}

		ArtifactVersion candidate = version;
		while (candidate.isWrapped()) {
			candidate = candidate.getVersion();
		}
		String displayVersion = version.toString();
		String innerMostVersion = candidate.toString();
		return branches.stream()
				.filter(it -> it.test(displayVersion) || it.test(innerMostVersion))
				.max(BranchRule::compareTo)
				.orElse(null);
	}

	@Override
	public String toString() {
		return "DependencyRules{" +
		       "artifacts=" + artifacts +
		       ", branches=" + branches +
		       '}';
	}

	private static Set<UpgradeStrategy> upgradeStrategies(@Nullable ArtifactVersion projectVersion) {

		if (projectVersion == null) {
			return Set.of();
		}
		ArtifactVersion candidate = projectVersion;
		while (candidate.isWrapped()) {
			candidate = candidate.getVersion();
		}
		if (!(candidate instanceof NumericVersion numericVersion) || numericVersion.size() != 3) {
			return Set.of();
		}
		int[] parts = numericVersion.getParts();
		if (parts.length > 2 && parts[2] != 0) {
			return EnumSet.of(UpgradeStrategy.PATCH, UpgradeStrategy.RELEASE);
		}
		return EnumSet.of(UpgradeStrategy.PATCH, UpgradeStrategy.RELEASE, UpgradeStrategy.PREVIEW,
				UpgradeStrategy.MINOR);
	}

	/**
	 * Builder for {@link DependencyRules}.
	 */
	public static class Builder {

		private final List<ArtifactRule> artifacts = new ArrayList<>();

		private final List<BranchRule> branches = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Add an artifact dependency rule.
		 * @param pattern the artifact pattern.
		 * @param generation the generation source.
		 * @return this builder.
		 */
		public Builder artifact(String pattern, String generation) {
			this.artifacts.add(ArtifactRule.of(pattern, generation));
			return this;
		}

		/**
		 * Add a configured artifact dependency rule.
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
		 * Build the {@code DependencyRules}.
		 * @return the dependency rules.
		 */
		public DependencyRules build() {
			return new DependencyRules(this.artifacts, this.branches);
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
		 * @param pattern the artifact pattern.
		 * @param generation the generation source.
		 * @return this builder.
		 */
		public BranchRuleBuilder artifact(String pattern, String generation) {
			this.artifacts.add(ArtifactRule.of(pattern, generation));
			return this;
		}

		/**
		 * Add a configured artifact rule to this branch rule.
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
		 * @return the branch rule.
		 */
		public BranchRule build() {
			return BranchRule.of(this.pattern, this.artifacts, this.upgradeStrategies);
		}

	}

	/**
	 * Builder for {@link ArtifactRule}.
	 */
	public static class ArtifactRuleBuilder {

		private final String pattern;

		private String name;

		private @Nullable Generation generation;

		private ArtifactRuleBuilder(String pattern) {
			this.pattern = pattern;
			this.name = pattern;
		}

		/**
		 * Set the friendly display name.
		 * @param name the friendly display name.
		 * @return this builder.
		 */
		public ArtifactRuleBuilder name(String name) {
			this.name = name;
			return this;
		}

		/**
		 * Set the generation.
		 * @param generation the generation source.
		 * @return this builder.
		 */
		public ArtifactRuleBuilder generation(String generation) {
			this.generation = Generation.of(generation);
			return this;
		}

		/**
		 * Build an artifact rule.
		 * @return the artifact rule.
		 */
		public ArtifactRule build() {
			return ArtifactRule.of(this.pattern, this.name, this.generation == null ? "*" : this.generation.toString());
		}

	}

}
