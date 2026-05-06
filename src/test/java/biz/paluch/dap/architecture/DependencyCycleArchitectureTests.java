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

import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.assistant.DependencyDocumentationProvider;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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

	private static final CycleExclusions EXCLUSIONS = CycleExclusions.none()
			.excludingClass(DependencyDocumentationProvider.class,
					"TODO: refine design")
			.excludingClass("biz.paluch.dap.maven.MavenParser",
					"Fix todo's")
			.excludingClass("biz.paluch.dap.gradle.ExtraDeclaration",
					"Fix todo's")
			.excludingClass("biz.paluch.dap.gradle.GroovyDslUtils",
					"Fix todo's")
			.excludingClass("biz.paluch.dap.npm.NpmGitRef",
					"Fix todo's")
			.excludingClass("biz.paluch.dap.npm.NpmVersionExpression",
					"Fix todo's")
			.excludingClass("biz.paluch.dap.gradle.KotlinDslSettingsParser",
					"Fix todo's")
			.excludingClass("biz.paluch.dap.gradle.VersionCatalogRegistry",
					"Fix todo's")
			.excludingClass("biz.paluch.dap.support.PropertyResolverUtil",
					"Fix todo's")
			.excludingClass(UpgradeStrategy.class, "wtf?");

	@ArchTest
	static final ArchRule packagesShouldBeFreeOfCycles = slices().assignedFrom(SliceRules.allPackages())
			.should()
			.beFreeOfCycles()
			.as("Packages should be free of cyclic dependencies");

	@ArchTest
	static final ArchRule classesShouldBeFreeOfCycles = slices()
			.assignedFrom(EXCLUSIONS.apply(SliceRules.classesAndClosedHierarchies()))
			.should()
			.beFreeOfCycles()
			.as("Classes should be free of cyclic dependencies");

	@ArchTest
	static final ArchRule root = packageDependencies("biz.paluch.dap",
			"artifact", "state", "support");

	@ArchTest
	static final ArchRule artifact = packageDependencies(
			"artifact", "util", "xml");

	@ArchTest
	static final ArchRule assistantPackage = packageDependencies(
			"assistant", "biz.paluch.dap", "artifact", "severity", "state", "support", "util");

	@ArchTest
	static final ArchRule github = packageDependencies("github",
			"biz.paluch.dap", "artifact", "assistant", "state", "support", "util");

	@ArchTest
	static final ArchRule gradle = packageDependencies("gradle",
			"biz.paluch.dap", "artifact", "assistant", "state", "support", "util");

	@ArchTest
	static final ArchRule maven = packageDependencies("maven",
			"biz.paluch.dap",
			"artifact", "assistant", "state", "support", "util");

	@ArchTest
	static final ArchRule npm = packageDependencies("npm", "biz.paluch.dap",
			"artifact", "assistant", "state", "support", "util", "github");

	@ArchTest
	static final ArchRule severity = packageDependencies("severity",
			"biz.paluch.dap", "support");

	@ArchTest
	static final ArchRule state = packageDependencies("state", "artifact",
			"util");

	@ArchTest
	static final ArchRule support = packageDependencies("support", "artifact",
			"state", "util");

	@ArchTest
	static final ArchRule util = packageDependencies("util");

	@ArchTest
	static final ArchRule xml = packageDependencies("xml");

	private static ArchRule packageDependencies(String packageUnderTest, String... allowedPackages) {

		return classes().that()
				.resideInAPackage(ArchRules.expandPackage(packageUnderTest))
				.should()
				.onlyDependOnClassesThat(ArchRules.residesInAnyPackage(packageUnderTest, allowedPackages))
				.as(String.format("Package '%s' should only depend on classes in %s", packageUnderTest,
						Arrays.asList(allowedPackages)));
	}

}
