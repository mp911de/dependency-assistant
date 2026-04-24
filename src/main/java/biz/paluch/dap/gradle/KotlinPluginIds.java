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

import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jspecify.annotations.Nullable;

/**
 * Factory for {@link PluginId} instances parsed from Kotlin DSL plugin
 * declarations.
 * <p>Supports the conventional infix shape:
 *
 * <pre class="code">
 * id("org.springframework.boot") version "3.3.2"
 * </pre>
 *
 * Where the {@code id(...)} call is the inner {@link KtCallElement} and the
 * surrounding {@link KtBinaryExpression} carries the {@code version} operation
 * and version string template.
 *
 * @author Mark Paluch
 */
final class KotlinPluginIds {

	private KotlinPluginIds() {
	}

	/**
	 * Parse a Kotlin plugin declaration anchored at {@code call} and surrounded by
	 * {@code be}. Returns {@literal null} when the call is not a recognised plugin
	 * declaration shape, when the resolved id is invalid, or when no version is
	 * declared.
	 *
	 * @param call the inner {@code id(...)} call element.
	 * @param be the enclosing binary expression carrying the {@code version}
	 * keyword and version literal; {@literal null} if absent.
	 * @param scriptProperties property resolver used to resolve interpolated id
	 * placeholders.
	 * @return the parsed plugin declaration, or {@literal null} when the call is
	 * not a complete plugin declaration.
	 */
	static @Nullable PluginId fromBinary(KtCallElement call, @Nullable KtBinaryExpression be,
			PropertyResolver scriptProperties) {

		StringBuilder pluginId = new StringBuilder();
		boolean[] failed = new boolean[1];

		KotlinDslUtils.doWithStrings(call, pluginId::append, expr -> {
			String resolved = KotlinDslUtils.resolveKotlinExpression(expr, scriptProperties);
			if (resolved == null) {
				failed[0] = true;
			} else {
				pluginId.append(resolved);
			}
		});

		if (failed[0]) {
			return null;
		}

		String id = scriptProperties.resolvePlaceholders(pluginId.toString());
		if (StringUtils.isEmpty(id)) {
			return null;
		}

		String versionText = null;
		String versionPropertyKey = null;
		PsiElement versionElement = null;

		if (be != null) {
			PsiElement[] children = be.getChildren();
			for (int i = 0; i < children.length; i++) {
				PsiElement child = children[i];
				if (child instanceof KtOperationReferenceExpression ops && "version".equals(ops.getReferencedName())
						&& children.length > i + 1
						&& children[i + 1] instanceof KtStringTemplateExpression versionExpr) {

					KtLiterals literals = KtLiterals.from(versionExpr);
					if (!literals.hasText()) {
						return null;
					}

					versionElement = versionExpr;
					versionText = literals.toString();
					if (literals.hasProperty() && literals.size() == 1) {
						versionPropertyKey = literals.getProperty();
						PropertyValue resolvedProperty = scriptProperties.getPropertyValue(versionPropertyKey);
						if (resolvedProperty != null && resolvedProperty.element() != null) {
							versionElement = resolvedProperty.element();
						}
					}
					break;
				}
			}
		}

		if (!StringUtils.hasText(versionText) || versionElement == null) {
			return null;
		}

		return new PluginId(call, versionElement, id, versionText, versionPropertyKey);
	}

}
