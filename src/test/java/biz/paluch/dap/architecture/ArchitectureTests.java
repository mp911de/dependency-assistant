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

package biz.paluch.dap.architecture;

import java.util.Arrays;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.Properties;
import biz.paluch.dap.util.Sequence;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.*;

/**
 * Architecture tests for cyclic dependencies within the production code base.
 *
 * @author Mark Paluch
 */
@AnalyzeClasses(packages = "biz.paluch.dap", importOptions = {DoNotIncludeTests.class,
		ExcludeInstrumentedTestCode.class})
class ArchitectureTests {

	private CycleExclusions EXCLUSIONS = CycleExclusions.none()
			.excludingClass("biz.paluch.dap.gradle.VersionCatalogRegistry",
					"Gradle catalog caching still depends on settings parsers")
			.excludingClass("biz.paluch.dap.support.PropertyResolverUtil",
					"Property interpolation helper is shared by resolver implementations")
			.excludingClass("biz.paluch.dap.github.GitHubTicketQuery",
					"needs rework");

	/*
	 * Variant 1: explicit closed hierarchy declaration. DeclarationSource, its
	 * nested marker types, nested implementations, and imported subtypes become one
	 * class slice.
	 */
	private SliceAssignment CLASSES_AND_HIERARCHIES = SliceRules.classes(
			it -> {
				it.withClosedHierarchy(ArtifactVersion.class)
						.withClosedHierarchy(BillOfMaterials.class)
						.withStrictClosedHierarchy(PropertyResolver.class)
						.withStrictClosedHierarchy(ProjectDependencyContext.class)
						.withStrictClosedHierarchy(VulnerabilityRepository.class)
						.withClosedHierarchy(DependencySite.class)
						.withClosedHierarchy(DependencyRuleService.class)
						.withClosedHierarchy(ReleaseSource.class)
						.withStrictClosedHierarchy(ArtifactId.class)
						.withClosedHierarchy(Properties.class)
						.withStrictClosedHierarchy(Sequence.class)
						.withStrictClosedHierarchy("biz.paluch.dap.util.StepsProgressIndicator")
						.withStrictClosedHierarchy("biz.paluch.dap.npm.NpmVersionExpression")
						.withStrictClosedHierarchy("biz.paluch.dap.gradle.GradlePluginId")
						.withStrictClosedHierarchy("biz.paluch.dap.gradle.GradleArtifactId");
			});

	@ArchTest
	ArchRule packagesShouldBeFreeOfCycles = slices().assignedFrom(SliceRules.allPackages())
			.should()
			.beFreeOfCycles()
			.as("Packages should be free of cyclic dependencies");

	@ArchTest
	ArchRule classesShouldBeFreeOfCycles = slices()
			.assignedFrom(EXCLUSIONS.apply(CLASSES_AND_HIERARCHIES))
			.should()
			.beFreeOfCycles()
			.as("Classes should be free of cyclic dependencies");

	@ArchTest
	ArchRule root = packageDependencies("biz.paluch.dap",
			"artifact", "state", "lookup", "support");

	@ArchTest
	ArchRule artifact = packageDependencies(
			"artifact", "util");

	@ArchTest
	ArchRule assistant = packageDependencies(
			"assistant", "biz.paluch.dap", "artifact", "checker", "lookup", "rule", "state", "support", "util");

	@ArchTest
	ArchRule assistantAction = packageDependencies("assistant.action",
			"biz.paluch.dap", "artifact", "assistant.check", "assistant.review", "plan", "rule", "state", "support",
			"upgrade", "util");

	@ArchTest
	ArchRule assistantCheck = packageDependencies("assistant.check",
			"biz.paluch.dap", "artifact", "assistant", "checker", "lookup", "rule", "state", "support", "upgrade",
			"util");

	@ArchTest
	ArchRule assistantCompletion = packageDependencies("assistant.completion",
			"biz.paluch.dap", "artifact", "assistant", "checker", "lookup", "rule", "state", "support", "util");

