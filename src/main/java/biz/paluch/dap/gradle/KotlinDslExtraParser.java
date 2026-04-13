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

import java.util.HashMap;
import java.util.Map;

import biz.paluch.dap.support.PsiPropertyValueElement;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.kotlin.psi.KtArrayAccessExpression;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
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
	 * Parses {@code extra["key"]} property declarations from a Kotlin DSL file to
	 * {@link PsiPropertyValueElement} for declarations of the form:
	 * <ul>
	 * <li>{@code extra["key"] = "value"} and
	 * {@code extra["key"] = """value"""}</li>
	 * <li>{@code "value".also { extra["key"] = it }}</li>
	 * <li>{@code extra["key"] = buildString { append("value") }}</li>
	 * </ul>
	 * <p>This is the Kotlin DSL equivalent of {@code gradle.properties}: versions
	 * can be declared inline in {@code build.gradle.kts} and referenced via
	 * {@code property("key")} or {@code extra["key"]}.
	 *
	 * @param file the Kotlin build script ({@code .kts})
	 * @return a map of property key to literal value.
	 */
	public static Map<String, PsiPropertyValueElement> parseExtraProperties(PsiFile file) {

		Map<String, PsiPropertyValueElement> result = new HashMap<>();

		SyntaxTraverser.psiTraverser(file)
				.filter(KtBinaryExpression.class)
				.filter(KotlinDslExtraParser::isExtra)
				.forEach(it -> {

					PsiPropertyValueElement element = parseExtra(it);
					if (element != null) {
						result.put(element.propertyKey(), element);
					}
				});

		return result;
	}

	/**
	 * Collects {@code extra["key"]} property declarations from a Kotlin DSL file,
	 * to a map of property key to literal value, for declarations of the form:
	 * <ul>
	 * <li>{@code extra["key"] = "value"} and
	 * {@code extra["key"] = """value"""}</li>
	 * <li>{@code "value".also { extra["key"] = it }}</li>
	 * <li>{@code extra["key"] = buildString { append("value") }}</li>
	 * </ul>
	 * <p>This is the Kotlin DSL equivalent of {@code gradle.properties}: versions
	 * can be declared inline in {@code build.gradle.kts} and referenced via
	 * {@code property("key")} or {@code extra["key"]}.
	 *
	 * @param file the Kotlin build script ({@code .kts})
	 * @return a map of property key to literal value.
	 */
	public static Map<String, String> getExtraProperties(PsiFile file) {

		Map<String, String> result = new HashMap<>();

		SyntaxTraverser.psiTraverser(file)
				.filter(KtBinaryExpression.class)
				.filter(KotlinDslExtraParser::isExtra)
				.forEach(it -> {

					PsiPropertyValueElement element = parseExtra(it);
					if (element != null) {
						result.put(element.propertyKey(), element.propertyValue());
					}
				});

		return result;
	}

	/**
	 * If {@code expr} assigns a resolvable literal to {@code extra["key"]}, returns
	 * that key-value pair.
	 */
	private static @Nullable PsiPropertyValueElement parseExtra(KtBinaryExpression expr) {

		KtStringTemplateExpression keyTemplate = getKeyAssignment(expr);
		if (keyTemplate == null) {
			return null;
		}
		String key = KotlinDslUtils.getText(keyTemplate);
		if (StringUtils.isEmpty(key)) {
			return null;
		}

		return getValueElement(expr.getRight(), key);
	}

	private static @Nullable KtStringTemplateExpression getKeyAssignment(KtBinaryExpression expression) {

		KtExpression left = expression.getLeft();

		if (!"=".equals(expression.getOperationReference().getText())) {
			return null;
		}

		// Left must be extra["key"]
		if (!(left instanceof KtArrayAccessExpression arrayAccess) || arrayAccess.getIndexExpressions().isEmpty()) {
			return null;
		}

		KtExpression indexExpr = arrayAccess.getIndexExpressions().get(0);
		if (!(indexExpr instanceof KtStringTemplateExpression keyTemplate)) {
			return null;
		}

		return keyTemplate;
	}

	/**
	 * Locates the PSI element whose text should be updated or highlighted as the
	 * declared value for {@code propertyKey}.
	 */
	public static @Nullable PsiPropertyValueElement findExtraPropertyLocation(PsiFile file, String propertyKey) {

		KtBinaryExpression extraAssignment = findExtraAssignment(file, propertyKey);
		if (extraAssignment != null) {
			return getValueElement(extraAssignment.getRight(), propertyKey);
		}
		return null;
	}

	private static @Nullable PsiPropertyValueElement getValueElement(@Nullable KtExpression assignment, String key) {

		if (assignment == null) {
			return null;
		}

		KtStringTemplateExpression literalElement = getLiteralElement(assignment);
		String value = KotlinDslUtils.getText(literalElement);
		if (literalElement != null && value != null) {
			return new PsiPropertyValueElement(literalElement, key, value);
		}
		return null;
	}

	/**
	 * Value literal to highlight for a given {@code extra["…"] = …} assignment.
	 * @param assignment the right side assignment expression to analyze.
	 */
	public static @Nullable KtStringTemplateExpression getLiteralElement(@Nullable KtExpression assignment) {

		if (assignment instanceof KtStringTemplateExpression st) {
			return st;
		}
		if (assignment instanceof KtNameReferenceExpression ref && "it".equals(ref.getReferencedName())) {
			return KotlinDslUtils.findAlsoReceiverStringTemplate(ref);
		}
		if (assignment instanceof KtCallExpression call) {
			return KotlinDslUtils.findBuildStringAppendLiteral(call);
		}
		return null;
	}

	private static @Nullable KtBinaryExpression findExtraAssignment(PsiFile file, String propertyKey) {

		return SyntaxTraverser.psiTraverser(file)
				.filter(KtBinaryExpression.class)
				.filter(KotlinDslExtraParser::isExtra)
				.filter(it -> isKey(it, propertyKey))
				.first();
	}

	private static boolean isKey(KtBinaryExpression expr, String propertyKey) {

		KtStringTemplateExpression rightAssignment = getKeyAssignment(expr);
		if (rightAssignment == null) {
			return false;
		}

		String key = KotlinDslUtils.getText(rightAssignment);
		return propertyKey.equals(key);
	}

	public static boolean isExtra(KtBinaryExpression expr) {

		// Must be an assignment: operator text is "="
		if (!"=".equals(expr.getOperationReference().getText())) {
			return false;
		}

		KtExpression left = expr.getLeft();

		// Left must be extra["key"]
		if (!(left instanceof KtArrayAccessExpression arrayAccess)) {
			return false;
		}

		KtExpression receiver = arrayAccess.getArrayExpression();
		if (!(receiver instanceof KtNameReferenceExpression nameRef)
				|| !"extra".equals(nameRef.getReferencedName())) {
			return false;
		}

		// Index must be a plain string literal
		if (arrayAccess.getIndexExpressions().isEmpty()) {
			return false;
		}

		return true;
	}

}
