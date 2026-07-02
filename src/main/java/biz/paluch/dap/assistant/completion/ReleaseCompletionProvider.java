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

package biz.paluch.dap.assistant.completion;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.rule.BranchSource;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.rule.ResolutionContext;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.PsiElements;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

/**
 * {@link CompletionProvider} that turns resolved artifact metadata into release
 * lookup elements.
 *
 * <p>The provider resolves the artifact at the completion position through the
 * active {@link ProjectDependencyContext}, reads cached releases from
 * {@link StateService}, and renders them as prioritized {@link ArtifactRelease}
 * lookup elements.
 *
 * <p>Subclasses usually customize one of the protected hooks instead of
 * replacing the provider:
 * {@link #getPrefixMatcher(CompletionParameters, CompletionResultSet)} for
 * format-specific prefix handling,
 * {@link #postProcess(CompletionParameters, LookupElementBuilder, PsiElement, ArtifactRelease)}
 * for insert handlers, and {@link #getRefStyle(PsiElement, CompletionMetadata)}
 * for tag-vs-SHA insertion.
 *
 * <p>This provider does not decide completion locations, refresh release
 * metadata, or contact remote repositories. Register it from a
 * {@code CompletionContributor} at PSI positions that can be resolved by the
 * current dependency context. The default implementation is stateless and can
 * be reused between contributors.
 *
 * @author Mark Paluch
 * @see ArtifactReleaseRenderer
 * @see VersionUpgradeLookup
 */
public class ReleaseCompletionProvider extends CompletionProvider<CompletionParameters> {

