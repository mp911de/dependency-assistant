package biz.paluch.dap.assistant;

import java.util.function.Function;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.AvailableUpgrades;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Resolved context for one {@link ArtifactReference}, built from the
 * {@link PsiElement} under a dependency declaration in a build file.
 *
 * <p>Editor features call {@link #from(PsiElement)} to turn an element into
 * everything they need to act on it: the resolved {@link ArtifactReference} and
 * its {@link Cache}, the owning {@link ProjectDependencyContext}, and the
 * governing {@link DependencyRule} together with its
 * {@link EvaluatedDependencyRule evaluation}. When the element does not resolve
 * to a version-defined dependency the context is {@link #isAbsent() absent}: a
 * shared empty instance on which only the present/absent checks are meaningful.
 * Callers test {@link #isPresent()} before reading any held value or calling
 * {@link #suggestUpgrades()}.
 *
 * @author Mark Paluch
 */
class ArtifactReferenceContext {

	private static final ArtifactReferenceContext ABSENT = new ArtifactReferenceContext(
			ProjectDependencyContext.absent(), new Cache(),
			null, ArtifactReference.unresolved(),
			DependencyRule.absent(), EvaluatedDependencyRule.absent());

	private final ProjectDependencyContext dependencyContext;

	private final Cache cache;

	private final @Nullable PsiElement element;

	private final ArtifactReference artifactReference;

	private final DependencyRule rule;

	private final EvaluatedDependencyRule evaluatedRule;

	private ArtifactReferenceContext(ProjectDependencyContext dependencyContext, Cache cache,
			@Nullable PsiElement element, ArtifactReference artifactReference,
			DependencyRule rule, EvaluatedDependencyRule evaluatedRule) {
		this.dependencyContext = dependencyContext;
		this.cache = cache;
		this.element = element;
		this.artifactReference = artifactReference;
		this.rule = rule;
		this.evaluatedRule = evaluatedRule;
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
		if ((context.isAbsent())) {
			return ABSENT;
		}

		VersionUpgradeLookup lookup = context.getLookup(element, element.getContainingFile().getVirtualFile());

		ArtifactReference artifactReference = UpgradeSuggestions.resolveArtifact(context, element);
		if (!artifactReference.isResolved() || !artifactReference.getDeclaration().isVersionDefined()) {
			return ABSENT;
		}

		VirtualFile containingFile = element.getContainingFile().getVirtualFile();
		DependencyfileService ruleService = DependencyfileService.getInstance(element.getProject());
		ArtifactId artifactId = artifactReference.getArtifactId();
		DependencyRule rule = artifactReference.getDeclaration().getDeclarationSource().isPlugin()
				? DependencyRule.absent()
				: ruleService.resolve(artifactId, containingFile, context.getProjectVersion());
		EvaluatedDependencyRule evaluated = EvaluatedDependencyRule.of(rule, artifactId,
				artifactReference.getDeclaration().getVersion().getVersion(),
				context.getInterfaceAssistant());

		return new ArtifactReferenceContext(context, lookup.getCache(), element, artifactReference,
				rule, evaluated);
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

	/**
	 * Suggest the upgrades available for the resolved artifact, keeping only those
	 * the governing rule enables.
	 *
	 * @return the available upgrades, filtered to the enabled rule.
	 * @throws IllegalStateException if this context is {@link #isAbsent() absent}.
	 */
	public AvailableUpgrades suggestUpgrades() {
		Assert.state(element != null, "No element on absent UpgradeContext");
		return UpgradeSuggestions.suggest(dependencyContext, element)
				.filterSuggestions(rule::isEnabled);
	}

	/**
	 * @return {@code true} if a governing {@link DependencyRule} applies to the
	 * resolved artifact; {@code false} otherwise.
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

	public ProjectDependencyContext getDependencyContext() {
		return dependencyContext;
	}

	public ArtifactReference getArtifactReference() {
		return artifactReference;
	}

	public Cache getCache() {
		return cache;
	}

	public EvaluatedDependencyRule getEvaluatedRule() {
		return evaluatedRule;
	}

}
