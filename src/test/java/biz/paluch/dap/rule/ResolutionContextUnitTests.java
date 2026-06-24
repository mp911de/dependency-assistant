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
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ResolutionContext}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class ResolutionContextUnitTests {

	private static final ArtifactId COMPILER_PLUGIN = ArtifactId.of("org.apache.maven.plugins",
			"maven-compiler-plugin");

	private static final Versioned SERVICE_RELEASE = Versioned.of(ArtifactVersion.of("2.1.1"));

	@Test
	@ProjectFile(name = "pom.xml", content = "<project/>")
	void getBranchSourceFromElementUsesContainingFile(PsiFile file) {

		assertThat(BranchSource.of(file).getFile()).isSameAs(file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "pom.xml", content = "<project/>")
	void artifactReferenceUsesPerDeclarationPluginSemantics(PsiFile file) {

		ResolutionContext plugin = ResolutionContext.of(reference(file, DeclarationSource.plugin()),
				BranchSource.of(file), SERVICE_RELEASE);
		ResolutionContext dependency = ResolutionContext.of(reference(file, DeclarationSource.dependency()),
				BranchSource.of(file), SERVICE_RELEASE);

		assertThat(plugin.getArtifactId()).isEqualTo(COMPILER_PLUGIN);
		assertThat(plugin.suppressSemanticUpgrading()).isTrue();
		assertThat(dependency.suppressSemanticUpgrading()).isFalse();
	}

	@Test
	void declaredDependencyUsesAggregatePluginOnlySemantics() {

		DeclaredDependency plugin = new DeclaredDependency(COMPILER_PLUGIN);
		plugin.addDeclarationSource(DeclarationSource.plugin());

		DeclaredDependency mixed = new DeclaredDependency(COMPILER_PLUGIN);
		mixed.addDeclarationSource(DeclarationSource.plugin());
		mixed.addDeclarationSource(DeclarationSource.dependency());

		assertThat(ResolutionContext.of(plugin, BranchSource.of((VirtualFile) null), SERVICE_RELEASE)
				.suppressSemanticUpgrading()).isTrue();
		assertThat(ResolutionContext.of(mixed, BranchSource.of((VirtualFile) null), SERVICE_RELEASE)
				.suppressSemanticUpgrading()).isFalse();
	}

	private static ArtifactReference reference(PsiFile file, DeclarationSource source) {
		return ArtifactReference.from(it -> it.artifact(COMPILER_PLUGIN)
				.versionSource(VersionSource.declared("3.14.0"))
				.declarationSource(source)
				.version(ArtifactVersion.of("3.14.0"))
				.declarationElement(file));
	}

}
