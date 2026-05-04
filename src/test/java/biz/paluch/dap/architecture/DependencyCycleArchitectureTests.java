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

import java.util.function.Function;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.DependencyDocumentationProvider;
import biz.paluch.dap.github.GitHubWorkflowCompletionContributor;
import biz.paluch.dap.maven.XmlReleaseVersionCompletionContributor;
import biz.paluch.dap.npm.NpmVersionCompletionContributor;
import biz.paluch.dap.severity.DependencyAssistantSeverities;
import biz.paluch.dap.support.PropertyExpression;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.*;

/**
 * Architecture tests for cyclic dependencies within the production code base.
 * <p>The package rule verifies that the root package and its top-level
 * subpackages do not form cycles. The class rule applies the same verification
 * at individual class level.
 *
 * @author Mark Paluch
 */
@AnalyzeClasses(packages = "biz.paluch.dap", importOptions = {DoNotIncludeTests.class,
		ExcludeInstrumentedTestCode.class})
class DependencyCycleArchitectureTests {

	private static final String ROOT_PACKAGE = "biz.paluch.dap";

	private static final String ROOT_PACKAGE_PREFIX = ROOT_PACKAGE + ".";

	// TODO: Allow self-encapsulated and sealed types to refer to themselves
	private static final CycleExclusions EXCLUSIONS = CycleExclusions.none()
			.excludingClass(ArtifactId.class, "inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.artifact.Suffix", "inner and outer class design that depends on each other")
			.excludingClass(ArtifactVersion.class, "inner and outer class design that depends on each other")
			.excludingClass(DeclarationSource.class, "inner and outer class design that depends on each other")
			.excludingClass(VersionSource.class, "inner and outer class design that depends on each other")
			.excludingClass(ReleaseSource.class, "inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.gradle.GradlePlugin",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.gradle.KotlinExtraAssignment",
					"inner and outer class design that depends on each other")
			.excludingClass(NpmVersionCompletionContributor.class,
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.gradle.GradleRichVersion",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.gradle.GradleDependency",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.gradle.GroovyExtAssignment",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.maven.MavenProjectContext",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.xml.XmlBeamProjectorFactory",
					"inner and outer class design that depends on each other")
			.excludingClass(DependencyAssistantSeverities.class,
					"inner and outer class design that depends on each other")
			.excludingClass(PropertyExpression.class,
					"inner and outer class design that depends on each other")
			.excludingClass(GitHubWorkflowCompletionContributor.class,
					"inner and outer class design that depends on each other")
			.excludingClass(XmlReleaseVersionCompletionContributor.class,
					"inner and outer class design that depends on each other")
			.excludingClass(NpmVersionCompletionContributor.class,
					"inner and outer class design that depends on each other")
			.excludingClass(biz.paluch.dap.assistant.PostStartup.class,
					"inner and outer class design that depends on each other")
			.excludingClass(DependencyDocumentationProvider.class,
					"TODO: refine design")
			.excludingClass("biz.paluch.dap.maven.MavenParser",
					"Fix todo's")
			.excludingClass("biz.paluch.dap.gradle.KotlinPluginIds",
					"Fix todo's")
			.excludingClass("biz.paluch.dap.gradle.LookupSite",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.npm.NpmGitRef",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.npm.NpmVersionExpression",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.support.ArtifactDeclaration",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.support.DependencySite",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.state.StateService",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.github.GitHubAction",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.gradle.GradlePropertyResolver",
					"inner and outer class design that depends on each other")
			.excludingClass("biz.paluch.dap.support.PropertyResolverUtil",
					"inner and outer class design that depends on each other")
			.excludingClass(UpgradeStrategy.class, "wtf?");

	private static final SliceAssignment TOP_LEVEL_PACKAGES = new SimpleSliceAssignment(
			DependencyCycleArchitectureTests::topLevelPackageIdentifier);

	private static final SliceAssignment INDIVIDUAL_CLASSES = new DependencyCycleSliceAssignment(
			DependencyCycleArchitectureTests::classIdentifier);

	@ArchTest
	static final ArchRule topLevelPackagesShouldBeFreeOfCycles = slices().assignedFrom(TOP_LEVEL_PACKAGES)
			.should()
			.beFreeOfCycles()
			.as("top-level packages should be free of cyclic dependencies");

	// @ArchTest
	// TODO: Formulate general rule that allows inner classes cycles and between an
	// interface and its subtypes (e.g. default method creating a concrete type).
	static final ArchRule classesShouldBeFreeOfCycles = slices().assignedFrom(INDIVIDUAL_CLASSES)
			.should()
			.beFreeOfCycles()
			.as("production classes should be free of cyclic dependencies");

	private static SliceIdentifier topLevelPackageIdentifier(JavaClass javaClass) {
		String packageName = javaClass.getPackageName();

		if (packageName.equals(ROOT_PACKAGE)) {
			return SliceIdentifier.of(ROOT_PACKAGE);
		}

		if (!packageName.startsWith(ROOT_PACKAGE_PREFIX)) {
			return SliceIdentifier.ignore();
		}

		int nextDot = packageName.indexOf('.', ROOT_PACKAGE_PREFIX.length());
		String sliceName = nextDot == -1 ? packageName : packageName.substring(0, nextDot);

		return SliceIdentifier.of(sliceName);
	}

	private static SliceIdentifier classIdentifier(JavaClass javaClass) {
		String packageName = javaClass.getPackageName();

		if (!packageName.equals(ROOT_PACKAGE) && !packageName.startsWith(ROOT_PACKAGE_PREFIX)) {
			return SliceIdentifier.ignore();
		}

		return SliceIdentifier.of(javaClass.getName());
	}

	private record DependencyCycleSliceAssignment(
			Function<JavaClass, SliceIdentifier> identifierFunction) implements SliceAssignment {

		@Override
		public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
			return EXCLUSIONS.isExcluded(javaClass) ? SliceIdentifier.ignore()
					: this.identifierFunction.apply(javaClass);
		}

		@Override
		public String getDescription() {
			return "Slice";
		}

	}

	private record SimpleSliceAssignment(
			Function<JavaClass, SliceIdentifier> identifierFunction) implements SliceAssignment {

		@Override
		public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
			return this.identifierFunction.apply(javaClass);
		}

		@Override
		public String getDescription() {
			return "Slice";
		}

	}

}

