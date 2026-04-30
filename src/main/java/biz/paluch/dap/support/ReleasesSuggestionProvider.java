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
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Suggestion provider for version numbers.
 */
public class ReleasesSuggestionProvider extends CompletionProvider<CompletionParameters> {

	private final Function<PsiElement, @Nullable CompletionMetadata> metadataFunction;

	/**
	 * Create a provider using the function that extracts completion metadata from
	 * the completion position.
	 */
	public ReleasesSuggestionProvider(Function<PsiElement, @Nullable CompletionMetadata> metadataFunction) {
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

			element = postProcess(parameters, position, currentVersion, option, versionsResult, element);

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

	protected LookupElementBuilder postProcess(CompletionParameters parameters, PsiElement position,
			@Nullable ArtifactVersion currentVersion, ArtifactRelease option, CompletionResultSet result,
			LookupElementBuilder element) {
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

	/**
	 * Artifact metadata needed to build release completion suggestions.
	 */
	public record CompletionMetadata(ArtifactId artifactId, @Nullable ArtifactVersion currentVersion) {
	}

}
