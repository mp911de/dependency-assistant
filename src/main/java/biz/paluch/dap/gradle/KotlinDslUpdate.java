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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.VersionSource;

import java.util.Collection;

import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jspecify.annotations.Nullable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;

/**
 * Kotlin DSL specific update helpers. Locates and replaces version values in {@code build.gradle.kts} files.
 *
 * @author Mark Paluch
 */
class KotlinDslUpdate {

	private static final Logger LOG = Logger.getInstance(KotlinDslUpdate.class);

	/**
	 * Applies a version update to a Kotlin DSL file, handling both {@code extra["key"] = "value"} property assignments
	 * and {@code "group:artifact:version"} string-notation dependency declarations.
	 *
	 * @param file the Kotlin DSL build file
	 * @param coordinate the artifact whose version is being updated
	 * @param newVersion the new version string
	 * @param versionSources the version sources that describe how the version is declared
	 * @param declarationSources the declaration sources that describe where the dependency appears
	 */
	static void applyKotlinUpdate(PsiFile file, ArtifactId coordinate, String newVersion,
			Collection<VersionSource> versionSources, Collection<DeclarationSource> declarationSources) {

		boolean isPlugin = declarationSources.stream().anyMatch(s -> s instanceof DeclarationSource.Plugin);
		String groupId = coordinate.groupId();
		String artifactId = coordinate.artifactId();

		file.accept(new PsiRecursiveElementVisitor() {
			@Override
			public void visitElement(PsiElement element) {
				super.visitElement(element);

				if (!(element instanceof KtStringTemplateExpression template)) {
					return;
				}

				String templateText = stripQuotes(template.getText());
				if (templateText == null) {
					return;
				}

				if (isPlugin) {
					// plugin: id("pluginId") version "oldVersion"
					if (isInsideVersionSuffix(template, groupId)) {
						replaceTemplateText(template, newVersion);
					}
					return;
				}

				// dependency GAV: "group:artifact:version"
				String[] parts = templateText.split(":");
				if (parts.length >= 3 && groupId.equals(parts[0].trim()) && artifactId.equals(parts[1].trim())
						&& !parts[2].contains("$")) {
					String newGav = GradleUtils.updateGavVersion(templateText, newVersion);
					if (newGav != null) {
						replaceTemplateText(template, newGav);
					}
				}
			}
		});
	}

	/**
	 * Finds and updates an {@code extra["key"]} assignment in a Kotlin DSL file (plain string, triple-quoted string,
	 * {@code "v".also { extra["k"] = it }}, or {@code buildString { append("v") }}).
	 *
	 * @return {@code true} if the property was found and updated
	 */
	static boolean updateExtraProperty(PsiFile file, String propertyKey, String newVersion) {

		KtBinaryExpression assign = KotlinDslExtraSupport.findExtraAssignment(file, propertyKey);
		if (assign == null) {
			return false;
		}
		KtExpression right = assign.getRight();
		if (right instanceof KtStringTemplateExpression valueTemplate) {
			replaceTemplateText(valueTemplate, newVersion);
			return true;
		}
		if (right instanceof KtNameReferenceExpression ref && "it".equals(ref.getReferencedName())) {
			KtStringTemplateExpression receiver = KotlinDslExtraSupport.findAlsoReceiverStringTemplate(ref);
			if (receiver != null) {
				replaceTemplateText(receiver, newVersion);
				return true;
			}
		}
		if (right instanceof KtCallExpression buildStringCall) {
			KtStringTemplateExpression appendLit = KotlinDslExtraSupport.findBuildStringAppendLiteral(buildStringCall);
			if (appendLit != null) {
				replaceTemplateText(appendLit, newVersion);
				return true;
			}
		}
		return false;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns {@code true} if the given string template is the version value in a {@code id("pluginId") version "x.y.z"}
	 * expression, where the plugin ID matches {@code pluginId}.
	 */
	private static boolean isInsideVersionSuffix(KtStringTemplateExpression template, String pluginId) {
		PsiElement parent = template.getParent();
		if (!(parent instanceof KtBinaryExpression binary)) {
			return false;
		}
		PsiElement[] children = binary.getChildren();
		// children[0] = call expression, children[1] = "version" ref, children[2] = template
		if (children.length < 3) {
			return false;
		}
		if (template != children[children.length - 1]) {
			return false;
		}
		// Verify the call expression contains the expected plugin id
		PsiElement callPart = children[0];
		if (callPart instanceof KtCallExpression callExpr) {
			String callText = callExpr.getText();
			return callText.contains(pluginId);
		}
		return false;
	}

	/**
	 * Replaces the content of a {@link KtStringTemplateExpression} with {@code newContent}, preserving the quote style.
	 */
	private static void replaceTemplateText(KtStringTemplateExpression template, String newContent) {
		String text = template.getText();
		if (text == null || text.length() < 2) {
			return;
		}
		String newText;
		if (text.length() >= 6 && text.startsWith("\"\"\"") && text.endsWith("\"\"\"")) {
			newText = "\"\"\"" + newContent + "\"\"\"";
		} else {
			char quote = text.charAt(0);
			newText = quote + newContent + quote;
		}
		try {
			// Use the document to perform a text-level replacement; this keeps offsets valid
			// because we immediately proceed to the next element (early return after found[0] = true).
			com.intellij.openapi.editor.Document doc = com.intellij.psi.PsiDocumentManager.getInstance(template.getProject())
					.getDocument(template.getContainingFile());
			if (doc != null) {
				com.intellij.openapi.util.TextRange range = template.getTextRange();
				doc.replaceString(range.getStartOffset(), range.getEndOffset(), newText);
			}
		} catch (Exception e) {
			LOG.warn("Failed to replace Kotlin string template in " + template.getContainingFile().getName(), e);
		}
	}

	/**
	 * Strips the surrounding quotes from a Kotlin string template's text representation. Returns {@code null} if the text
	 * is not a quoted string literal.
	 */
	static @Nullable String stripQuotes(@Nullable String text) {
		if (text == null || text.length() < 2) {
			return null;
		}
		if (text.length() >= 6 && text.startsWith("\"\"\"") && text.endsWith("\"\"\"")) {
			String inner = text.substring(3, text.length() - 3);
			if (inner.startsWith("\n")) {
				inner = inner.substring(1);
			}
			if (inner.endsWith("\n")) {
				inner = inner.substring(0, inner.length() - 1);
			}
			return inner;
		}
		char open = text.charAt(0);
		char close = text.charAt(text.length() - 1);
		if ((open == '"' && close == '"') || (open == '\'' && close == '\'')) {
			return text.substring(1, text.length() - 1);
		}
		return null;
	}

}
