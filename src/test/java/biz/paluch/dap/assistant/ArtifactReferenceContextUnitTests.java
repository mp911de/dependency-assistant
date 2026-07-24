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

package biz.paluch.dap.assistant;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.checker.Vulnerabilities;
import com.intellij.psi.PsiElement;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Unit tests for {@link ArtifactReferenceContext} and
 * {@link ArtifactReferenceContextVisitor}.
 *
 * @author Mark Paluch
 */
class ArtifactReferenceContextUnitTests {

	private static final ArtifactVersion CANDIDATE = ArtifactVersion.of("1.1.0");

	@Test
	void absentResolutionRetainsAbsentDomainBehavior() {

		ArtifactReferenceContext context = ArtifactReferenceContext.from(psiElement(),
				it -> ProjectDependencyContext.absent());

		assertThat(context.isPresent()).isFalse();
		assertThat(context.isAbsent()).isTrue();
		assertThat(context.getReleases()).isEmpty();
		assertThat(context.getSuggestions()).isEmpty();
		assertThat(context.getCurrentVulnerabilities()).isSameAs(Vulnerabilities.absent());
		assertThat(context.getVulnerabilities(CANDIDATE)).isSameAs(Vulnerabilities.absent());
		assertThat(context.getStatus(CANDIDATE).getVersionAge()).isEqualTo(VersionAge.SAME_OR_UNKNOWN);
		assertThatIllegalStateException().isThrownBy(context::getDeclaration)
				.withMessage("No declaration on absent ArtifactReferenceContext");
		assertThatIllegalStateException().isThrownBy(context::getStateService)
				.withMessage("No state service on absent ArtifactReferenceContext");
	}

	@Test
	void visitorSkipsElementsWithoutArtifactReferenceContext() {

		AtomicBoolean visited = new AtomicBoolean();
		ArtifactReferenceContextVisitor visitor = new ArtifactReferenceContextVisitor(
				ProjectDependencyContext.absent()) {

			@Override
			protected void visitArtifactReference(PsiElement visitedElement, ArtifactReferenceContext context) {
				visited.set(true);
			}

		};

		visitor.visitElement(psiElement());

		assertThat(visited).isFalse();
	}

	private static PsiElement psiElement() {
		return (PsiElement) Proxy.newProxyInstance(PsiElement.class.getClassLoader(), new Class<?>[] {PsiElement.class},
				(proxy, method, args) -> null);
	}

}
