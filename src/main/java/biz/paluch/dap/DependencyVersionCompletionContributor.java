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
package biz.paluch.dap;

import static com.intellij.patterns.PlatformPatterns.*;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;

import java.util.Comparator;
import java.util.List;

import org.springframework.util.StringUtils;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;

/**
 * Provides version auto-completion suggestions when editing a Maven {@code <version>} value.
 * <p>
 * Invoking completion once ({@code Ctrl+Space}) filters by the typed prefix. Invoking it a second time shows all cached
 * versions regardless of the current text.
 */
public class DependencyVersionCompletionContributor extends CompletionContributor {

	public DependencyVersionCompletionContributor() {
		extend(CompletionType.BASIC, psiElement().withElementType(XmlTokenType.XML_DATA_CHARACTERS) //
				.and(psiElement().inside(XmlPatterns.xmlFile())) //
				.and(psiElement().inside(XmlPatterns.xmlTag().withLocalName("version"))), new VersionSuggestionProvider());
	}

	static class VersionSuggestionProvider extends CompletionProvider<CompletionParameters> {

		@Override
		protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
				CompletionResultSet result) {

			Project project = parameters.getEditor().getProject();
			XmlTag versionTag = XmlUtil.findVersionTag(parameters.getPosition());
			if (project == null || versionTag == null) {
				return;
			}

			VersionUpgradeLookupService service = new VersionUpgradeLookupService(project, parameters.getOriginalFile());

			XmlTag parentTag = versionTag.getParentTag();
			String artifactId = parentTag.getSubTagText("artifactId");
			String groupId = parentTag.getSubTagText("groupId");

			if (StringUtils.hasText(artifactId) && StringUtils.hasText(groupId)) {

				Cache cache = DependencyAssistantService.getInstance(project).getCache();
				ArtifactId artifact = ArtifactId.of(groupId, artifactId);
				ArtifactVersion currentVersion = service.getCurrentVersion(artifact);

				// Show all cached versions on a second invocation (Ctrl+Space twice)
				CompletionResultSet versionsResult = parameters.getInvocationCount() > 1 ? result.withPrefixMatcher("")
						: result;

				List<ArtifactRelease> allOptions = findVersions(artifact,
						cache);
				if (allOptions.isEmpty()) {
					return;
				}

				SuggestionProviderUtil.addSuggestions(allOptions, versionsResult, it -> "", currentVersion);
			}
		}

		private static List<ArtifactRelease> findVersions(ArtifactId artifactId, Cache cache) {

			List<ArtifactRelease> options = SuggestionProviderUtil.findOptions(artifactId, cache);
			options.sort(Comparator.reverseOrder());
			return options;
		}

	}

}
