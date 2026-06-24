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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.mock.MockPsiElement;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyRuleEvaluator}.
 *
 * @author Mark Paluch
 */
class DependencyRuleEvaluatorUnitTests {

	private static final ArtifactId COMPILER_PLUGIN = ArtifactId.of("org.apache.maven.plugins",
			"maven-compiler-plugin");

	private static final Versioned SERVICE_RELEASE = Versioned.of(ArtifactVersion.of("2.1.1"));

	@Test
	void pluginArtifactReferenceSuppressesSemanticUpgrading() {

		DependencyRuleEvaluator evaluation = evaluate(reference(DeclarationSource.plugin()));

		assertThat(evaluation.isPresent()).isTrue();
		assertThat(evaluation.isSemanticUpgradingEnabled()).isFalse();
	}

	@Test
	void dependencyArtifactReferenceKeepsSemanticUpgrading() {

		DependencyRuleEvaluator evaluation = evaluate(reference(DeclarationSource.dependency()));

		assertThat(evaluation.isSemanticUpgradingEnabled()).isTrue();
	}

	@Test
	void aggregatePluginDeclarationSuppressesSemanticUpgrading() {

		DeclaredDependency dependency = new DeclaredDependency(COMPILER_PLUGIN);
		dependency.addDeclarationSource(DeclarationSource.plugin());

		DependencyRuleEvaluator evaluation = evaluate(
				ResolutionContext.of(dependency, BranchSource.of((VirtualFile) null),
						SERVICE_RELEASE));

		assertThat(evaluation.isSemanticUpgradingEnabled()).isFalse();
	}

	@Test
	void mixedAggregateDeclarationKeepsSemanticUpgrading() {

		DeclaredDependency dependency = new DeclaredDependency(COMPILER_PLUGIN);
		dependency.addDeclarationSource(DeclarationSource.plugin());
		dependency.addDeclarationSource(DeclarationSource.dependency());

		DependencyRuleEvaluator evaluation = evaluate(
				ResolutionContext.of(dependency, BranchSource.of((VirtualFile) null),
						SERVICE_RELEASE));

		assertThat(evaluation.isSemanticUpgradingEnabled()).isTrue();
	}

	private static DependencyRuleEvaluator evaluate(ArtifactReference reference) {
		return evaluate(ResolutionContext.of(reference, BranchSource.of((VirtualFile) null), SERVICE_RELEASE));
	}

	private static DependencyRuleEvaluator evaluate(ResolutionContext context) {
		return DependencyRuleEvaluator.evaluate(governing(), context, ArtifactVersion.of("4.0.0"),
				new TestInterfaceAssistant());
	}

	private static ArtifactReference reference(DeclarationSource source) {
		return ArtifactReference.from(it -> it.artifact(COMPILER_PLUGIN)
				.versionSource(VersionSource.declared("3.14.0"))
				.declarationSource(source)
				.version(ArtifactVersion.of("3.14.0"))
				.declarationElement(new MockPsiElement(() -> {
				})));
	}

	private static DependencyRuleService governing() {

		return new DependencyRuleService() {

			@Override
			public DependencyRule resolve(ResolutionContext context) {
				return new SemanticRule(!context.suppressSemanticUpgrading());
			}

		};
	}

	private record SemanticRule(boolean semanticUpgrading) implements DependencyRule {

		@Override
		public boolean isPresent() {
			return true;
		}

		@Override
		public boolean isSemanticUpgradingEnabled() {
			return semanticUpgrading;
		}

		@Override
		public Generations getGenerations() {
			return Generations.unconstrained();
		}

		@Override
		public String getDependencyName() {
			return "Compiler Plugin";
		}

		@Override
		public boolean isEnabled(UpgradeStrategy upgradeStrategy) {
			return true;
		}

		@Override
		public boolean test(ArtifactVersion version) {
			return true;
		}

		@Override
		public @Nullable Release suggestRemediation(Releases releases) {
			return null;
		}

	}

}
