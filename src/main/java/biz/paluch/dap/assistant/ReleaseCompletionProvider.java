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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.RuleService;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.VersionUpgradeLookup;
import biz.paluch.dap.util.PsiElements;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
 * for insert handlers, {@link #getRefStyle(PsiElement, CompletionMetadata)} for
 * tag-vs-SHA insertion, and {@link #getReleases(ArtifactId, Cache)} for
 * filtering or ordering the release list.
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
	 * <p>Subclasses that gate completion should usually call
	 * {@code super.addCompletions(...)} once their format-specific preconditions
	 * are satisfied.
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
		StateService service = StateService
				.getInstance(project);
		List<ArtifactRelease> releases = getReleases(metadata.artifactId(), service.getCache());

		if (releases.isEmpty()) {
			return;
		}

		RuleService ruleService = RuleService.getInstance(project);
		DependencyRule rule = ruleService.resolve(metadata.artifactId(), project, parameters.getEditor()
				.getVirtualFile(), metadata.context.getProjectVersion());
		CompletionResultSet versionsResult = getPrefixMatcher(parameters, result);
		ArtifactReleaseRenderer renderer = new ArtifactReleaseRenderer(metadata.context()
				.getInterfaceAssistant(), metadata.currentVersion(), rule);

		List<LookupElement> elements = new ArrayList<>();
		double priority = releases.size();
		for (ArtifactRelease release : releases) {

			ArtifactVersion completionVersion = release.getVersion();
			LookupElementBuilder element = createLookupElement(release, completionVersion, position, refStyle)
					.withRenderer(renderer);

			if (metadata.versionLiteral() != null) {
				element = element
						.withInsertHandler((insertionContext, lookupElement) -> replaceVersion(insertionContext,
								metadata.versionLiteral(), lookupElement.getLookupString()));
			}
			element = postProcess(parameters, element, position, release);
			LookupElement lookupElement = PrioritizedLookupElement.withPriority(
					element.withAutoCompletionPolicy(AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE), priority--);
			elements.add(lookupElement);
		}

		versionsResult.addAllElements(elements);
	}

	private LookupElementBuilder createLookupElement(ArtifactRelease release, ArtifactVersion completionVersion,
			PsiElement position, RefStyle refStyle) {

		String completion;
		Set<String> lookupStrings = new LinkedHashSet<>();
		lookupStrings.add(completionVersion.toString());
		lookupStrings.add(completionVersion.getVersion().toString());
		lookupStrings.add(completionVersion.getVersion().getVersion().toString());

		if (completionVersion instanceof GitVersion gitVersion && supports(position, gitVersion)
				&& StringUtils.hasText(gitVersion.getSha())
				&& refStyle == RefStyle.SHA) {
			completion = gitVersion.getSha();
			lookupStrings.add(gitVersion.getSha());
			lookupStrings.add(gitVersion.getShortSha());
		} else if (completionVersion instanceof GitRef gitRef
				&& StringUtils.hasText(gitRef.getRef())
				&& refStyle == RefStyle.SHA) {
			completion = gitRef.toString();
			lookupStrings.add(gitRef.toString());
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

		return new CompletionMetadata(artifactReference.getArtifactId(), version,
				artifactReference.getDeclaration().getVersionLiteral(), context);
	}

	/**
	 * Return whether the given Git-backed version can be used for SHA insertion at
	 * the current completion position.
	 *
	 * <p>The default accepts every {@link GitVersion}. Override when a contributor
	 * can resolve Git-backed releases but a particular PSI location must still
	 * insert the version text instead of the release SHA.
	 * @param element the PSI element used to resolve completion metadata.
	 * @param gitVersion the Git-backed release version under consideration.
	 * @return {@literal true} if SHA insertion is supported for the given element;
	 * {@literal false} otherwise.
	 */
	protected boolean supports(PsiElement element, GitVersion gitVersion) {
		return true;
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
	 * Return cached release options for the given artifact ordered by version, the
	 * newest version first.
	 */
	private List<ArtifactRelease> getReleases(ArtifactId artifactId, Cache cache) {

		List<Release> versions = cache.getReleases(artifactId);
		List<ArtifactRelease> result = new ArrayList<>();

		for (Release release : versions) {
			result.add(new ArtifactRelease(artifactId, release));
		}

		result.sort(Comparator.naturalOrder());
		return result;
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
	 * <p>The default matcher uses an empty prefix after explicit repeated
	 * completion invocation and otherwise uses
	 * {@link #getPrefix(CompletionParameters)}. Contributors with larger
	 * surrounding syntaxes, such as URLs or dependency notations, can override this
	 * method to calculate the prefix from a format-specific version range.
	 * @param parameters the IntelliJ completion parameters.
	 * @param result the original completion result set.
	 * @return the result set to receive release lookup elements.
	 */
	protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, CompletionResultSet result) {
		return parameters.getInvocationCount() > 1 ? result.withPrefixMatcher("")
				: result.withPrefixMatcher(getPrefix(parameters));
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
	 * Return the version-token prefix at the current completion position.
	 *
	 * <p>The original position is preferred when IntelliJ has inserted completion
	 * placeholder PSI. If no original position is available, the live completion
	 * position is used.
	 * @param parameters the IntelliJ completion parameters.
	 * @return the version-token prefix before the caret; never {@literal null}.
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
	 * @return the version-token prefix before the caret; never {@literal null}.
	 */
	protected static String getPrefix(CompletionParameters parameters, PsiElement literal) {

		String text = literal.getText();
		int caretInScalar = parameters.getOffset() - literal.getTextRange().getStartOffset();
		caretInScalar = Math.clamp(caretInScalar, 0, text.length());

		int start = caretInScalar;
		while (start > 0 && isVersionChar(text.charAt(start - 1))) {
			start--;
		}

		return text.substring(start, caretInScalar);
	}

	private static boolean isVersionChar(char c) {
		return Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == '+';
	}

	private static ProjectDependencyContext context(PsiElement element, PsiFile psiFile) {
		return DependencyAssistantDispatcher.findFirstContext(element.getProject(), psiFile);
	}

	private static void replaceVersion(InsertionContext context, PsiElement versionLiteral, String version) {

		if (!versionLiteral.isValid()) {
			return;
		}

		PsiElement updated = replaceVersion(versionLiteral, version);
		if (updated == null || !updated.isValid()) {
			return;
		}

		context.getEditor().getCaretModel().moveToOffset(getVersionTextEndOffset(updated));
	}

	private static @Nullable PsiElement replaceVersion(PsiElement versionLiteral, String version) {

		/*
		 * if (versionLiteral instanceof XmlTag tag) {
		 *
		 * XmlToken token =
		 * SyntaxTraverser.psiTraverser(tag).filter(XmlToken.class).first(); if (token
		 * != null) { tag.getValue().getTextRange().replace(token.getText(), version); }
		 * else { tag.getValue().setText(version); } return tag; }
		 */
		ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(versionLiteral);
		if (manipulator == null) {
			return null;
		}

		TextRange valueTextRange = ElementManipulators.getValueTextRange(versionLiteral);
		return ElementManipulators.handleContentChange(versionLiteral, valueTextRange, version);
	}

	private static int getVersionTextEndOffset(PsiElement versionLiteral) {

		ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(versionLiteral);
		if (manipulator != null) {
			return versionLiteral.getTextRange().getStartOffset()
					+ ElementManipulators.getValueTextRange(versionLiteral).getEndOffset();
		}

		return versionLiteral.getTextRange().getEndOffset();
	}

	/**
	 * Artifact metadata needed to build release completion suggestions.
	 *
	 * @param artifactId the resolved artifact whose releases should be suggested.
	 * @param currentVersion the currently declared version, or {@literal null} if
	 * no current version is available.
	 * @param versionLiteral the PSI element to replace with the selected lookup
	 * string, or {@literal null} if insertion is handled elsewhere.
	 * @param context the dependency context that resolved the artifact.
	 */
	public record CompletionMetadata(ArtifactId artifactId, @Nullable ArtifactVersion currentVersion,
			@Nullable PsiElement versionLiteral, ProjectDependencyContext context) {

		/**
		 * Create metadata without a default replacement literal.
		 *
		 * <p>Use this constructor when a subclass supplies its own insert handler or
		 * when completion should only contribute lookup elements.
		 * @param artifactId the resolved artifact whose releases should be suggested.
		 * @param currentVersion the currently declared version, or {@literal null} if
		 * no current version is available.
		 * @param context the dependency context that resolved the artifact.
		 */
		public CompletionMetadata(ArtifactId artifactId, @Nullable ArtifactVersion currentVersion,
				ProjectDependencyContext context) {
			this(artifactId, currentVersion, null, context);
		}

	}

}
