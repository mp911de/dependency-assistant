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
import biz.paluch.dap.artifact.VersionOption;
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
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
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
 * </p>
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
			Cache cache = DependencyAssistantService.getInstance(project).getState().getCache();

			// Show all cached versions on a second invocation (Ctrl+Space twice)
			CompletionResultSet versionsResult = parameters.getInvocationCount() > 1 ? result.withPrefixMatcher("") : result;

			List<ArtifactVersion> allOptions = findVersions(propertyName, cache);
			if (allOptions.isEmpty()) {
				return;
			}

			double priority = allOptions.size();

			for (ArtifactVersion option : allOptions) {

				String typeText = option.artifactId.toString();
				VersionOption version = option.version();

				LookupElementBuilder element = LookupElementBuilder.create(version.version().toString()).withTypeText(typeText)
						.withBoldness(version.isReleaseVersion() && !version.isPreview());

				if (version.releaseDate() != null) {
					element = element.withTailText(" (" + version.releaseDate().toLocalDate() + ")", true);
				}

				versionsResult.addElement(PrioritizedLookupElement.withPriority(element, priority--));
			}
		}

		private static List<ArtifactVersion> findVersions(String propertyName, Cache cache) {

			Property property = cache.getProperty(propertyName);
			if (property == null) {
				return List.of();
			}

			List<ArtifactVersion> options = new ArrayList<>();
			for (Artifact artifact : property.artifacts()) {
				ArtifactId artifactId = artifact.toArtifactId();
				List<VersionOption> versionOptions = cache.getVersionOptions(artifactId, false);

				for (VersionOption versionOption : versionOptions) {
					options.add(new ArtifactVersion(artifactId, versionOption));
				}

				break;
			}
			options.sort(Comparator.reverseOrder());
			return options;
		}

		record ArtifactVersion(ArtifactId artifactId, VersionOption version) implements Comparable<ArtifactVersion> {

			@Override
			public int compareTo(PropertyVersionCompletionContributor.VersionSuggestionProvider.ArtifactVersion o) {
				return version.compareTo(o.version());
			}
		}
	}
}
