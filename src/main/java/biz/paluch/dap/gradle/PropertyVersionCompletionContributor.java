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
package biz.paluch.dap.gradle;

import static com.intellij.patterns.PlatformPatterns.*;

import biz.paluch.dap.SuggestionProviderUtil;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.state.DependencyAssistantService;

import java.util.Comparator;
import java.util.List;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;

/**
 * Completion contributor that suggests available versions for property values in {@code gradle.properties} files when
 * the property key maps to a known dependency artifact.
 *
 * @author Mark Paluch
 */
public class PropertyVersionCompletionContributor extends CompletionContributor {

	public PropertyVersionCompletionContributor() {
		extend(CompletionType.BASIC, psiElement(), new PropertyVersionCompletionContributor.VersionSuggestionProvider());
	}

	static class VersionSuggestionProvider extends CompletionProvider<CompletionParameters> {

		@Override
		protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
				CompletionResultSet result) {

			PsiFile file = parameters.getOriginalFile();
			if (!GradleUtils.isGradlePropertiesFile(file.getVirtualFile())) {
				return;
			}

			PsiElement element = parameters.getOriginalPosition();
			if (element == null) {
				element = parameters.getPosition();
			}

			VersionUpgradeLookupService lookupService = new VersionUpgradeLookupService(file.getProject(), file);
			Dependency dependency = lookupService.findDependency(element);
			if (dependency == null) {
				return;
			}

			Project project = file.getProject();
			DependencyAssistantService service = DependencyAssistantService.getInstance(project);

			// Show all cached versions on a second invocation (Ctrl+Space twice)
			CompletionResultSet versionsResult = parameters.getInvocationCount() > 1 ? result.withPrefixMatcher("") : result;

			List<ArtifactRelease> options = SuggestionProviderUtil.findOptions(dependency.getArtifactId(),
					service.getCache());
			if (options.isEmpty()) {
				return;
			}
			options.sort(Comparator.reverseOrder());

			SuggestionProviderUtil.addSuggestions(options, versionsResult, id -> "", null);
		}
	}

}
