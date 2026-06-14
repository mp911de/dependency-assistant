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
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.Properties;
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
class DependencyCycleArchitectureTests {

	private CycleExclusions EXCLUSIONS = CycleExclusions.none()
			.excludingClass("biz.paluch.dap.gradle.VersionCatalogRegistry",
					"Gradle catalog caching still depends on settings parsers")
			.excludingClass("biz.paluch.dap.support.PropertyResolverUtil",
					"Property interpolation helper is shared by resolver implementations");

	/*
	 * Variant 1: explicit closed hierarchy declaration. DeclarationSource, its
	 * nested marker types, nested implementations, and imported subtypes become one
	 * class slice.
	 */
	private SliceAssignment CLASSES_AND_HIERARCHIES = SliceRules.classes(
			it -> {
				it.withClosedHierarchy(ArtifactVersion.class)
						.withStrictClosedHierarchy(PropertyResolver.class)
						.withStrictClosedHierarchy(ProjectDependencyContext.class)
						.withClosedHierarchy(DependencySite.class)
						.withClosedHierarchy(ReleaseSource.class)
						.withStrictClosedHierarchy(ArtifactId.class)
						.withStrictClosedHierarchy(DependencyRuleService.class)
						.withClosedHierarchy(Properties.class)
						.withStrictClosedHierarchy("biz.paluch.dap.npm.NpmVersionExpression")
						.withStrictClosedHierarchy("biz.paluch.dap.gradle.GradleVersionSite")
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
			"artifact", "util", "xml");

	@ArchTest
	ArchRule assistantPackage = packageDependencies(
			"assistant", "biz.paluch.dap", "artifact", "rule", "severity", "state", "lookup", "support", "util");

	@ArchTest
	ArchRule antora = packageDependencies("antora",
			"biz.paluch.dap", "artifact", "assistant", "state", "lookup", "support", "support.yaml", "util", "github");

	@ArchTest
	ArchRule github = packageDependencies("github",
			"biz.paluch.dap", "artifact", "assistant", "state", "lookup", "support", "support.yaml", "util");

	@ArchTest
	ArchRule gradle = packageDependencies("gradle",
			"biz.paluch.dap", "artifact", "assistant", "state", "lookup", "support", "util");

	@ArchTest
	ArchRule maven = packageDependencies("maven",
			"biz.paluch.dap",
			"artifact", "assistant", "state", "lookup", "support", "util", "maven.wrapper");

	@ArchTest
	ArchRule mavenWrapper = packageDependencies("maven.wrapper",
			"biz.paluch.dap",
			"artifact", "assistant", "state", "lookup", "support", "util");

	@ArchTest
	ArchRule npm = packageDependencies("npm", "biz.paluch.dap",
			"artifact", "assistant", "state", "lookup", "support", "util", "github");

	@ArchTest
	ArchRule severity = packageDependencies("severity",
			"biz.paluch.dap", "support");

	@ArchTest
	ArchRule state = packageDependencies("state", "artifact",
			"util");

	@ArchTest
	ArchRule support = packageDependencies("support", "artifact",
			"state", "util");

	@ArchTest
	ArchRule util = packageDependencies("util");

	@ArchTest
	ArchRule xml = packageDependencies("xml");

	private static ArchRule packageDependencies(String packageUnderTest, String... allowedPackages) {

		return classes().that()
				.resideInAPackage(ArchRules.expandPackage(packageUnderTest))
				.should()
				.onlyDependOnClassesThat(ArchRules.residesInAnyPackage(packageUnderTest, allowedPackages))
				.as("Package '%s' should only depend on classes in %s".formatted(packageUnderTest,
						Arrays.asList(allowedPackages)));
	}

}
