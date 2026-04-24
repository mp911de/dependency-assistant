/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package biz.paluch.dap.support;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import biz.paluch.dap.SuggestionProviderUtil;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.state.DependencyAssistantService;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

/**
 * Suggestion provider for version numbers.
 */
public class ReleasesSuggestionProvider extends CompletionProvider<CompletionParameters> {

	private final Function<PsiElement, @Nullable CompletionMetadata> metadataFunction;

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
		List<ArtifactRelease> releases = SuggestionProviderUtil.findOptions(metadata.artifactId(), service.getCache());
		releases.sort(Comparator.reverseOrder());

		if (releases.isEmpty()) {
			return;
		}

		// Run all remaining contributors first and pass their results through
		// unchanged.
		// Collect the lookup strings they contribute so we can skip our own duplicates.
		Set<String> alreadyContributed = new HashSet<>();
		result.runRemainingContributors(parameters, completionResult -> {
			alreadyContributed.add(completionResult.getLookupElement().getLookupString());
			result.passResult(completionResult);
		});

		// Show all cached versions on a second invocation (Ctrl+Space twice)
		CompletionResultSet versionsResult = parameters.getInvocationCount() > 1 ? result.withPrefixMatcher("")
				: result;

		List<ArtifactRelease> unique = releases.stream()
				.filter(opt -> !alreadyContributed.contains(opt.release().version().toString())).toList();

		SuggestionProviderUtil.addSuggestions(unique, versionsResult, it -> "", metadata.currentVersion());
	}

	public record CompletionMetadata(ArtifactId artifactId, @Nullable ArtifactVersion currentVersion) {
	}

}
