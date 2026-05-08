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

import biz.paluch.dap.assistant.ReleasesCompletionProvider;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ProcessingContext;

/**
 * Completion contributor for Maven release versions.
 *
 * @author Mark Paluch
 */
public class MavenReleaseVersionCompletionContributor extends CompletionContributor {

	private static final PatternCondition<XmlFile> IS_MAVEN_FILE = new PatternCondition<>("isMavenFile") {

		@Override
		public boolean accepts(XmlFile xmlFile, ProcessingContext processingContext) {
			return MavenUtils.isMavenPomFile(xmlFile);
		}

	};

	private static final PsiElementPattern.Capture<PsiElement> DEPENDENCY_VERSION = PlatformPatterns.psiElement() //
			.inside(XmlPatterns.xmlTag().withLocalName("version")
					.inside(PlatformPatterns.or(XmlPatterns.xmlTag().withLocalName("dependency"),
							XmlPatterns.xmlTag().withLocalName("plugin")))
					.inside(XmlPatterns.xmlFile().with(IS_MAVEN_FILE)));

	private static final PsiElementPattern.Capture<PsiElement> PROPERTIES = PlatformPatterns.psiElement() //
			.inside(XmlPatterns.xmlTag().withAncestor(2, XmlPatterns.xmlTag().withName("properties"))
					.inside(XmlPatterns.xmlFile().with(IS_MAVEN_FILE)));

	private static final ReleasesCompletionProvider provider = new ReleasesCompletionProvider();

	public MavenReleaseVersionCompletionContributor() {
		extend(CompletionType.BASIC, DEPENDENCY_VERSION, provider);
		extend(CompletionType.BASIC, PROPERTIES, provider);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return ReleasesCompletionProvider.isVersionCharacter(typeChar)
				&& (PROPERTIES.accepts(position) || DEPENDENCY_VERSION.accepts(position));
	}

}
