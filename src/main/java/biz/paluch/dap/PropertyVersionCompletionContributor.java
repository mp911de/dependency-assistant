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
import biz.paluch.dap.state.Artifact;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.Property;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;

/**
 * Provides version auto-completion suggestions when editing a Maven {@code <properties>} value whose property name maps
 * to a known dependency artifact in the {@link Cache}.
 * <p>
 * Invoking completion once ({@code Ctrl+Space}) filters by the typed prefix. Invoking it a second time shows all cached
 * versions regardless of the current text.
 */
public class PropertyVersionCompletionContributor extends CompletionContributor {

	public PropertyVersionCompletionContributor() {
		extend(CompletionType.BASIC, PlatformPatterns.psiElement() //
				.withElementType(XmlTokenType.XML_DATA_CHARACTERS) //
				.and(psiElement().inside(XmlPatterns.xmlFile())) //
				.and(psiElement().inside(XmlPatterns.xmlTag().withParent(XmlPatterns.xmlTag().withName("properties")))),
				new VersionSuggestionProvider());
	}

	private static final class VersionSuggestionProvider extends CompletionProvider<CompletionParameters> {

		@Override
		protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
				CompletionResultSet result) {

			Project project = parameters.getEditor().getProject();
			XmlTag propertyTag = XmlUtil.getPropertyTag(parameters.getPosition());
			if (project == null || propertyTag == null) {
				return;
			}

			String propertyName = propertyTag.getLocalName();
			Cache cache = DependencyAssistantService.getInstance(project).getCache();

			// Show all cached versions on a second invocation (Ctrl+Space twice)
			CompletionResultSet versionsResult = parameters.getInvocationCount() > 1 ? result.withPrefixMatcher("") : result;

			List<SuggestionProviderUtil.ArtifactVersion> allOptions = findVersionsForFirstArtifact(propertyName, cache);
			if (allOptions.isEmpty()) {
				return;
			}

			SuggestionProviderUtil.addSuggestions(allOptions, versionsResult, ArtifactId::toString);
		}

		private static List<SuggestionProviderUtil.ArtifactVersion> findVersionsForFirstArtifact(String propertyName,
				Cache cache) {

			Property property = cache.getProperty(propertyName);
			if (property == null) {
				return List.of();
			}

			List<SuggestionProviderUtil.ArtifactVersion> options = new ArrayList<>();
			for (Artifact artifact : property.artifacts()) {
				options.addAll(SuggestionProviderUtil.findOptions(artifact.toArtifactId(), cache));
				break;
			}
			options.sort(Comparator.reverseOrder());
			return options;
		}

	}
}
