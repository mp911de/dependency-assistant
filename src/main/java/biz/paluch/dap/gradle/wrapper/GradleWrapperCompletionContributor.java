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

package biz.paluch.dap.gradle.wrapper;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiFilePattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * IntelliJ completion contributor for Gradle wrapper property files.
 *
 * @author Mark Paluch
 */
class GradleWrapperCompletionContributor extends CompletionContributor {

	private static final PsiFilePattern.Capture<PsiFile> WRAPPER_PROPERTIES = PlatformPatterns.psiFile()
			.withName(GradleWrapperUtils.WRAPPER_FILENAME);

	private static final PsiElementPattern.Capture<PsiElement> VALUE_PATTERNS = PropertyContributor.PATTERNS
			.inFile(WRAPPER_PROPERTIES);

	private static final PropertyContributor VALUE_CONTRIBUTOR = new PropertyContributor();

	private static final VersionContributor VERSION_CONTRIBUTOR = new VersionContributor();

	private static final PsiElementPattern.Capture<PsiElement> VERSION_PATTERNS = VersionContributor.WRAPPER_VERSION
			.inFile(WRAPPER_PROPERTIES);

	GradleWrapperCompletionContributor() {
		extend(CompletionType.BASIC, VALUE_PATTERNS, VALUE_CONTRIBUTOR);
		extend(CompletionType.BASIC, VERSION_PATTERNS, VERSION_CONTRIBUTOR);
	}

	// TODO: migrate to TypedHandlerDelegate
	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		if (VALUE_PATTERNS.accepts(position) && VALUE_CONTRIBUTOR.invokeAutoPopup(position, typeChar)) {
			return true;
		}
		if (VERSION_PATTERNS.accepts(position) && VERSION_CONTRIBUTOR.invokeAutoPopup(position, typeChar)) {
			return true;
		}
		return false;
	}

}