	@ArchTest
	ArchRule assistantDocumentation = packageDependencies("assistant.documentation",
			"biz.paluch.dap", "artifact", "assistant", "assistant.action", "checker",
			"lookup", "rule", "state", "support", "util");

	@ArchTest
	ArchRule assistantEditor = packageDependencies("assistant.editor",
			"biz.paluch.dap", "artifact", "assistant", "assistant.action", "checker", "rule", "severity", "state",
			"support", "upgrade", "util");

	@ArchTest
	ArchRule assistantReview = packageDependencies("assistant.review",
			"biz.paluch.dap", "artifact", "assistant", "assistant.check", "checker", "lookup", "plan", "rule",
			"support", "upgrade", "util");

	@ArchTest
	ArchRule antora = packageDependencies("antora",
			"biz.paluch.dap", "artifact", "assistant.completion", "assistant.editor", "state",
			"lookup", "support", "support.yaml", "util", "github");

	@ArchTest
	ArchRule checker = packageDependencies("checker", "artifact", "util");

	@ArchTest
	ArchRule github = packageDependencies("github",
			"biz.paluch.dap", "artifact", "assistant", "assistant.completion", "assistant.editor", "state",
			"lookup", "support", "support.yaml", "ticket", "util");

	@ArchTest
	ArchRule gradle = packageDependencies("gradle",
			"biz.paluch.dap", "artifact", "assistant.completion", "assistant.editor", "maven", "state",
			"lookup", "support", "util");

	@ArchTest
	ArchRule gradleWrapper = packageDependencies("gradle.wrapper",
			"biz.paluch.dap", "artifact", "assistant.completion", "assistant.editor", "gradle", "state",
			"lookup", "support", "util");

	@ArchTest
	ArchRule maven = packageDependencies("maven",
			"biz.paluch.dap",
			"artifact", "assistant.completion", "assistant.editor", "state",
			"lookup", "support", "util", "maven.wrapper");

	@ArchTest
	ArchRule mavenWrapper = packageDependencies("maven.wrapper",
			"biz.paluch.dap",
			"artifact", "assistant.action", "assistant.completion", "state", "lookup", "support",
			"util");

	@ArchTest
	ArchRule npm = packageDependencies("npm", "biz.paluch.dap",
			"artifact", "assistant.completion", "assistant.documentation", "assistant.editor", "state",
			"lookup", "support", "util", "github");

	@ArchTest
	ArchRule lookup = packageDependencies("lookup", "artifact", "state", "support", "util");

	@ArchTest
	ArchRule plan = packageDependencies("plan",
			"biz.paluch.dap", "artifact", "assistant", "assistant.check", "checker", "lookup", "rule", "state",
			"support", "ticket", "upgrade", "util");

	@ArchTest
	ArchRule rule = packageDependencies("rule",
			"biz.paluch.dap", "artifact", "state", "support", "util");

	@ArchTest
	ArchRule severity = packageDependencies("severity",
			"biz.paluch.dap", "util");

	@ArchTest
	ArchRule state = packageDependencies("state", "artifact", "checker", "util");

	@ArchTest
	ArchRule upgrade = packageDependencies(
			"upgrade", "artifact", "checker", "rule", "state", "support", "util");

	@ArchTest
	ArchRule support = packageDependencies("support", "artifact",
			"state", "util");

	@ArchTest
	ArchRule ticket = packageDependencies("ticket");

	@ArchTest
	ArchRule util = packageDependencies("util");

	private static ArchRule packageDependencies(String packageUnderTest, String... allowedPackages) {

		return classes().that()
				.resideInAPackage(ArchRules.expandPackage(packageUnderTest))
				.should()
				.onlyDependOnClassesThat(ArchRules.residesInAnyPackage(packageUnderTest, allowedPackages))
				.as("Package '%s' should only depend on classes in %s".formatted(packageUnderTest,
						Arrays.asList(allowedPackages)));
	}

}
