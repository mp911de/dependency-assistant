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

import java.util.List;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.UpgradeStrategy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyRules}.
 *
 * @author Mark Paluch
 */
class DependencyRulesUnitTests {

	@Test
	void resolvesDefaultDependencyRule() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", artifact -> artifact
						.name("Spring Framework")
						.generation("7.0.x"))
				.build();

		DependencyRule rule = rules.resolve(ArtifactId.of("org.springframework", "spring-core"), "main", null);

		assertThat(rule.getGeneration()).isEqualTo("7.0");
		assertThat(rule).accepts(ArtifactVersion.of("7.0.1"));
	}

	@Test
	void resolvesUngovernedArtifactToAbsentRule() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", "7.0.x")
				.build();

		DependencyRule rule = rules.resolve(ArtifactId.of("org.junit", "junit-bom"), "main", null);

		assertThat(rule).isSameAs(DependencyRule.absent());
		assertThat(rule.getGeneration()).isEmpty();
		assertThat(rule).accepts(ArtifactVersion.of("1.0.0"), ArtifactVersion.of("999.0.0"));
		Predicate<UpgradeStrategy> isEnabled = rule::isEnabled;
		assertThat(isEnabled).accepts(UpgradeStrategy.PATCH, UpgradeStrategy.MINOR, UpgradeStrategy.MAJOR,
				UpgradeStrategy.RELEASE, UpgradeStrategy.PREVIEW, UpgradeStrategy.LATEST);
	}

	@Test
	void resolvesMostSpecificArtifactRule() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("*", "1")
				.artifact("org.springframework:*", "6.0")
				.artifact("spring-*", "6.1")
				.artifact("org.springframework:spring-core", "6.2")
				.build();

		DependencyRule rule = rules.resolve(ArtifactId.of("org.springframework", "spring-core"), "main", null);

		assertThat(rule.getGeneration()).isEqualTo("6.2");
	}

	@Test
	void resolvesActiveBranchRuleWithoutMergingDefaultRules() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", "7.0")
				.branch("3.5.x", branch -> branch.artifact("org.springframework:*", "6.0"))
				.build();

		DependencyRule branchRule = rules.resolve(ArtifactId.of("org.springframework", "spring-core"), "3.5.x", null);
		DependencyRule defaultRule = rules.resolve(ArtifactId.of("org.springframework", "spring-core"), "main", null);

		assertThat(branchRule.getGeneration()).isEqualTo("6.0");
		assertThat(defaultRule.getGeneration()).isEqualTo("7.0");
	}

	@Test
	void fallsBackToProjectVersionBranchRule() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", "7.0")
				.branch("2.*.0", branch -> branch.artifact("org.springframework:*", "5.0"))
				.build();

		DependencyRule rule = rules.resolve(ArtifactId.of("org.springframework", "spring-core"), null,
				ArtifactVersion.of("2.1.0"));

		assertThat(rule.getGeneration()).isEqualTo("5.0");
	}

	@Test
	void resolvesMostSpecificBranchRule() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", "7.0")
				.branch("3.*.x", branch -> branch.artifact("org.springframework:*", "6.0"))
				.branch("3.5.x", branch -> branch.artifact("org.springframework:*", "6.1"))
				.build();

		DependencyRule rule = rules.resolve(ArtifactId.of("org.springframework", "spring-core"), "3.5.x", null);

		assertThat(rule.getGeneration()).isEqualTo("6.1");
	}

	@Test
	void exposesBranchUpgradeStrategies() {

		BranchRule rule = DependencyRules.branch("2.*.x")
				.artifact("org.springframework:*", "5.0")
				.upgrades(UpgradeStrategy.PATCH, UpgradeStrategy.MINOR)
				.build();
		Predicate<UpgradeStrategy> supports = rule::supports;

		assertThat(rule.upgradeStrategies()).containsExactlyInAnyOrder(UpgradeStrategy.PATCH, UpgradeStrategy.MINOR);
		assertThat(supports).accepts(UpgradeStrategy.PATCH, UpgradeStrategy.MINOR).rejects(UpgradeStrategy.MAJOR);
	}

	@Test
	void treatsOmittedBranchUpgradeStrategiesAsUnrestricted() {

		BranchRule rule = DependencyRules.branch("2.*.x")
				.artifact("org.springframework:*", "5.0")
				.build();
		Predicate<UpgradeStrategy> supports = rule::supports;

		assertThat(supports).accepts(UpgradeStrategy.PATCH, UpgradeStrategy.MINOR, UpgradeStrategy.MAJOR);
	}

	@Test
	void configuresArtifactRuleFunctionally() {

		ArtifactRule rule = DependencyRules.artifact("org.springframework:*")
				.name("Spring Framework")
				.generation("7.0.x")
				.build();

		assertThat(rule.name()).isEqualTo("Spring Framework");
		assertThat(rule.generation().value()).isEqualTo("7.0");
	}

	@Test
	void absentBranchRuleMatchesEveryBranch() {

		BranchRule rule = BranchRule.absent(List.of(ArtifactRule.of("org.springframework:*", "7.0")));

		assertThat((Predicate<String>) rule).accepts("main", "3.5.x", "v2.1.0");
	}

	@Test
	void limitsAbsentBranchRuleToPatchAndReleaseForPatchProjectVersion() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", "7.0")
				.build();
		BranchRule rule = rules.resolveBranchRule(null, ArtifactVersion.of("2.1.1"));
		Predicate<UpgradeStrategy> supports = rule::supports;

		assertThat(supports).accepts(UpgradeStrategy.PATCH, UpgradeStrategy.RELEASE)
				.rejects(UpgradeStrategy.MINOR, UpgradeStrategy.MAJOR, UpgradeStrategy.PREVIEW, UpgradeStrategy.LATEST);
	}

	@Test
	void limitsResolvedDependencyRuleToPatchAndReleaseForPatchProjectVersion() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", "7.0")
				.build();

		DependencyRule rule = rules.resolve(ArtifactId.of("org.springframework", "spring-core"), null,
				ArtifactVersion.of("2.1.1"));
		Predicate<UpgradeStrategy> isEnabled = rule::isEnabled;

		assertThat(isEnabled).accepts(UpgradeStrategy.PATCH, UpgradeStrategy.RELEASE)
				.rejects(UpgradeStrategy.MINOR, UpgradeStrategy.MAJOR, UpgradeStrategy.PREVIEW, UpgradeStrategy.LATEST);
	}

	@Test
	void allowsPreviewAndMinorForBaseProjectVersion() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", "7.0")
				.build();
		BranchRule rule = rules.resolveBranchRule(null, ArtifactVersion.of("2.1.0"));
		Predicate<UpgradeStrategy> supports = rule::supports;

		assertThat(supports).accepts(UpgradeStrategy.PATCH, UpgradeStrategy.RELEASE, UpgradeStrategy.PREVIEW,
				UpgradeStrategy.MINOR).rejects(UpgradeStrategy.MAJOR, UpgradeStrategy.LATEST);
	}

	@Test
	void matchesProjectVersionAgainstDisplayedVersion() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", "7.0")
				.branch("v2.*", branch -> branch.artifact("org.springframework:*", "5.0"))
				.build();

		DependencyRule rule = rules.resolve(ArtifactId.of("org.springframework", "spring-core"), null,
				ArtifactVersion.of("v2.1.0"));

		assertThat(rule.getGeneration()).isEqualTo("5.0");
	}

	@Test
	void matchesProjectVersionAgainstInnerMostVersion() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", "7.0")
				.branch("2.*", branch -> branch.artifact("org.springframework:*", "5.0"))
				.build();

		DependencyRule rule = rules.resolve(ArtifactId.of("org.springframework", "spring-core"), null,
				ArtifactVersion.of("v2.1.0"));

		assertThat(rule.getGeneration()).isEqualTo("5.0");
	}

	@Test
	void choosesMostSpecificProjectVersionRuleAcrossDisplayedAndInnerMostVersions() {

		DependencyRules rules = DependencyRules.builder()
				.artifact("org.springframework:*", "7.0")
				.branch("v2.*", branch -> branch.artifact("org.springframework:*", "5.0"))
				.branch("2.1.0", branch -> branch.artifact("org.springframework:*", "5.1"))
				.build();

		DependencyRule rule = rules.resolve(ArtifactId.of("org.springframework", "spring-core"), null,
				ArtifactVersion.of("v2.1.0"));

		assertThat(rule.getGeneration()).isEqualTo("5.1");
	}

}
