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
package biz.paluch.dap.maven;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.Property;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.ReleasesSuggestionProvider;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;

import static com.intellij.patterns.PlatformPatterns.*;

/**
 * Provides version auto-completion suggestions when editing a Maven
 * {@code <properties>} value whose property name maps to a known dependency
 * artifact in the {@link Cache}.
 * <p>Invoking completion once ({@code Ctrl+Space}) filters by the typed prefix.
 * Invoking it a second time shows all cached versions regardless of the current
 * text.
 */
public class PropertyVersionCompletionContributor extends CompletionContributor {

	public PropertyVersionCompletionContributor() {
		extend(CompletionType.BASIC, PlatformPatterns.psiElement() //
				.withElementType(XmlTokenType.XML_DATA_CHARACTERS) //
				.and(psiElement().inside(XmlPatterns.xmlFile().withName("pom.xml"))) //
				.and(psiElement().inside(XmlPatterns.xmlTag().withParent(XmlPatterns.xmlTag().withName("properties")))),
				new PropertySuggestionProvider());
	}

	private static class PropertySuggestionProvider extends ReleasesSuggestionProvider {

		public PropertySuggestionProvider() {
			super(element -> {

				XmlTag propertyTag = PomUtil.findPropertyTag(element);
				if (propertyTag == null) {
					return null;
				}

				VersionUpgradeLookupService lookupService = VersionUpgradeLookupService.create(element);
				String propertyName = propertyTag.getLocalName();
				Property property = lookupService.getProperty(propertyName);
				if (property == null || !property.hasArtifacts()) {
					return null;
				}

				ArtifactReference artifactReference = lookupService.resolveArtifactReference(element);
				if (artifactReference.isResolved()) {
					return new CompletionMetadata(artifactReference.getDeclaration().getArtifactId(),
							artifactReference.getDeclaration().getVersion());
				}

				ArtifactVersion version = lookupService.getCurrentVersion(artifactReference.getArtifactId());
				if (version != null) {
					return new CompletionMetadata(artifactReference.getArtifactId(), version);
				}
				CachedArtifact first = property.artifacts().getFirst();
				return new CompletionMetadata(first.toArtifactId(), null);
			});
		}

	}

}
