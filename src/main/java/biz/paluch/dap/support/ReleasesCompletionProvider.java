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

package biz.paluch.dap.support;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.ProcessingContext;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * {@link CompletionProvider} releases.
 * 
 * @author Mark Paluch
 */
public class ReleasesCompletionProvider extends CompletionProvider<CompletionParameters> {

	private final Function<PsiElement, @Nullable CompletionMetadata> metadataFunction;

	/**
	 * Create a provider using the function that extracts completion metadata from
	 * the completion position.
	 */
	public ReleasesCompletionProvider(Function<PsiElement, @Nullable CompletionMetadata> metadataFunction) {
		this.metadataFunction = metadataFunction;
	}

	@Override
	protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
			CompletionResultSet result) {

		PsiElement position = parameters.getOriginalPosition();
		if (position == null) {
			position = parameters.getPosition();
		}

		CompletionMetadata metadata = metadataFunction.apply(position);
		if (metadata == null) {
			return;
		}

		DependencyAssistantService service = DependencyAssistantService
				.getInstance(parameters.getPosition().getProject());
		List<ArtifactRelease> releases = findOptions(metadata.artifactId(), service.getCache());
		releases.sort(Comparator.reverseOrder());

		if (releases.isEmpty()) {
			return;
		}

		// Run all remaining contributors first and pass their results through
		// unchanged.
		// Collect the lookup strings they contribute so we can skip our own duplicates.
		Set<String> alreadyContributed = getAlreadyContributed(parameters, result);

		// Show all cached versions on a second invocation (Ctrl+Space twice)
		CompletionResultSet versionsResult = getPrefixMatcher(parameters, result);

		List<ArtifactRelease> unique = releases.stream()
				.filter(opt -> !alreadyContributed.contains(opt.release().version().toString())).toList();

		ArtifactVersion currentVersion = metadata.currentVersion();
		double priority = unique.size();

		for (ArtifactRelease option : unique) {

			Release release = option.release();
			LookupElementBuilder element = LookupElementBuilder.create(release.version().toString());

			if (release.releaseDate() != null) {

				Date date = Date.from(release.releaseDate().toLocalDate().atStartOfDay()
						.atZone(ZoneId.systemDefault()).toInstant());
				String pretty = DateFormatUtil.formatPrettyDate(date);
				element = element.withTailText(" (" + pretty + ")", true);
			}

			if (currentVersion != null) {
				element = applyVersion(currentVersion, option.getVersion(), element);
			}

			if (metadata.versionLiteral() != null) {
				element = element
						.withInsertHandler((insertionContext, lookupElement) -> replaceVersion(insertionContext,
								metadata.versionLiteral(), lookupElement.getLookupString()));
			}

			element = postProcess(element, position, option);

			// TODO Elements are sometimes not sorted properly
			versionsResult.addElement(PrioritizedLookupElement.withPriority(element, priority--));
		}
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

	protected LookupElementBuilder postProcess(LookupElementBuilder element, PsiElement position,
			ArtifactRelease option) {
		return element;
	}

	public static @NotNull LookupElementBuilder applyVersion(@NonNull ArtifactVersion currentVersion,
			ArtifactVersion option,
			LookupElementBuilder element) {
		if (option.isNewer(currentVersion)) {
			element = element.bold();
		}
		if (option.isOlder(currentVersion)) {
			element = element.withItemTextItalic(true);
		}

		VersionAge versionAge = VersionAge.fromVersions(currentVersion, option);
		return element.withIcon(versionAge.getIcon());
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

		if (versionLiteral instanceof XmlTag tag) {
			tag.getValue().setText(version);
			return tag;
		}

		ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(versionLiteral);
		if (manipulator == null) {
			return null;
		}

		TextRange valueTextRange = ElementManipulators.getValueTextRange(versionLiteral);
		return ElementManipulators.handleContentChange(versionLiteral, valueTextRange, version);
	}

	private static int getVersionTextEndOffset(PsiElement versionLiteral) {

		if (versionLiteral instanceof XmlTag tag) {
			XmlText[] textElements = tag.getValue().getTextElements();
			if (textElements.length > 0) {
				return textElements[textElements.length - 1].getTextRange().getEndOffset();
			}
		}

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
