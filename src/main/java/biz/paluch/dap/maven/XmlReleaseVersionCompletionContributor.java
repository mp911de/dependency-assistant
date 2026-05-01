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

import biz.paluch.dap.support.ReleasesCompletionProvider;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import org.jspecify.annotations.NonNull;

/**
 * Completion contributor for Maven release versions.
 * 
 * @author Mark Paluch
 */
public class XmlReleaseVersionCompletionContributor extends CompletionContributor {

	private static final ReleasesCompletionProvider provider = new ReleasesCompletionProvider(
			ReleasesCompletionProvider.resolver());

	public XmlReleaseVersionCompletionContributor() {

		PatternCondition<XmlFile> isMavenFile = new PatternCondition<>("isMavenFile") {

			@Override
			public boolean accepts(@NonNull XmlFile xmlFile, ProcessingContext processingContext) {
				return MavenUtils.isMavenPomFile(xmlFile);
			}

		};

		extend(CompletionType.BASIC, PlatformPatterns.psiElement().withElementType(XmlTokenType.XML_DATA_CHARACTERS) //
				.inside(XmlPatterns.xmlFile().with(isMavenFile)) //
				.inside(XmlPatterns.xmlTag().withLocalName("version")), provider);

		extend(CompletionType.BASIC, PlatformPatterns.psiElement() //
				.withElementType(XmlTokenType.XML_DATA_CHARACTERS) //
				.inside(XmlPatterns.xmlFile().with(isMavenFile)) //
				.inside(XmlPatterns.xmlTag().withParent(XmlPatterns.xmlTag().withName("properties"))), provider);
	}

}
