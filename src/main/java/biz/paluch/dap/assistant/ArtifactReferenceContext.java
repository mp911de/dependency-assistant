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

package biz.paluch.dap.assistant;

import java.util.function.Function;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.assistant.presentation.DependencyPresentation;
import biz.paluch.dap.assistant.presentation.DependencyPresentationFactory;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.metadata.ProjectMetadata;
import biz.paluch.dap.metadata.ProjectMetadataService;
import biz.paluch.dap.rule.BranchSource;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.rule.ResolutionContext;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.upgrade.UpgradeSuggestionsFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Resolution context for one {@link ArtifactReference}, built from the
 * {@link PsiElement} under a dependency declaration in a build file.
 *
 * <p>Resolution always returns a context. Callers use {@link #isPresent()} or
 * {@link #isAbsent()} to distinguish a resolved declaration from an element
 * that cannot participate in dependency operations. The regular element lookup
 * requires a version-defined declaration; the copied-PSI lookup can retain a
 * resolved declaration without a current version for completion.
 *
 * @author Mark Paluch
 */
public class ArtifactReferenceContext implements HasArtifactId, HasPackageIdentity {

	private static final ArtifactReferenceContext ABSENT = new ArtifactReferenceContext();

	private final ProjectDependencyContext dependencyContext;

	private final @Nullable StateService stateService;

	private final @Nullable ArtifactDeclaration declaration;

	private final @Nullable ArtifactVersion currentVersion;

	private final DependencyRule rule;

	private final DependencyRuleEvaluator evaluator;

	private @Nullable Releases releases;

	private @Nullable UpgradeSuggestions suggestions;

	private final ProjectMetadata projectMetadata;

	private final DependencyPresentation presentation;

	private ArtifactReferenceContext() {
		this.dependencyContext = ProjectDependencyContext.absent();
		this.stateService = null;
		this.declaration = null;
		this.currentVersion = null;
		this.rule = DependencyRule.absent();
		this.evaluator = DependencyRuleEvaluator.absent();
		this.releases = null;
		this.projectMetadata = ProjectMetadata.absent();
		this.presentation = null;
	}

	private ArtifactReferenceContext(ProjectDependencyContext dependencyContext, StateService stateService,
			ArtifactDeclaration declaration, @Nullable ArtifactVersion currentVersion, DependencyRule rule,
			DependencyRuleEvaluator evaluator, ProjectMetadata projectMetadata, DependencyPresentation presentation) {
		this.dependencyContext = dependencyContext;
		this.stateService = stateService;
		this.declaration = declaration;
		this.currentVersion = currentVersion;
		this.rule = rule;
		this.evaluator = evaluator;
		this.releases = null;
		this.projectMetadata = projectMetadata;
		this.presentation = presentation;
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
		return resolve(element, context, lookup, BranchSource.of(element), true);
	}

	/**
	 * Resolve the given element against the supplied context file.
	 *
	 * <p>This variant supports copied PSI, such as IntelliJ completion positions,
	 * where the element's containing file is not the project file used to locate
	 * the dependency context. A resolved declaration is retained even when no
	 * current version is available.
	 *
	 * @param element the PSI element under a dependency declaration to resolve.
	 * @param contextFile the original project file used for context, lookup, and
	 * branch-rule resolution.
	 * @return the resolved context, or an {@link #isAbsent() absent} context when
	 * no dependency context or artifact declaration applies.
	 */
	public static ArtifactReferenceContext from(PsiElement element, PsiFile contextFile) {

		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(element.getProject(),
				contextFile);
		if (context.isAbsent()) {
			return ABSENT;
		}

		VirtualFile containingFile = contextFile.getVirtualFile();
		VersionUpgradeLookup lookup = context.getLookup(element, containingFile);
		return resolve(element, context, lookup, BranchSource.of(containingFile), false);
	}

	private static ArtifactReferenceContext resolve(PsiElement element, ProjectDependencyContext context,
			VersionUpgradeLookup lookup, BranchSource branchSource, boolean requireDefinedVersion) {

		ArtifactReference artifactReference = lookup.resolveArtifactReference(element);
		if (!artifactReference.isResolved()) {
			return ABSENT;
		}
		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		if (requireDefinedVersion && !declaration.isVersioned()) {
			return ABSENT;
		}


		ArtifactVersion currentVersion = requireDefinedVersion ? declaration.getVersion()
				: lookup.getCurrentVersion(artifactReference);

		DependencyRuleService ruleService = DependencyRuleService.getInstance(element.getProject());
		ResolutionContext resolutionContext = ResolutionContext.forDeclaration(declaration,
				branchSource, context.getProjectVersion());
		DependencyRule rule = ruleService.resolve(resolutionContext);
		DependencyRuleEvaluator evaluator = currentVersion != null
				? DependencyRuleEvaluator.create(rule, currentVersion)
				: DependencyRuleEvaluator.absent();
		StateService stateService = lookup.getStateService();
		DependencyPresentationFactory presentationFactory = new DependencyPresentationFactory(element.getProject());
		ProjectMetadata metadata = ProjectMetadataService.getMetadata(declaration);
		DependencyPresentation presentation = presentationFactory.create(declaration.getPackageIdentity(),
				rule, context.getInterfaceAssistant());
		return new ArtifactReferenceContext(context, stateService, declaration, currentVersion,
				rule, evaluator, metadata, presentation);
	}

	/**
	 * @return {@literal true} if the element resolved to an artifact declaration;
	 * {@literal false} otherwise.
	 */
	public boolean isPresent() {
		return declaration != null;
	}

	/**
	 * @return {@literal true} if the element did not resolve to an artifact
	 * declaration; {@literal false} otherwise.
	 */
	public boolean isAbsent() {
		return !isPresent();
	}

	public ProjectDependencyContext getDependencyContext() {
		return dependencyContext;
	}

	/**
	 * Return the artifact id of the resolved package identity.
	 *
	 * @return the resolved package id.
	 * @throws IllegalStateException if this context is {@link #isAbsent() absent}.
	 */
	@Override
	public PackageIdentity getPackageIdentity() {
		return getDeclaration().getPackageIdentity();
	}

	/**
	 * Return the artifact id of the resolved artifact Id.
	 *
	 * @return the resolved artifact id.
	 * @throws IllegalStateException if this context is {@link #isAbsent() absent}.
	 */
	@Override
	public ArtifactId getArtifactId() {
		return getDeclaration().getArtifactId();
	}

	/**
	 * Return the resolved artifact declaration.
	 *
	 * @return the declaration represented by this context.
	 * @throws IllegalStateException if this context is {@link #isAbsent() absent}.
	 */
	public ArtifactDeclaration getDeclaration() {
		Assert.state(declaration != null, "No declaration on absent ArtifactReferenceContext");
		return declaration;
	}

	/**
	 * Return the version of the resolved declaration.
	 *
	 * @return the non-null version established by {@link #from(PsiElement)}.
	 * @throws IllegalStateException if this context is {@link #isAbsent() absent}
	 * or the declaration has no version.
	 */
	public ArtifactVersion getVersion() {
		return getDeclaration().getVersion();
	}

	/**
	 * Return the current version resolved from project state or the declaration.
	 *
	 * @return the current version, or {@literal null} when the resolved declaration
	 * carries no version and project state has no entry.
	 */
	public @Nullable ArtifactVersion getCurrentVersion() {
		return currentVersion;
	}

	/**
	 * Return the dependency rule governing the resolved declaration.
	 *
	 * @return the resolved rule, or the absent rule when no rule applies.
	 */
	public DependencyRule getRule() {
		return rule;
	}

	public DependencyRuleEvaluator getEvaluator() {
		return evaluator;
	}

	public Releases getReleases() {

		if (isAbsent()) {
			return Releases.empty();
		}
		if (releases == null) {
			releases = getStateService().getCache().getReleases(getPackageIdentity());
		}
		return releases;
	}

	/**
	 * Return upgrade suggestions for the resolved declaration.
	 *
	 * <p>Suggestions are computed when first requested so reference-only editor
	 * surfaces do not pay for upgrade policy evaluation.
	 *
	 * @return the computed suggestions, or empty suggestions when this context is
	 * absent.
	 */
	public UpgradeSuggestions getSuggestions() {

		if (isAbsent()) {
			return UpgradeSuggestions.empty();
		}
		if (!getDeclaration().isVersioned() || !getDeclaration().hasVersionSource()) {
			return UpgradeSuggestions.empty();
		}
		if (suggestions == null) {
			Dependency dependency = getDeclaration().toDependency();
			suggestions = UpgradeSuggestionsFactory.createSuggestions(dependency, getReleases(),
					version -> getStateService().getVulnerabilities(getPackageIdentity(), version), rule);
		}
		return suggestions;
	}

	/**
	 * Return the {@link StateService} backing this context. The service also serves
	 * as the advisory lookup, aggregating BOM member advisories on top of the raw
	 * cache state.
	 *
	 * @return the backing state service.
	 * @throws IllegalStateException if this context is {@link #isAbsent() absent}.
	 */
	public StateService getStateService() {
		Assert.state(stateService != null, "No state service on absent ArtifactReferenceContext");
		return stateService;
	}

	public ProjectMetadata getProjectMetadata() {
		return projectMetadata;
	}

	public DependencyPresentation getPresentation() {
		return presentation;
	}

	/**
	 * Return the {@link Vulnerabilities} for the resolved artifact.
	 *
	 * @return the {@link Vulnerabilities} for the current version, or absent when
	 * this context is {@link #isAbsent() absent} or the current version was never
	 * scanned.
	 */
	public Vulnerabilities getCurrentVulnerabilities() {

		if (currentVersion == null) {
			return Vulnerabilities.absent();
		}
		return getVulnerabilities(currentVersion);
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
		if (isAbsent()) {
			return Vulnerabilities.absent();
		}
		return getStateService().getVulnerabilities(getPackageIdentity(), artifactVersion);
	}

	/**
	 * Return the status of the given candidate version within this resolved
	 * reference context.
	 * @param artifactVersion the candidate version to describe.
	 * @return the candidate version status.
	 */
	public VersionStatus getStatus(ArtifactVersion artifactVersion) {

		return VersionStatus.of(evaluator, currentVersion, artifactVersion, getVulnerabilities(artifactVersion));
	}

	/**
	 * @return {@literal true} if a governing {@link DependencyRule} applies to the
	 * resolved artifact; {@literal false} otherwise.
	 */
	public boolean hasRule() {
		return rule.isPresent();
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
		return declaration == null ? "Absent" : "Resolved: " + declaration;
	}

}
