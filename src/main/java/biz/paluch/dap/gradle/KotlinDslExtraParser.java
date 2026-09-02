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

import java.util.HashMap;
import java.util.Map;

import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtProperty;
import org.jetbrains.kotlin.psi.KtScript;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jspecify.annotations.Nullable;

/**
 * Kotlin DSL {@code extra["key"]} parser.
 *
 * @author Mark Paluch
 */
class KotlinDslExtraParser {

	private KotlinDslExtraParser() {
	}

	/**
	 * Parse {@code extra["key"]} property declarations from a Kotlin DSL file to
	 * {@link Property} instances.
	 * @param file the Kotlin build script.
	 * @return a map of property key to its {@link Property}.
	 */
	public static Map<String, Property> parseExtraProperties(PsiFile file) {

		Map<String, Property> result = new HashMap<>();

		SyntaxTraverser.psiTraverser(file)
				.filter(KtBinaryExpression.class)
				.forEach(expression -> {

					KotlinExtraAssignment assignment = KotlinExtraAssignment.from(expression);
					if (assignment != null) {
						result.put(assignment.getKey(), assignment);
					}
				});

		return result;
	}

	/**
	 * Collect {@code extra["key"]} property declarations from a Kotlin DSL file.
	 * @param file the Kotlin build script.
	 * @return a map of property key to literal value.
	 */
	public static Map<String, String> getExtraProperties(PsiFile file) {

		Map<String, String> result = new HashMap<>();
		parseExtraProperties(file).forEach((k, v) -> result.put(k, v.getValue()));
		return result;
	}

	/**
	 * Parse top-level {@code val} declarations from a Kotlin DSL file and return
	 * them as {@link PropertyValue} instances.
	 * @param file the Kotlin build script.
	 * @return a map of variable name to literal value element.
	 */
	public static Map<String, PropertyValue> parseValProperties(PsiFile file) {

		Map<String, PropertyValue> result = new HashMap<>();
		SyntaxTraverser.psiTraverser(file)
				.filter(KtProperty.class)
				.filter(KotlinDslExtraParser::isFileScopedProperty)
				.filterMap(KotlinDslExtraParser::parseValProperty)
				.forEach(it -> result.put(it.getKey(), it));

		return result;
	}

	/**
	 * Locates the PSI element whose text should be updated or highlighted as the
	 * declared value for {@code propertyKey}.
	 */
	public static @Nullable Property findExtraPropertyLocation(PsiFile file, String propertyKey) {

		return SyntaxTraverser.psiTraverser(file)
				.filter(KtBinaryExpression.class)
				.filterMap(KotlinExtraAssignment::from)
				.filter(it -> propertyKey.equals(it.getKey()))
				.first();
	}

	/**
	 * Return {@literal true} when {@code expression} is an
	 * {@code extra["key"] = value} assignment.
	 */
	public static boolean isExtra(@Nullable KtBinaryExpression expression) {
		return KotlinExtraAssignment.from(expression) != null;
	}

	private static boolean isFileScopedProperty(KtProperty property) {
		return PsiTreeUtil.getParentOfType(property, KtDeclaration.class) instanceof KtScript;
	}

	/**
	 * Parse {@code val x = "1.0"} and {@code val x by extra("1.0")}.
	 */
	private static @Nullable PropertyValue parseValProperty(KtProperty property) {

		String name = property.getName();
		KtExpression value = property.getInitializer();

		if (value == null && property.getDelegateExpression() instanceof KtCallExpression delegate
				&& "extra".equals(KotlinDslUtils.getKotlinCallName(delegate))) {
			value = KotlinDslUtils.getFirstValueArgument(delegate);
		}

		if (StringUtils.isEmpty(name) || !(value instanceof KtStringTemplateExpression template)) {
			return null;
		}

		String text = KtLiterals.getText(template);
		return StringUtils.hasText(text) ? new PropertyValue(name, text, template) : null;
	}

}