	/**
	 * Add release completions for the artifact resolved at the current completion
	 * position.
	 *
	 * @param parameters the IntelliJ completion parameters; must not be
	 * {@literal null}.
	 * @param context the processing context supplied by IntelliJ; must not be
	 * {@literal null}.
	 * @param result the result set to receive release lookup elements; must not be
	 * {@literal null}.
	 */
	@Override
	protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
			CompletionResultSet result) {

		PsiElement position = parameters.getOriginalPosition();
		if (position == null) {
			position = parameters.getPosition();
		}
		position = PsiElements.unleaf(position);

		CompletionMetadata metadata = getCompletionMetadata(position, parameters.getOriginalFile(),
				parameters.getOriginalFile().getVirtualFile());
		if (metadata == null) {
			return;
		}

		RefStyle refStyle = getRefStyle(position, metadata);
		Project project = parameters.getPosition().getProject();
		Cache cache = StateService.getInstance(project).getCache();
		ArtifactId artifactId = metadata.artifactReference().getArtifactId();
		Releases history = cache.getReleases(artifactId);

		if (history.isEmpty()) {
			result.addLookupAdvertisement(MessageBundle.message("completion.advertisement.no-releases"));
			return;
		}

		DependencyRuleService ruleService = DependencyRuleService.getInstance(project);
		ResolutionContext resolutionContext = ResolutionContext.forReference(metadata.artifactReference,
				BranchSource.of(parameters.getEditor().getVirtualFile()), metadata.context().getProjectVersion());
		DependencyRule rule = ruleService.resolve(resolutionContext);

		CompletionResultSet prefixed = getPrefixMatcher(parameters, result);
		List<ArtifactRelease> proposals = proposals(parameters, history, metadata, rule, artifactId);

		advertiseShowAllReleases(parameters, result, history.size(), proposals.size());

		CompletionResultSet versionsResult = prefixed.withRelevanceSorter(releaseOrderSorter(proposals));
		versionsResult.restartCompletionWhenNothingMatches();

		Map<ArtifactVersion, Vulnerabilities> vulnerabilities = cache.getVulnerabilities(artifactId);
		ArtifactReleaseRenderer renderer = new ArtifactReleaseRenderer(metadata.currentVersion(), rule,
				key -> vulnerabilities.getOrDefault(key, Vulnerabilities.absent()));

		for (ArtifactRelease release : proposals) {
			renderer.withVersion(release);
		}

		// Auto-inserting a single match is only safe when nothing is hidden;
		// a curated selection must keep the lookup open so the advertisement can
		// point at the full history.
		AutoCompletionPolicy autoCompletionPolicy = proposals.size() < history.size()
				? AutoCompletionPolicy.NEVER_AUTOCOMPLETE
				: AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE;

		List<LookupElement> elements = new ArrayList<>();
		for (ArtifactRelease release : proposals) {

			ArtifactVersion completionVersion = release.getVersion();
			LookupElementBuilder element = createLookupElement(release, completionVersion, refStyle)
					.withRenderer(renderer);

			if (metadata.versionLiteral() != null) {
				element = element
						.withInsertHandler(new LookupElementInsertHandler(metadata.versionLiteral()));
			}
			element = postProcess(parameters, element, position, release);
			elements.add(element.withAutoCompletionPolicy(autoCompletionPolicy));
		}

		versionsResult.addAllElements(elements);
	}

	/**
	 * Return the releases to contribute: the full history when
	 * {@link #showsFullHistory(CompletionParameters)} applies, otherwise the
	 * curated {@link ReleaseProposals} selection steered by the typed version
	 * prefix and unioned with the governing rule's remediation target.
	 */
	private static List<ArtifactRelease> proposals(CompletionParameters parameters, Releases history,
			CompletionMetadata metadata, DependencyRule rule, ArtifactId artifactId) {

		if (showsFullHistory(parameters)) {
			return history.stream().map(release -> new ArtifactRelease(artifactId, release)).toList();
		}

		// The stem derives from the typed text, not the prefix matcher: several
		// contributors deliberately match with an empty prefix.
		VersionStem stem = VersionStem.from(getPrefix(parameters));
		ReleaseProposals proposals = ReleaseProposals.select(history, metadata.currentVersion(), stem);

		Release remediation = rule.suggestRemediation(history);
		if (remediation != null) {
			proposals = proposals.with(remediation);
		}

		return proposals.stream().map(release -> new ArtifactRelease(artifactId, release)).toList();
	}

	/**
	 * Advertise repeated completion invocation as the way to see the full release
	 * history regardless of the typed prefix, including the history size when the
	 * curated first invocation hides releases.
	 */
	private static void advertiseShowAllReleases(CompletionParameters parameters,
			CompletionResultSet result, int total, int shown) {

		if (showsFullHistory(parameters)) {
			return;
		}

		String shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CODE_COMPLETION);
		if (!StringUtils.hasText(shortcut)) {
			return;
		}

		NumberFormat format = NumberFormat.getIntegerInstance();

		result.addLookupAdvertisement(shown < total
				? MessageBundle.message("completion.advertisement.show-all-count", shortcut, format.format(total))
				: MessageBundle.message("completion.advertisement.show-all", shortcut));
	}

	/**
	 * Return a sorter that keeps the cached release order, the newest release
	 * first, instead of letting platform relevance weighers reorder version items.
	 */
	private static CompletionSorter releaseOrderSorter(List<ArtifactRelease> releases) {

		Map<ArtifactRelease, Integer> order = new IdentityHashMap<>();
		for (ArtifactRelease release : releases) {
			order.put(release, order.size());
		}

		return CompletionSorter.emptySorter().weigh(new LookupElementWeigher("dependencyAssistantReleaseOrder") {

			@Override
			public Integer weigh(LookupElement element) {
				return element.getObject() instanceof ArtifactRelease release ? order.get(release) : null;
			}

		});
	}

	private LookupElementBuilder createLookupElement(ArtifactRelease release, ArtifactVersion completionVersion,
			RefStyle refStyle) {

		String completion;
		Set<String> lookupStrings = new LinkedHashSet<>();

		ArtifactVersion form = completionVersion;
		lookupStrings.add(form.toString());
		while (form.isWrapped()) {
			form = form.getVersion();
			lookupStrings.add(form.toString());
		}

		if (completionVersion instanceof GitVersion gitVersion && StringUtils.hasText(gitVersion.getSha())
				&& refStyle == RefStyle.SHA) {
			completion = gitVersion.getSha();
			lookupStrings.add(gitVersion.getSha());
			lookupStrings.add(gitVersion.getShortSha());
		} else {
			completion = completionVersion.toString();
		}

		return LookupElementBuilder.create(release, completion).withLookupStrings(lookupStrings);
	}

	private @Nullable CompletionMetadata getCompletionMetadata(PsiElement element, PsiFile psiFile,
			VirtualFile containingFile) {

		ProjectDependencyContext context = context(element, psiFile);
		if (context.isAbsent()) {
			return null;
		}

		VersionUpgradeLookup lookup = context.getLookup(element, containingFile);
		ArtifactReference artifactReference = lookup.resolveArtifactReference(element);
		if (!artifactReference.isResolved()) {
			return null;
		}

		ArtifactVersion version = lookup.getCurrentVersion(artifactReference);
		if (version == null && artifactReference.getDeclaration().isVersionDefined()) {
			version = artifactReference.getDeclaration().getVersion();
		}

		return new CompletionMetadata(artifactReference, version,
				artifactReference.getDeclaration().getVersionLiteral(), context);
	}

	/**
	 * Return the reference style to use when choosing the inserted lookup string.
	 *
	 * <p>The default inserts version text. Contributors for formats that preserve
	 * SHA references can return {@link RefStyle#SHA}; in that case Git-backed
	 * releases with SHA metadata insert the SHA while keeping version lookup
	 * strings available for matching.
	 * @param element the PSI element used to resolve completion metadata.
	 * @param metadata the resolved completion metadata.
	 * @return the reference style to use; guaranteed to be not {@literal null}.
	 */
	protected RefStyle getRefStyle(PsiElement element, CompletionMetadata metadata) {
		return RefStyle.VERSION;
	}

	/**
	 * Customize the lookup element for a release option.
	 *
	 * <p>The builder already has the release renderer, lookup strings, and default
	 * replacement handler applied. Override to add format-specific insert handling
	 * or lookup metadata while preserving the supplied builder's existing behavior.
	 * The default returns {@code builder} unchanged.
	 * @param parameters the IntelliJ completion parameters.
	 * @param builder the lookup element builder prepared by this provider.
	 * @param element the PSI element used to resolve completion metadata.
	 * @param option the release option represented by the lookup element.
	 * @return the lookup element builder to add to the result set.
	 */
	protected LookupElementBuilder postProcess(CompletionParameters parameters, LookupElementBuilder builder,
			PsiElement element, ArtifactRelease option) {
		return builder;
	}

	/**
	 * Return the result set with the prefix matcher used for release lookup
	 * elements.
	 *
	 * <p>The default matcher uses an empty prefix on full-history invocations (see
	 * {@link #showsFullHistory(CompletionParameters)}) and otherwise uses
	 * {@link #getPrefix(CompletionParameters)}. Contributors with larger
	 * surrounding syntaxes, such as URLs or dependency notations, can override this
	 * method to calculate the prefix from a format-specific version range.
	 * @param parameters the IntelliJ completion parameters.
	 * @param result the original completion result set.
	 * @return the result set to receive release lookup elements.
	 */
	protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, CompletionResultSet result) {
		return showsFullHistory(parameters) ? result.withPrefixMatcher("")
				: result.withPrefixMatcher(getPrefix(parameters));
	}

	/**
	 * Return whether this invocation shows the full release history instead of the
	 * curated proposals.
	 *
	 * <p>Repeated completion cycles between the two stages: autopopup and the first
	 * explicit invocation show the curated proposals, every second explicit
	 * invocation shows the full history, and one more invocation returns to the
	 * proposals.
	 * @param parameters the IntelliJ completion parameters.
	 * @return {@literal true} if this invocation shows the full history;
	 * {@literal false} if it shows the curated proposals.
	 */
	protected static boolean showsFullHistory(CompletionParameters parameters) {

		int invocationCount = parameters.getInvocationCount();
		return invocationCount > 1 && invocationCount % 2 == 0;
	}

	/**
	 * Return whether the {@code typedChar} is a typical version character such as a
	 * letter, digit or dot.
	 * @param typedChar the character to check.
	 * @return {@literal true} if the character should trigger release completion;
	 * {@literal false} otherwise.
	 */
	public static boolean isVersionCharacter(char typedChar) {
		return Character.isLetterOrDigit(typedChar) || typedChar == '.';
	}

	/**
	 * Return whether {@code c} can be part of a version token. Version tokens may
	 * contain letters, digits, dots, hyphens, underscores, and plus signs.
	 * @param c the character to check.
	 * @return {@literal true} if the character belongs to a version token;
	 * {@literal false} otherwise.
	 */
	public static boolean isVersionTokenCharacter(char c) {
		return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == '+';
	}

	/**
	 * Return the version-token prefix at the current completion position.
	 *
	 * <p>The original position is preferred when IntelliJ has inserted completion
	 * placeholder PSI. If no original position is available, the live completion
	 * position is used.
	 * @param parameters the IntelliJ completion parameters.
	 * @return the version-token prefix before the caret.
	 */
	protected static String getPrefix(CompletionParameters parameters) {

		PsiElement position = parameters.getOriginalPosition();
		if (position == null) {
			position = parameters.getPosition();
		}
		position = PsiElements.unleaf(position);

		return getPrefix(parameters, position);
	}

	/**
	 * Return the version-token prefix inside the given literal.
	 *
	 * <p>Version tokens may contain letters, digits, dots, hyphens, underscores,
	 * and plus signs. The returned prefix is the contiguous token fragment between
	 * the token start and the caret offset.
	 * @param parameters the IntelliJ completion parameters.
	 * @param literal the PSI element whose text contains the version token.
	 * @return the version-token prefix before the caret.
	 */
	protected static String getPrefix(CompletionParameters parameters, PsiElement literal) {

		String text = literal.getText();
		int caretInScalar = parameters.getOffset() - literal.getTextRange().getStartOffset();
		caretInScalar = Math.clamp(caretInScalar, 0, text.length());

		int start = caretInScalar;
		while (start > 0 && isVersionTokenCharacter(text.charAt(start - 1))) {
			start--;
		}

		return text.substring(start, caretInScalar);
	}

	private static ProjectDependencyContext context(PsiElement element, PsiFile psiFile) {
		return DependencyAssistantDispatcher.findFirstContext(element.getProject(), psiFile);
	}

	/**
	 * Artifact metadata needed to build release completion suggestions.
	 *
	 * @param artifactReference the resolved artifact whose releases should be
	 * suggested.
	 * @param currentVersion the currently declared version, or {@literal null} if
	 * no current version is available.
	 * @param versionLiteral the PSI element to replace with the selected lookup
	 * string, or {@literal null} if insertion is handled elsewhere.
	 * @param context the dependency context that resolved the artifact.
	 */
	public record CompletionMetadata(ArtifactReference artifactReference, @Nullable ArtifactVersion currentVersion,
			@Nullable PsiElement versionLiteral, ProjectDependencyContext context) {
	}

	public static class LookupElementInsertHandler implements InsertHandler<LookupElement> {

		private final SmartPsiElementPointer<PsiElement> pointer;

		public LookupElementInsertHandler(PsiElement pointer) {
			this.pointer = SmartPointerManager.createPointer(pointer);
		}

		@Override
		public void handleInsert(InsertionContext insertionContext, LookupElement lookupElement) {

			String version = lookupElement.getLookupString();
			PsiElement element = pointer.getElement();

			if (element == null || !element.isValid()) {
				return;
			}

			PsiElement updated = replaceVersion(element, version);
			if (updated == null || !updated.isValid()) {
				return;
			}

			moveCaretTo(insertionContext, getVersionTextEndOffset(updated));
		}

		/**
		 * Move the caret and completion tail to {@code offset} after an insert handler
		 * rewrote the completed declaration, so completion always ends at the applied
		 * version text.
		 */
		public static void moveCaretTo(InsertionContext context, int offset) {

			context.getEditor().getCaretModel().moveToOffset(offset);
			context.setTailOffset(offset);
		}

		private static @Nullable PsiElement replaceVersion(PsiElement versionLiteral, String version) {

			ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(versionLiteral);
			if (manipulator == null) {
				return null;
			}

			TextRange valueTextRange = ElementManipulators.getValueTextRange(versionLiteral);
			return ElementManipulators.handleContentChange(versionLiteral, valueTextRange, version);
		}

		private int getVersionTextEndOffset(PsiElement updated) {

			ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(updated);
			if (manipulator != null) {
				return updated.getTextRange().getStartOffset()
						+ ElementManipulators.getValueTextRange(updated).getEndOffset();
			}

			return updated.getTextRange().getEndOffset();
		}

	}

}
