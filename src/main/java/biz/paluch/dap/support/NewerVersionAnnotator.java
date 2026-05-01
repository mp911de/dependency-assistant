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

package biz.paluch.dap.support;

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.NewerVersionSeveritiesProvider;
import biz.paluch.dap.ProjectDependencyContext;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Annotator that marks outdated dependency versions in supported build files.
 *
 * @author Mark Paluch
 */
public class NewerVersionAnnotator implements Annotator {

	@Override
	public void annotate(PsiElement element, AnnotationHolder holder) {

		VersionUpgradeLookupSupport service = getVersionLookupSupport(element);
		if (service == null) {
			return;
		}

		UpgradeSuggestion suggestion = service.suggestUpgrade(element);

		if (!suggestion.isPresent()) {
			return;
		}

		// TODO: Gutter text vs. file problems summary
		IntentionAction action = UpgradeDependenciesIntention.INSTANCE;
		String message = suggestion.getMessage();
		ArtifactDeclaration declaration = suggestion.getArtifactDeclaration();
		if (!declaration.isVersionDefinedInSameFile()) {

			PsiElement versionLiteral = declaration.getVersionLiteral();

			if (versionLiteral != null && versionLiteral.getContainingFile() != null) {
				VirtualFile virtualFile = versionLiteral.getContainingFile().getVirtualFile();
				if (virtualFile != null) {

					message = MessageBundle.message("gutter.declaration.file", virtualFile.getName())
							+ System.lineSeparator()
							+ message;
					action = null;
				}
			}
		}

		AnnotationBuilder builder = holder.newAnnotation(NewerVersionSeveritiesProvider.NEWER_VERSION, message)
				.range(getTextRange(element))
				.textAttributes(NewerVersionSeveritiesProvider.NEWER_VERSION_KEY);

		if (action != null) {
			builder = builder.withFix(action);
		}

		builder.create();
	}

	/**
	 * Return lookup support for the given element, if the containing file is
	 * supported.
	 */
	protected @Nullable VersionUpgradeLookupSupport getVersionLookupSupport(PsiElement element) {

		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(element.getProject(),
				element.getContainingFile());

		if (context == null || !context.isVersionElement(element) || context.isAbsent()) {
			return null;
		}

		return context.getLookup(element);
	}

	/**
	 * Return the text range used for the annotation highlight.
	 */
	protected TextRange getTextRange(PsiElement element) {

		TextRange textRange = element.getTextRange();
		if (element.getContainingFile().getName().endsWith(".versions.toml") && element.getText().startsWith("\"")) {
			return new TextRange(textRange.getStartOffset() + 1, textRange.getEndOffset() - 1);
		}
		return textRange;
	}

}
