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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.PsiVisitors;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

/**
 * {@link CompletionProvider} releases.
 *
 * TODO: provide either SHA's and display text or version + display text to
 * avoid messy completion.
 *
 * @author Mark Paluch
 */
public class ReleasesCompletionProvider extends CompletionProvider<CompletionParameters> {

	private static @Nullable ProjectDependencyContext context(PsiElement element) {
		return DependencyAssistantDispatcher.findFirstContext(element.getProject(), element.getContainingFile());
	}

	/**
	 * Return whether the {@code typedChar} is a typical version character such as a
	 * letter, digit or dot.
	 * @param typedChar the character to check.
	 * @return whether the character is a version character.
	 */
	public static boolean isVersionCharacter(char typedChar) {
		return Character.isLetterOrDigit(typedChar) || typedChar == '.';
	}

	@Override
	protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
			CompletionResultSet result) {

		PsiElement position = parameters.getOriginalPosition();
		if (position == null) {
			position = parameters.getPosition();
		}
		position = PsiVisitors.unleaf(position);

		CompletionMetadata metadata = getCompletionMetadata(position);
		if (metadata == null) {
			return;
		}

		RefStyle refStyle = getRefStyle(position, metadata);

		StateService service = StateService
				.getInstance(parameters.getPosition().getProject());
		List<ArtifactRelease> releases = findOptions(metadata.artifactId(), service.getCache());
		releases.sort(Comparator.reverseOrder());

		if (releases.isEmpty()) {
			return;
		}

		// Run all remaining contributors first and pass their results through
		// unchanged. Collect the lookup strings they contribute so we can skip our own
		// duplicates.
		Set<String> alreadyContributed = getAlreadyContributed(parameters, result);

		// TODO: Gradle lookup missing version, TOML, Kotlin completion
		// Show all cached versions on a second invocation (Ctrl+Space twice)
		CompletionResultSet versionsResult = getPrefixMatcher(parameters, result);
		List<ArtifactRelease> unique = releases.stream()
				.filter(opt -> !alreadyContributed.contains(opt.release().version().toString())).toList();

		ArtifactVersion currentVersion = metadata.currentVersion();
		double priority = unique.size();
		ReleaseDateFormatter formatter = ReleaseDateFormatter.create();
		for (ArtifactRelease option : unique) {

			Release release = option.release();
			ArtifactVersion completionVersion = release.version();
			String completion;
			Set<String> lookupStrings = new HashSet<>();

			if (completionVersion instanceof GitVersion gitVersion && StringUtils.hasText(gitVersion.getSha())
					&& refStyle == RefStyle.SHA) {
				completion = gitVersion.getSha();
				lookupStrings.add(gitVersion.getSha());
				lookupStrings.add(gitVersion.getShortSha());
			} else {
				completion = completionVersion.toString();
			}

			lookupStrings.add(completionVersion.toString());
			lookupStrings.add(completionVersion.getVersion().toString());
			lookupStrings.add(completionVersion.getVersion().getVersion().toString());

			LookupElementBuilder element = LookupElementBuilder.create(completion)
					.withPresentableText(completionVersion.getVersion().getVersion().toString())
					.withLookupStrings(lookupStrings);
			String typeText = "";

			LocalDateTime releaseDate = release.releaseDate();
			if (releaseDate != null) {

				Duration age = Duration.between(releaseDate, LocalDateTime.now());
				if (age.toDays() < 5) {
					element = element.withItemTextUnderlined(true);
				}

				typeText = formatter.format(releaseDate);
			}

			if (option.getVersion() instanceof GitVersion gitVersion && StringUtils.hasText(gitVersion.getSha())) {

				typeText = gitVersion.getShortSha() + " " + typeText;
				element = element.withTypeText(typeText.trim(), AllIcons.Vcs.CommitNode, false);
			} else {
				element = element.withTypeText(typeText.trim(), false);
			}

			if (currentVersion != null) {

				if (option.isOlder(currentVersion)) {
					element = element.withItemTextItalic(true);
				}

				VersionAge versionAge = VersionAge.between(currentVersion, completionVersion);
				element = element.withIcon(versionAge.getIcon());
			}

			if (option.getVersion().isPreview()) {
				element = element.withIcon(VersionAge.PREVIEW.getIcon());
			}

			if (metadata.versionLiteral() != null) {
				element = element
						.withInsertHandler((insertionContext, lookupElement) -> replaceVersion(insertionContext,
								metadata.versionLiteral(), lookupElement.getLookupString()));
			}

			element = postProcess(parameters, element, position, option);

			// TODO Elements are sometimes not sorted properly
			versionsResult.addElement(PrioritizedLookupElement.withPriority(
					element.withAutoCompletionPolicy(AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE), priority--));
		}
	}

	protected RefStyle getRefStyle(PsiElement element, CompletionMetadata metadata) {
		return RefStyle.VERSION;
	}

	private @Nullable CompletionMetadata getCompletionMetadata(PsiElement element) {

		ProjectDependencyContext context = context(element);
		if (context == null) {
			return null;
		}

		VersionUpgradeLookupSupport lookup = context.getLookup(element);
		ArtifactReference artifactReference = lookup.resolveArtifactReference(element);
		if (!artifactReference.isResolved()) {
			return null;
		}

		ArtifactVersion version = lookup.getCurrentVersion(artifactReference);
		if (version == null && artifactReference.getDeclaration().isVersionDefined()) {
			version = artifactReference.getDeclaration().getVersion();
		}

		return new CompletionMetadata(artifactReference.getArtifactId(), version,
				artifactReference.getDeclaration().getVersionLiteral());
	}

	private Set<String> getAlreadyContributed(CompletionParameters parameters, CompletionResultSet result) {
		Set<String> alreadyContributed = new HashSet<>();
		result.runRemainingContributors(parameters, completionResult -> {
			alreadyContributed.add(completionResult.getLookupElement().getLookupString());
			result.passResult(completionResult);
		});
		return alreadyContributed;
	}

	/**
	 * Return cached release options for the given artifact.
	 */
	protected List<ArtifactRelease> findOptions(ArtifactId artifactId, Cache cache) {

		List<Release> versions = cache.getReleases(artifactId);
		List<ArtifactRelease> result = new java.util.ArrayList<>();

		for (Release release : versions) {
			result.add(new ArtifactRelease(artifactId, release));
		}

		return result;
	}

	protected LookupElementBuilder postProcess(CompletionParameters parameters, LookupElementBuilder builder,
			PsiElement element, ArtifactRelease option) {
		return builder;
	}

	protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, CompletionResultSet result) {
		return parameters.getInvocationCount() > 1 ? result.withPrefixMatcher("")
				: result;
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
	 */
	public record CompletionMetadata(ArtifactId artifactId, @Nullable ArtifactVersion currentVersion,
			@Nullable PsiElement versionLiteral) {

		public CompletionMetadata(ArtifactId artifactId, @Nullable ArtifactVersion currentVersion) {
			this(artifactId, currentVersion, null);
		}

	}

}
