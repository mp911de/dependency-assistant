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
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.support.VersionedDependencySite;
import biz.paluch.dap.util.PsiVisitors;
import com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
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
	 * @param artifactId the artifact whose version is being updated.
	 * @param newVersion the new version string.
	 */
	void updateDeclaration(PsiFile file, ArtifactId artifactId, String newVersion) {

		file.accept(PsiVisitors.visitTreeUntil(KtCallElement.class, call -> {

			DependencySite site = KotlinDslParser.parseDependencySite(call, propertyResolver);
			if (!(site instanceof VersionedDependencySite versioned) || !site.getArtifactId()
					.equals(artifactId)) {
				return false;
			}

			if (versioned.getVersionElement() instanceof KtStringTemplateExpression template) {
				GradleUtils.updateVersion(KtLiterals.getText(template), newVersion, template::updateText);
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
	 * @return {@literal true} if the property was found and updated.
	 */
	boolean updateExtraProperty(PsiFile file, String propertyKey, String newVersion) {

		PropertyValue element = KotlinDslExtraParser.findExtraPropertyLocation(file, propertyKey);
		if (element == null) {
			return updateValProperty(file, propertyKey, newVersion);
		}

		if (element.getValueLiteral() instanceof KtStringTemplateExpression valueTemplate) {
			valueTemplate.updateText(newVersion);
			return true;
		}
		return false;
	}

	/**
	 * Finds and updates a {@code val key = "value"} or
	 * {@code val key by extra("value")} declaration in a Kotlin DSL file.
	 *
	 * @return {@literal true} if the property was found and updated.
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

}
