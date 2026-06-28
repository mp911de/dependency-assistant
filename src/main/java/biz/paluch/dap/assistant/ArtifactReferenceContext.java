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

import java.util.function.Function;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.rule.BranchSource;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.rule.ResolutionContext;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * Resolved context for one {@link ArtifactReference}, built from the
 * {@link PsiElement} under a dependency declaration in a build file.
 *
 * @author Mark Paluch
 */
public class ArtifactReferenceContext {

	private static final ArtifactReferenceContext ABSENT = new ArtifactReferenceContext(
			ProjectDependencyContext.absent(), new Cache(), ArtifactReference.unresolved(),
			DependencyRuleEvaluator.absent());

	private final ProjectDependencyContext dependencyContext;

	private final Cache cache;

	private final @Nullable ArtifactDeclaration declaration;

	private final ArtifactReference artifactReference;

	private final DependencyRuleEvaluator evaluator;

	private final Releases releases;

	ArtifactReferenceContext(ProjectDependencyContext dependencyContext, Cache cache,
			ArtifactReference artifactReference, DependencyRuleEvaluator evaluator) {
		this.dependencyContext = dependencyContext;
		this.cache = cache;
		this.declaration = artifactReference.isResolved() ? artifactReference.getDeclaration() : null;
		this.artifactReference = artifactReference;
		this.evaluator = evaluator;
		this.releases = artifactReference.isResolved() ? cache.getReleases(artifactReference.getArtifactId())
				: Releases.empty();
	}

	/**
	 * Resolve the given element into an {@code ArtifactReferenceContext}, locating
	 * the owning {@link ProjectDependencyContext} through the dispatcher.
	 *
	 * @param element the PSI element under a dependency declaration to resolve.
	 * @return the resolved context, or an {@link #isAbsent() absent} context when
	 * the element has no dependency context or does not declare a defined version.
	 */
	public static ArtifactReferenceContext from(PsiElement element) {
		return from(element, DependencyAssistantDispatcher::findFirstContext);
	}

	/**
	 * Resolve the given element into an {@code ArtifactReferenceContext}, locating
	 * the owning {@link ProjectDependencyContext} through the given resolver.
	 *
	 * <p>The resolver seam lets a caller supply its own context lookup, for example
	 * a per-provider cache or a test double.
	 *
	 * @param element the PSI element under a dependency declaration to resolve.
	 * @param getContext resolves the project dependency context for the element,
	 * and yields an {@link ProjectDependencyContext#absent() absent} context when
	 * none applies.
	 * @return the resolved context, or an {@link #isAbsent() absent} context when
	 * the element has no dependency context or does not declare a defined version.
	 */
	public static ArtifactReferenceContext from(PsiElement element,
			Function<PsiElement, ProjectDependencyContext> getContext) {

		ProjectDependencyContext context = getContext.apply(element);
		if (context.isAbsent() || !context.isVersionElement(element)) {
			return ABSENT;
		}

		VersionUpgradeLookup lookup = context.getLookup(element, element.getContainingFile().getVirtualFile());
		ArtifactReference artifactReference = lookup.resolveArtifactReference(element);
		if (!artifactReference.isResolved() || !artifactReference.getDeclaration().isVersionDefined()) {
			return ABSENT;
		}

		DependencyRuleService ruleService = DependencyRuleService.getInstance(element.getProject());
		ResolutionContext resolutionContext = ResolutionContext.of(artifactReference, BranchSource.of(element),
				context.getProjectVersion());
		DependencyRuleEvaluator evaluator = DependencyRuleEvaluator.evaluate(ruleService, resolutionContext,
				artifactReference.getDeclaration().getVersion(), context.getInterfaceAssistant());

		return new ArtifactReferenceContext(context, lookup.getCache(), artifactReference, evaluator);
	}

	/**
	 * @return {@code true} if the element resolved to a version-defined dependency;
	 * {@code false} otherwise.
	 */
	public boolean isPresent() {
		return artifactReference.isResolved();
	}

	/**
	 * @return {@code true} if the element did not resolve to a version-defined
	 * dependency; {@code false} otherwise.
	 */
	public boolean isAbsent() {
		return !isPresent();
	}

	public ProjectDependencyContext getDependencyContext() {
		return dependencyContext;
	}

	public ArtifactReference getArtifactReference() {
		return artifactReference;
	}

	public ArtifactDeclaration getDeclaration() {
		Assert.state(isPresent(), "No declaration on absent ArtifactReferenceContext");
		return declaration;
	}

	public DependencyRuleEvaluator getEvaluator() {
		return evaluator;
	}

	public Releases getReleases() {
		return releases;
	}

	/**
	 * Return the {@link Cache} backing this context, for composing layers (such as
	 * {@link DependencyUpgradeContext}) that derive further state from the same
	 * release and vulnerability store without re-resolving the element.
	 *
	 * @return the backing cache.
	 */
	Cache getCache() {
		return cache;
	}

	/**
	 * Return the {@link Vulnerabilities} for the resolved artifact.
	 *
	 * @return the {@link Vulnerabilities} for the current version, or absent when
	 * this context is {@link #isAbsent() absent} or the current version was never
	 * scanned.
	 */
	public Vulnerabilities getCurrentVulnerabilities() {

		if (isAbsent()) {
			return Vulnerabilities.absent();
		}
		ArtifactDeclaration declaration = getDeclaration();
		if (!declaration.isVersionDefined()) {
			return Vulnerabilities.absent();
		}
		return getVulnerabilities(declaration.getVersion());
	}

	/**
	 * Return the {@link Vulnerabilities} for the given version of the resolved
	 * artifact.
	 * @param artifactVersion the version to check.
	 * @return the {@link Vulnerabilities} for the given version, or absent when
	 * this context is {@link #isAbsent() absent} or the given version was never
	 * scanned.
	 */
	public Vulnerabilities getVulnerabilities(ArtifactVersion artifactVersion) {
		if (!artifactReference.isResolved()) {
			return Vulnerabilities.absent();
		}
		return cache.getVulnerabilities(artifactReference.getArtifactId(), artifactVersion);
	}

	/**
	 * Return the status of the given candidate version within this resolved
	 * reference context.
	 * @param artifactVersion the candidate version to describe.
	 * @return the candidate version status.
	 */
	VersionStatus getStatus(ArtifactVersion artifactVersion) {

		ArtifactVersion currentVersion = null;
		if (isPresent() && getDeclaration().isVersionDefined()) {
			currentVersion = getDeclaration().getVersion();
		}

		return VersionStatus.of(evaluator, currentVersion, artifactVersion, getVulnerabilities(artifactVersion));
	}

	/**
	 * @return {@code true} if a governing {@link DependencyRule} applies to the
	 * resolved artifact; {@code false} otherwise.
	 */
	public boolean hasRule() {
		return evaluator.isPresent();
	}

	/**
	 * Compute the editor highlight range for the given element through the active
	 * interface assistant.
	 *
	 * @param element the element to highlight, which may be a navigation anchor
	 * distinct from the resolved element.
	 * @return the range to highlight.
	 */
	public TextRange getHighlightRange(PsiElement element) {
		return dependencyContext.getInterfaceAssistant().getHighlightRange(element);
	}

	@Override
	public String toString() {
		return artifactReference.toString();
	}

}
