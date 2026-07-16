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
import java.util.concurrent.atomic.AtomicInteger;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactReference;
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

	private static final ArtifactId ARTIFACT = ArtifactId.of("com.example", "demo");

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

	@Test
	void releasesAreLoadedOnlyWhenRequested() {

		Releases releases = TestReleases.from("1.0.0", "1.1.0");
		AtomicInteger releaseLookups = new AtomicInteger();
		Cache cache = new Cache() {

			@Override
			public Releases getReleases(ArtifactId artifactId) {
				releaseLookups.incrementAndGet();
				return releases;
			}

		};
		StateService stateService = new StateService(cache);

		ArtifactReferenceContext context = new ArtifactReferenceContext(ProjectDependencyContext.absent(), stateService,
				reference().getDeclaration(), DependencyRuleEvaluator.absent());

		assertThat(releaseLookups).hasValue(0);
		assertThat(context.getReleases()).isSameAs(releases);
		assertThat(context.getReleases()).isSameAs(releases);
		assertThat(releaseLookups).hasValue(1);
	}

	@Test
	void suggestionsReuseLazilyLoadedReleasesAndAreCached() {

		Releases releases = TestReleases.from("1.0.0", "1.1.0");
		AtomicInteger releaseLookups = new AtomicInteger();
		Cache cache = new Cache() {

			@Override
			public Releases getReleases(ArtifactId artifactId) {
				releaseLookups.incrementAndGet();
				return releases;
			}

		};
		ArtifactReferenceContext context = new ArtifactReferenceContext(ProjectDependencyContext.absent(),
				new StateService(cache), reference().getDeclaration(), DependencyRuleEvaluator.absent());

		assertThat(context.getReleases()).isSameAs(releases);
		assertThat(context.getSuggestions()).isNotEmpty();
		assertThat(context.getSuggestions()).isNotEmpty();
		assertThat(releaseLookups).hasValue(1);
	}

	@Test
	void rejectsPresentContextWithoutDefinedVersion() {

		ArtifactReference versionless = ArtifactReference.from(builder -> builder.artifact(ARTIFACT)
				.versionSource(VersionSource.none())
				.declarationSource(DeclarationSource.dependency())
				.declarationElement(psiElement()));

		assertThatIllegalArgumentException().isThrownBy(() -> new ArtifactReferenceContext(
				ProjectDependencyContext.absent(), new StateService(), versionless.getDeclaration(),
				DependencyRuleEvaluator.absent()));
	}

	private static ArtifactReference reference() {
		return ArtifactReference.from(builder -> builder.artifact(ARTIFACT)
				.version(ArtifactVersion.of("1.0.0"))
				.versionSource(VersionSource.declared("1.0.0"))
				.declarationSource(DeclarationSource.dependency())
				.declarationElement(psiElement()));
	}

	private static PsiElement psiElement() {
		return (PsiElement) Proxy.newProxyInstance(PsiElement.class.getClassLoader(), new Class<?>[] {PsiElement.class},
				(proxy, method, args) -> null);
	}

}
