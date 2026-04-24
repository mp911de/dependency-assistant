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

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jspecify.annotations.Nullable;
import org.toml.lang.psi.TomlLiteral;

/**
 * Shared completion helpers for Gradle-related files.
 *
 * @author Mark Paluch
 */
abstract class GradleCompletionSupport {

	private GradleCompletionSupport() {
	}

	static @Nullable PsiElement findLookupElement(PsiFile file, @Nullable PsiElement element) {

		if (element == null) {
			return null;
		}

		if (GradleUtils.isGradlePropertiesFile(file)) {
			return PsiTreeUtil.getParentOfType(element, PropertyValueImpl.class, false);
		}

		if (GradleUtils.isVersionCatalog(file)) {
			return PsiTreeUtil.getParentOfType(element, TomlLiteral.class, false);
		}

		if (GradleUtils.isGroovyDsl(file)) {
			return PsiTreeUtil.getParentOfType(element, GroovyPsiElement.class, false);
		}

		if (GradleUtils.KOTLIN_AVAILABLE) {
			return PsiTreeUtil.getParentOfType(element, KtElement.class, false);
		}

		return element;
	}

	static CompletionResultSet versionResultSet(CompletionParameters parameters, CompletionResultSet result,
			PsiFile file, @Nullable PsiElement lookupElement) {

		if (parameters.getInvocationCount() > 1) {
			return result.withPrefixMatcher("");
		}

		if (GradleUtils.isVersionCatalog(file) && lookupElement instanceof TomlLiteral literal) {
			return result.withPrefixMatcher(tomlPrefix(parameters, literal));
		}

		return result;
	}

	static boolean shouldAutoPopup(Project project, Editor editor, PsiFile file, char typedChar) {

		if (!GradleUtils.isGradleFile(file) || !isVersionTypingChar(typedChar)) {
			return false;
		}

		PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
		documentManager.commitDocument(editor.getDocument());

		int offset = editor.getCaretModel().getOffset();
		PsiElement element = offset > 0 ? file.findElementAt(offset - 1) : null;
		if (element == null) {
			element = file.findElementAt(offset);
		}

		PsiElement lookupElement = findLookupElement(file, element);
		if (lookupElement == null) {
			return false;
		}

		VersionUpgradeLookupService lookupService = new VersionUpgradeLookupService(project, file);
		return lookupService.findDependency(lookupElement) != null;
	}

	private static boolean isVersionTypingChar(char typedChar) {
		return Character.isLetterOrDigit(typedChar) || typedChar == '.' || typedChar == '-' || typedChar == '_';
	}

	private static String tomlPrefix(CompletionParameters parameters, TomlLiteral literal) {

		int caret = parameters.getOffset();
		int start = literal.getTextRange().getStartOffset();
		int end = literal.getTextRange().getEndOffset();

		if (caret <= start) {
			return "";
		}

		int relativeEnd = Math.min(caret - start, literal.getTextLength());
		String prefix = literal.getText().substring(0, relativeEnd);
		return prefix.replace("\"", "").replace("'", "");
	}

}
