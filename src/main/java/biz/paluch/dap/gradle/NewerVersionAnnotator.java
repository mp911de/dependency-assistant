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

import biz.paluch.dap.NewerVersionSeveritiesProvider;
import biz.paluch.dap.maven.NewerVersionLineMarkerProvider;
import biz.paluch.dap.support.NewerVersionAnnotatorSupport;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Annotator that highlights outdated version strings in Gradle build files.
 * <p>
 * Complements {@link NewerVersionLineMarkerProvider}: the gutter icon provides a click target while this annotation
 * draws the reader's eye directly to the outdated version string in the editor.
 *
 * @author Mark Paluch
 */
public class NewerVersionAnnotator extends NewerVersionAnnotatorSupport {

	public NewerVersionAnnotator() {
		super(UpdateGradleDependenciesIntention.INSTANCE, NewerVersionSeveritiesProvider.NEWER_VERSION_GRADLE);
	}

	@Override
	protected VersionUpgradeLookupSupport getVersionLookupSupport(PsiElement element) {
		return new VersionUpgradeLookupService(element.getProject(), element.getContainingFile());
	}

	@Override
	protected TextRange getTextRange(PsiElement element) {
		return getRange(element);
	}

	static TextRange getRange(PsiElement element) {

		TextRange textRange = element.getTextRange();

		// strip quotes, TOML elements always contain quotes
		if (GradleUtils.isVersionCatalog(element.getContainingFile().getVirtualFile())
				&& element.getText().startsWith("\"")) {
			return new TextRange(textRange.getStartOffset() + 1, textRange.getEndOffset() - 1);
		}
		return textRange;
	}

}
