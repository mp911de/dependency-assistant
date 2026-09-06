/*
 * Copyright 2026-present the original author or authors.
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
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.PsiElements;
import com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.ValueArgument;

/**
 * Locates and rewrites version values in Kotlin DSL Gradle scripts.
 *
 * @author Mark Paluch
 */
class UpdateKotlinDsl {

	private final PropertyResolver propertyResolver;

	UpdateKotlinDsl(PropertyResolver propertyResolver) {
		this.propertyResolver = propertyResolver;
	}

	/**
	 * Update the parsed version source of the matching artifact.
	 * <p>The editable version may belong to another build file.
	 */
	void updateDeclaration(PsiFile file, ArtifactId artifactId, String newVersion) {

		KotlinDslFileParser parser = new KotlinDslFileParser(file, propertyResolver);

		file.accept(PsiElements.visitTreeUntil(KtCallElement.class, call -> {

			ArtifactDeclaration declaration = parser.parse(call);
			if (declaration == null || !declaration.getArtifactId().equals(artifactId)) {
				return false;
			}

			if (declaration.getVersionLiteral() instanceof KtStringTemplateExpression template) {
				updateVersion(template, newVersion);
				return true;
			}

			return false;
		}));
	}

	public static void updateVersion(KtStringTemplateExpression template, String newVersion) {
		GradleUtils.updateVersion(KtLiterals.getText(template), newVersion, template::updateText);
	}

	/**
	 * Update an extra property, falling back to a local property if absent.
	 * @return whether a property was updated.
	 */
	public static boolean updateExtraProperty(PsiFile file, String propertyKey, String newVersion) {

		Property element = KotlinDslExtraParser.findExtraPropertyLocation(file, propertyKey);
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
	 * Update a literal or {@code by extra(...)} property.
	 * @return whether a property was updated.
	 */
	static boolean updateValProperty(PsiFile file, String propertyKey, String newVersion) {

		boolean[] updated = {false};
		file.accept(PsiElements.visitTreeUntil(KtProperty.class, property -> {
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
