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

package biz.paluch.dap.assistant.completion;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.state.StateService;
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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

/**
 * Completion provider backed by cached dependency releases.
 * <p>Does not fetch release metadata. Register it at resolvable dependency
 * positions through a completion contributor. Instances can be reused.
 * <p>The first invocation shows curated proposals. Repeated invocation cycles
 * between proposals and full history, preserving release order.
 * <p>Processing contexts belong to synchronous hook invocation and must not be
 * retained. Capture values needed by insert handlers separately.
 *
 * @author Mark Paluch
 */
public class ReleaseCompletionProvider extends CompletionProvider<CompletionParameters> {

	@Override
	protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
			CompletionResultSet result) {

		PsiElement position = parameters.getOriginalPosition();
		if (position == null) {
			position = parameters.getPosition();
		}
		position = PsiElements.unleaf(position);

		ArtifactReferenceContext referenceContext = ArtifactReferenceContext.from(position,
				parameters.getOriginalFile());
		if (referenceContext.isAbsent()) {
			return;
		}

		RefStyle refStyle = getRefStyle(position);
		StateService stateService = referenceContext.getStateService();
		ArtifactId artifactId = referenceContext.getArtifactId();
		PackageIdentity pkg = referenceContext.getPackageIdentity();
		Releases history = referenceContext.getReleases();

		if (history.isEmpty()) {
			result.addLookupAdvertisement(MessageBundle.message("completion.advertisement.no-releases"));
			return;
		}

		DependencyRule rule = referenceContext.getRule();

		CompletionResultSet prefixed = getPrefixMatcher(parameters, context, result);
		List<ArtifactRelease> proposals = proposals(parameters, history, referenceContext.getCurrentVersion(), rule,
				pkg);

		advertiseShowAllReleases(parameters, result, history.size(), proposals.size());

		CompletionResultSet versionsResult = prefixed.withRelevanceSorter(releaseOrderSorter(proposals));
		versionsResult.restartCompletionWhenNothingMatches();

		ArtifactReleaseRenderer renderer = new ArtifactReleaseRenderer(referenceContext.getCurrentVersion(), rule,
				version -> stateService.getVulnerabilities(artifactId, version), referenceContext.getPresentation());

		for (ArtifactRelease release : proposals) {
			renderer.withVersion(release);
		}

		// Keep curated results open so users can discover the full history.
		AutoCompletionPolicy autoCompletionPolicy = proposals.size() < history.size()
				? AutoCompletionPolicy.NEVER_AUTOCOMPLETE
				: AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE;

		PsiElement versionLiteral = referenceContext.getDeclaration().getVersionLiteral();
		List<LookupElement> elements = new ArrayList<>();
		for (ArtifactRelease release : proposals) {

			ArtifactVersion completionVersion = release.getVersion();
			LookupElementBuilder element = createLookupElement(release, completionVersion, refStyle)
					.withRenderer(renderer);

			if (versionLiteral != null) {
				element = element
						.withInsertHandler(new LookupElementInsertHandler(versionLiteral));
			}
			element = postProcess(parameters, context, element, position, release);
			elements.add(element.withAutoCompletionPolicy(autoCompletionPolicy));
		}


		afterCompletion(versionsResult, elements);
	}

	/**
	 * Publish the prepared elements in canonical release order.
	 * <p>The default adds all elements. Overrides that do not delegate must publish
	 * them.
	 */
	protected void afterCompletion(CompletionResultSet result, List<LookupElement> elements) {
		result.addAllElements(elements);
	}

	private static List<ArtifactRelease> proposals(CompletionParameters parameters, Releases history,
			@Nullable ArtifactVersion currentVersion, DependencyRule rule, PackageIdentity pkg) {

		if (showsFullHistory(parameters)) {
			return history.stream().map(release -> new ArtifactRelease(pkg.getArtifactId(), release)).toList();
		}

		// The stem derives from the typed text, not the prefix matcher: several
		// contributors deliberately match with an empty prefix.
		VersionStem stem = VersionStem.from(getPrefix(parameters));
		ReleaseProposals proposals = ReleaseProposals.select(history, currentVersion, stem);

		Release remediation = rule.suggestRemediation(history);
		if (remediation != null) {
			proposals = proposals.with(remediation);
		}

		return proposals.stream().map(release -> new ArtifactRelease(pkg.getArtifactId(), release)).toList();
	}

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

		if (completionVersion instanceof GitVersion gitVersion && gitVersion.hasSha()
				&& refStyle == RefStyle.SHA) {
			completion = gitVersion.getRequiredSha();
			lookupStrings.add(gitVersion.getRequiredSha());
			lookupStrings.add(gitVersion.getRequiredShortSha());
		} else {
			completion = completionVersion.toString();
		}

		return LookupElementBuilder.create(release, completion).withLookupStrings(lookupStrings);
	}

	/**
	 * Choose the inserted reference form. The default uses version text.
	 * <p>{@link RefStyle#SHA} inserts a known Git SHA while retaining version
	 * strings for matching.
	 */
	protected RefStyle getRefStyle(PsiElement element) {
		return RefStyle.VERSION;
	}

	/**
	 * Customize a prepared release item. The default leaves it unchanged.
	 * <p>The builder already has rendering and matching. A default insert handler
	 * is present when the declaration exposes a version literal. Override the
	 * insert handler when the format requires a complete replacement.
	 */
	protected LookupElementBuilder postProcess(CompletionParameters parameters, LookupElementBuilder builder,
			PsiElement element, ArtifactRelease option) {
		return builder;
	}

	/**
	 * Customize an item with invocation-scoped state.
	 * <p>Delegates to
	 * {@link #postProcess(CompletionParameters, LookupElementBuilder, PsiElement, ArtifactRelease)}
	 * by default. Do not retain the processing context.
	 */
	protected LookupElementBuilder postProcess(CompletionParameters parameters, ProcessingContext context,
			LookupElementBuilder builder, PsiElement element, ArtifactRelease option) {
		return postProcess(parameters, builder, element, option);
	}

	/**
	 * Configure version-prefix matching.
	 * <p>The default uses an empty prefix for full history and the typed version
	 * prefix otherwise. Override for versions embedded in larger expressions.
	 */
	protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, CompletionResultSet result) {
		return showsFullHistory(parameters) ? result.withPrefixMatcher("")
				: result.withPrefixMatcher(getPrefix(parameters));
	}

	/**
	 * Configure prefix matching with invocation-scoped state.
	 * <p>Delegates to
	 * {@link #getPrefixMatcher(CompletionParameters, CompletionResultSet)} by
	 * default. Do not retain the processing context.
	 */
	protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, ProcessingContext context,
			CompletionResultSet result) {
		return getPrefixMatcher(parameters, result);
	}

	/**
	 * Return whether this invocation shows full history.
	 * <p>Every second explicit invocation does. Automatic completion uses
	 * proposals.
	 */
	protected static boolean showsFullHistory(CompletionParameters parameters) {

		int invocationCount = parameters.getInvocationCount();
		return invocationCount > 1 && invocationCount % 2 == 0;
	}

	/**
	 * Return whether the character triggers version completion.
	 */
	public static boolean isVersionCharacter(char typedChar) {
		return Character.isLetterOrDigit(typedChar) || typedChar == '.';
	}

	/**
	 * Return whether the character belongs to a version token.
	 * <p>Tokens accept letters, digits, dots, hyphens, underscores, and plus signs.
	 */
	public static boolean isVersionTokenCharacter(char c) {
		return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == '+';
	}

	/**
	 * Return the typed version prefix, preferring original PSI over completion
	 * placeholders.
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
	 * Return the version-token fragment immediately before the caret in the
	 * literal.
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

	/**
	 * Replaces a version literal and moves the caret and completion tail to its
	 * end.
	 * <p>Invalid pointers and missing manipulators leave the document unchanged.
	 */
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
		 * Move the caret and completion tail together after insertion.
		 * @param offset the file-absolute destination.
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
