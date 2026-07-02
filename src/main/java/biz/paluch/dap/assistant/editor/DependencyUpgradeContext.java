/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.assistant.editor;

import java.util.function.Function;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.upgrade.UpgradeSuggestionsFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Element-anchored upgrade view: an {@link ArtifactReferenceContext} together
 * with the {@link UpgradeSuggestions} computed for it.
 *
 * <p>This is the live-PSI analog of {@code DependencyUpdateCandidate}, which
 * pairs the same suggestions with a collected {@code Dependency} for the dialog
 * flow rather than with a {@link PsiElement}. Build it only on the
 * upgrade-action surfaces (annotator, gutter line marker, Safe Version
 * intention, vulnerability remediation) that need computed suggestions;
 * surfaces that render only release, vulnerability, or rule state (completion,
 * documentation, drift, rule inspection) use the lighter
 * {@link ArtifactReferenceContext} and never pay for suggestion computation.
 *
 * <p>The context composes an already-resolved {@link ArtifactReferenceContext}
 * and layers suggestions on top through {@link UpgradeSuggestionsFactory}; it
 * does not re-resolve the element. All non-suggestion accessors delegate to the
 * reference context.
 *
 * @author Mark Paluch
 * @see ArtifactReferenceContext
 */
class DependencyUpgradeContext {

	private final ArtifactReferenceContext reference;

	private final UpgradeSuggestions suggestions;

	private DependencyUpgradeContext(ArtifactReferenceContext reference, UpgradeSuggestions suggestions) {
		this.reference = reference;
		this.suggestions = suggestions;
	}

	/**
	 * Resolve the given element and layer its upgrade suggestions, locating the
	 * owning {@link ProjectDependencyContext} through the dispatcher.
	 *
	 * @param element the PSI element under a dependency declaration to resolve.
	 * @return the upgrade context, or an {@link #isAbsent() absent} context when
	 * the element does not resolve to a version-defined dependency.
	 */
	public static DependencyUpgradeContext from(PsiElement element) {
		return of(ArtifactReferenceContext.from(element));
	}

	/**
	 * Resolve the given element and layer its upgrade suggestions, locating the
	 * owning {@link ProjectDependencyContext} through the given resolver.
	 *
	 * @param element the PSI element under a dependency declaration to resolve.
	 * @param getContext resolves the project dependency context for the element.
	 * @return the upgrade context, or an {@link #isAbsent() absent} context when
	 * the element does not resolve to a version-defined dependency.
	 */
	public static DependencyUpgradeContext from(PsiElement element,
			Function<PsiElement, ProjectDependencyContext> getContext) {
		return of(ArtifactReferenceContext.from(element, getContext));
	}

	/**
	 * Layer upgrade suggestions onto an already-resolved reference context.
	 *
	 * @param reference the resolved reference context; an
	 * {@link ArtifactReferenceContext#isAbsent() absent} reference yields an absent
	 * upgrade context carrying no suggestions.
	 * @return the upgrade context over the given reference.
	 */
	public static DependencyUpgradeContext of(ArtifactReferenceContext reference) {

		UpgradeSuggestions suggestions = new UpgradeSuggestionsFactory(reference.getCache())
				.createSuggestions(reference.getArtifactReference(), reference.getEvaluator());
		return new DependencyUpgradeContext(reference, suggestions);
	}

	/**
	 * @return {@code true} if the element resolved to a version-defined dependency;
	 * {@code false} otherwise.
	 */
	public boolean isPresent() {
		return reference.isPresent();
	}

	/**
	 * @return {@code true} if the element did not resolve to a version-defined
	 * dependency; {@code false} otherwise.
	 */
	public boolean isAbsent() {
		return reference.isAbsent();
	}

	public ArtifactReferenceContext getReferenceContext() {
		return reference;
	}

	public ArtifactReference getArtifactReference() {
		return reference.getArtifactReference();
	}

	public UpgradeSuggestions getSuggestions() {
		return suggestions;
	}

	public ProjectDependencyContext getDependencyContext() {
		return reference.getDependencyContext();
	}

	public DependencyRuleEvaluator getEvaluator() {
		return reference.getEvaluator();
	}

	/**
	 * @return {@code true} if a governing rule applies to the resolved artifact;
	 * {@code false} otherwise.
	 */
	public boolean hasRule() {
		return reference.hasRule();
	}

	/**
	 * Return the {@link Vulnerabilities} for the given version.
	 *
	 * @param artifactVersion the version to check.
	 * @return the known vulnerabilities, or absent when this context is absent or
	 * the version was never scanned.
	 */
	public Vulnerabilities getVulnerabilities(ArtifactVersion artifactVersion) {
		return reference.getVulnerabilities(artifactVersion);
	}

	/**
	 * Return the {@link Vulnerabilities} for the resolved artifact's current
	 * version.
	 *
	 * @return the known vulnerabilities for the current version, or absent.
	 */
	public Vulnerabilities getCurrentVulnerabilities() {
		return reference.getCurrentVulnerabilities();
	}

	/**
	 * Compute the editor highlight range for the given element through the active
	 * interface assistant.
	 *
	 * @param element the element to highlight.
	 * @return the range to highlight.
	 */
	public TextRange getHighlightRange(PsiElement element) {
		return reference.getHighlightRange(element);
	}

	@Override
	public String toString() {
		return reference.getArtifactReference() + " -> " + suggestions;
	}

}
