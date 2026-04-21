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
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtLambdaArgument;
import org.jetbrains.kotlin.psi.KtLambdaExpression;
import org.jetbrains.kotlin.psi.KtProperty;
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

	UpdateKotlinDsl(PropertyResolver propertyResolver) {
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
			updateVersionBlock(file, artifact, newVersion);
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

	private void updateVersionBlock(PsiFile file, ArtifactId id, String newVersion) {

		file.accept(PsiVisitors.visitTreeUntil(KtCallElement.class, call -> {

			KtStringTemplateExpression gavTemplate = null;
			for (ValueArgument arg : call.getValueArguments()) {
				if (arg instanceof KtLambdaArgument) {
					continue;
				}
				if (!(arg.getArgumentExpression() instanceof KtStringTemplateExpression st)) {
					continue;
				}
				String text = KotlinDslUtils.getText(st);
				if (text == null) {
					continue;
				}
				String[] parts = text.split(":");
				if (parts.length == 2 && id.groupId().equals(parts[0]) && id.artifactId().equals(parts[1])) {
					gavTemplate = st;
					break;
				}
			}

			if (gavTemplate == null) {
				return false;
			}

			KtLambdaExpression trailingLambda = null;
			for (ValueArgument arg : call.getValueArguments()) {
				if (arg instanceof KtLambdaArgument la
						&& la.getArgumentExpression() instanceof KtLambdaExpression lam) {
					trailingLambda = lam;
					break;
				}
			}

			if (trailingLambda == null || trailingLambda.getBodyExpression() == null) {
				return false;
			}

			KtCallExpression versionCall = null;
			for (KtCallExpression inner : PsiTreeUtil.collectElementsOfType(trailingLambda.getBodyExpression(),
					KtCallExpression.class)) {
				if ("version".equals(KotlinDslUtils.getKotlinCallName(inner))) {
					versionCall = inner;
					break;
				}
			}

			if (versionCall == null) {
				return false;
			}

			KtLambdaExpression versionLambda = null;
			for (ValueArgument arg : versionCall.getValueArguments()) {
				if (arg instanceof KtLambdaArgument la
						&& la.getArgumentExpression() instanceof KtLambdaExpression lam) {
					versionLambda = lam;
					break;
				}
			}

			if (versionLambda == null || versionLambda.getBodyExpression() == null) {
				return false;
			}

			KtStringTemplateExpression preferTemplate = null;
			KtStringTemplateExpression strictlyTemplate = null;

			for (KtCallExpression inner : PsiTreeUtil.collectElementsOfType(versionLambda.getBodyExpression(),
					KtCallExpression.class)) {
				String name = KotlinDslUtils.getKotlinCallName(inner);
				for (ValueArgument va : inner.getValueArguments()) {
					if (va.getArgumentExpression() instanceof KtStringTemplateExpression st) {
						if (GradleVersionConstraint.PREFER.equals(name) && preferTemplate == null) {
							preferTemplate = st;
						} else if (GradleVersionConstraint.STRICTLY.equals(name) && strictlyTemplate == null) {
							strictlyTemplate = st;
						}
					}
				}
			}

			KtStringTemplateExpression target;
			if (preferTemplate != null) {
				target = preferTemplate;
			} else if (strictlyTemplate != null
					&& !GradleUtils.isVersionRange(KotlinDslUtils.getText(strictlyTemplate))) {
				target = strictlyTemplate;
			} else {
				return false;
			}

			target.updateText(newVersion);
			return true;
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
			return updateValProperty(file, propertyKey, newVersion);
		}

		if (element.element() instanceof KtStringTemplateExpression valueTemplate) {
			valueTemplate.updateText(newVersion);
			return true;
		}
		return false;
	}

	/**
	 * Finds and updates a {@code val key = "value"} or
	 * {@code val key by extra("value")} declaration in a Kotlin DSL file.
	 *
	 * @return {@code true} if the property was found and updated
	 */
	boolean updateValProperty(PsiFile file, String propertyKey, String newVersion) {

		boolean[] updated = {false};
		file.accept(PsiVisitors.visitTreeUntil(KtProperty.class, property -> {
			if (!propertyKey.equals(property.getName())) {
				return false;
			}
			if (property.getInitializer() instanceof KtStringTemplateExpression template) {
				template.updateText(newVersion);
				updated[0] = true;
				return true;
			}
			if (property.hasDelegateExpression()
					&& property.getDelegateExpression() instanceof KtCallExpression delegateCall
					&& "extra".equals(KotlinDslUtils.getKotlinCallName(delegateCall))) {
				for (ValueArgument va : delegateCall.getValueArguments()) {
					if (va.getArgumentExpression() instanceof KtStringTemplateExpression argTemplate) {
						argTemplate.updateText(newVersion);
						updated[0] = true;
						return true;
					}
				}
			}
			return false;
		}));
		return updated[0];
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
		if (binary.getRight() != template) {
			return false;
		}
		if (!(binary.getLeft() instanceof KtCallExpression callExpr)) {
			return false;
		}
		for (ValueArgument va : callExpr.getValueArguments()) {
			if (va.getArgumentExpression() instanceof KtStringTemplateExpression st
					&& pluginId.equals(KotlinDslUtils.getText(st))) {
				return true;
			}
		}
		return false;
	}

}
