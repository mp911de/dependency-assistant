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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.gradle.GradleParserSupport.NamedDependencyDeclaration;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PsiPropertyValueElement;
import biz.paluch.dap.util.PsiVisitors;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.ValueArgument;

/**
 * Kotlin DSL specific update helpers. Locates and replaces version values in
 * {@code build.gradle.kts} files.
 *
 * @author Mark Paluch
 */
class UpdateKotlinDsl {

	private final PropertyResolver propertyResolver;

	public UpdateKotlinDsl(PropertyResolver propertyResolver) {
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Applies a version update to a Kotlin DSL file, handling both
	 * {@code extra["key"] = "value"} property assignments and
	 * {@code "group:artifact:version"} string-notation dependency declarations.
	 *
	 * @param file the Kotlin DSL build file.
	 * @param artifact the artifact whose version is being updated.
	 * @param newVersion the new version string.
	 * @param declarationSource the declaration source that describe where the
	 * dependency appears.
	 */
	void updateDeclaration(PsiFile file, ArtifactId artifact, String newVersion,
			DeclarationSource declarationSource) {

		boolean isPlugin = declarationSource instanceof DeclarationSource.Plugin;

		file.accept(PsiVisitors.visitTreeUntil(KtStringTemplateExpression.class, template -> {

			String templateText = KotlinDslUtils.getText(template);

			if (isPlugin) {
				// plugin: id("pluginId") version "oldVersion"
				if (isInsideVersionSuffix(template, artifact.groupId())) {
					template.updateText(newVersion);
					return true;
				}
			}

			GradleDependency dependency = GradleDependency.parse(templateText);
			if (dependency == null || !dependency.getVersionSource().isDefined()
					|| dependency.getVersionSource().isProperty()) {
				return false;
			}

			if (dependency.getId().equals(artifact)) {
				String newGav = GradleUtils.updateGavVersion(templateText, newVersion);
				if (newGav != null) {
					template.updateText(newGav);
					return true;
				}
			}

			return false;
		}));

		if (!isPlugin) {
			updateMapSyntaxDeclaration(file, artifact, newVersion);
		}
	}

	private void updateMapSyntaxDeclaration(PsiFile file, ArtifactId id, String newVersion) {

		file.accept(PsiVisitors.visitTreeUntil(KtCallElement.class, call -> {

			List<? extends ValueArgument> args = call.getValueArguments();
			if (args.isEmpty()) {
				return false;
			}

			NamedDependencyDeclaration entry = KotlinDslParser.parseMapDeclaration(call, propertyResolver);
			if (!entry.isComplete()) {
				return false;
			}

			if (entry.matches(id) && entry.getRequiredVersionLiteral() instanceof KtStringTemplateExpression version) {
				version.updateText(newVersion);
				return true;
			}

			return false;
		}));
	}

	/**
	 * Finds and updates an {@code extra["key"]} assignment in a Kotlin DSL file
	 * (plain string, triple-quoted string, {@code "v".also { extra["k"] = it }}, or
	 * {@code buildString { append("v") }}).
	 *
	 * @return {@code true} if the property was found and updated
	 */
	boolean updateExtraProperty(PsiFile file, String propertyKey, String newVersion) {

		PsiPropertyValueElement element = KotlinDslExtraParser.findExtraPropertyLocation(file, propertyKey);
		if (element == null) {
			return false;
		}

		if (element.element() instanceof KtStringTemplateExpression valueTemplate) {
			valueTemplate.updateText(newVersion);
			return true;
		}
		return false;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns {@code true} if the given string template is the version value in a
	 * {@code id("pluginId") version "x.y.z"} expression, where the plugin ID
	 * matches {@code pluginId}.
	 */
	private static boolean isInsideVersionSuffix(KtStringTemplateExpression template, String pluginId) {
		PsiElement parent = template.getParent();
		if (!(parent instanceof KtBinaryExpression binary)) {
			return false;
		}
		PsiElement[] children = binary.getChildren();
		// children[0] = call expression, children[1] = "version" ref, children[2] =
		// template
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

}
