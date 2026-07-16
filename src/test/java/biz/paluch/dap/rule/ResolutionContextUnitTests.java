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

import java.lang.reflect.Proxy;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.psi.PsiElement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ResolutionContext}.
 *
 * @author Mark Paluch
 */
class ResolutionContextUnitTests {

	private static final ArtifactId ARTIFACT_ID = ArtifactId.of("org.example", "example");

	private static final BranchSource BRANCH_SOURCE = () -> null;

	private static final Versioned PROJECT_VERSION = Versioned.unversioned();

	@Test
	void referenceContextUsesDeclarationPluginSemantics() {

		ResolutionContext plugin = ResolutionContext.forDeclaration(declaration(DeclarationSource.plugin()),
				BRANCH_SOURCE,
				PROJECT_VERSION);
		ResolutionContext dependency = ResolutionContext.forDeclaration(declaration(DeclarationSource.dependency()),
				BRANCH_SOURCE, PROJECT_VERSION);

		assertThat(plugin.suppressSemanticUpgrading()).isTrue();
		assertThat(dependency.suppressSemanticUpgrading()).isFalse();
	}

	@Test
	void aggregateContextUsesPluginOnlySemantics() {

		ResolutionContext plugins = ResolutionContext.forAggregate(ARTIFACT_ID,
				List.of(DeclarationSource.plugin(), DeclarationSource.pluginManagement()), BRANCH_SOURCE,
				PROJECT_VERSION);
		ResolutionContext mixed = ResolutionContext.forAggregate(ARTIFACT_ID,
				List.of(DeclarationSource.plugin(), DeclarationSource.dependency()), BRANCH_SOURCE, PROJECT_VERSION);

		assertThat(plugins.suppressSemanticUpgrading()).isTrue();
		assertThat(mixed.suppressSemanticUpgrading()).isFalse();
	}

	private static ArtifactDeclaration declaration(DeclarationSource source) {

		return ArtifactDeclaration.builder().artifact(ARTIFACT_ID)
				.versionSource(VersionSource.declared("1.0.0"))
				.declarationSource(source)
				.declarationElement(psiElement()).build();
	}

	private static PsiElement psiElement() {

		return (PsiElement) Proxy.newProxyInstance(PsiElement.class.getClassLoader(),
				new Class<?>[] {PsiElement.class}, (proxy, method, args) -> null);
	}

}
