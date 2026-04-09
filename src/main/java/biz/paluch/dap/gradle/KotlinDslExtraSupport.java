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

import org.jetbrains.kotlin.psi.KtArrayAccessExpression;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLambdaExpression;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtQualifiedExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.ValueArgument;
import org.jetbrains.kotlin.psi.stubs.elements.KtStringTemplateExpressionElementType;
import org.jspecify.annotations.Nullable;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Kotlin DSL {@code extra["key"]} assignment patterns beyond plain {@code extra["k"] = "v"}.
 *
 * @author Mark Paluch
 */
class KotlinDslExtraSupport {

	private KotlinDslExtraSupport() {}

	/**
	 * Resolves the string value assigned to an {@code extra[...]} expression, or {@code null} if unsupported.
	 */
	static @Nullable String getText(KtExpression right) {

		if (right instanceof KtStringTemplateExpression st) {
			return getText(st);
		}
		if (right instanceof KtNameReferenceExpression ref && "it".equals(ref.getReferencedName())) {
			return extractAlsoReceiverStringLiteral(ref);
		}
		if (right instanceof KtCallExpression call) {
			KtStringTemplateExpression literal = findBuildStringAppendLiteral(call);
			if (literal != null) {
				return getText(literal);
			}
		}
		return null;
	}

	/**
	 * Locates the PSI element whose text should be updated or highlighted as the declared value for {@code propertyKey}.
	 */
	static @Nullable PsiElement findExtraPropertyValuePsi(PsiFile file, String propertyKey) {

		KtBinaryExpression assign = findExtraAssignment(file, propertyKey);
		if (assign == null) {
			return null;
		}
		KtExpression right = assign.getRight();
		if (right instanceof KtStringTemplateExpression st) {
			return st;
		}
		if (right instanceof KtNameReferenceExpression ref && "it".equals(ref.getReferencedName())) {
			return findAlsoReceiverStringTemplate(ref);
		}
		if (right instanceof KtCallExpression call) {
			return findBuildStringAppendLiteral(call);
		}
		return null;
	}

	static KtBinaryExpression findExtraAssignment(PsiFile file, String propertyKey) {

		KtBinaryExpression[] found = { null };
		file.accept(new PsiRecursiveElementVisitor() {
			@Override
			public void visitElement(PsiElement element) {
				if (found[0] != null) {
					return;
				}
				super.visitElement(element);
				if (element instanceof KtBinaryExpression bin && matchesExtraKey(bin, propertyKey)) {
					found[0] = bin;
				}
			}
		});
		return found[0];
	}

	static boolean matchesExtraKey(KtBinaryExpression expr, String propertyKey) {

		if (!"=".equals(expr.getOperationReference().getText())) {
			return false;
		}
		KtExpression left = expr.getLeft();
		if (!(left instanceof KtArrayAccessExpression arrayAccess)) {
			return false;
		}
		KtExpression receiver = arrayAccess.getArrayExpression();
		if (!(receiver instanceof KtNameReferenceExpression nameRef) || !"extra".equals(nameRef.getReferencedName())) {
			return false;
		}
		if (arrayAccess.getIndexExpressions().isEmpty()) {
			return false;
		}
		KtExpression indexExpr = arrayAccess.getIndexExpressions().get(0);
		if (!(indexExpr instanceof KtStringTemplateExpression keyTemplate)) {
			return false;
		}
		String key = getText(keyTemplate);
		return propertyKey.equals(key);
	}

	/**
	 * Plain or triple-quoted Kotlin string literal without interpolation.
	 */
	static @Nullable String getText(KtStringTemplateExpression template) {

		String text = template.getText();
		if (text == null) {
			return null;
		}
		PsiElement[] children = template.getChildren();
		if (template.getIElementType() instanceof KtStringTemplateExpressionElementType && children.length == 1) {
			return children[0].getText();
		}

		if (text.length() >= 6 && text.startsWith("\"\"\"") && text.endsWith("\"\"\"")) {
			String inner = text.substring(3, text.length() - 3);
			if (inner.startsWith("\n")) {
				inner = inner.substring(1);
			}
			if (inner.endsWith("\n")) {
				inner = inner.substring(0, inner.length() - 1);
			}
			if (inner.contains("${") || (inner.contains("$") && inner.matches(".*\\$[A-Za-z_].*"))) {
				return null;
			}
			return inner.isEmpty() ? null : inner;
		}
		if (text.length() < 2) {
			return null;
		}
		char open = text.charAt(0);
		char close = text.charAt(text.length() - 1);
		if (!((open == '"' && close == '"') || (open == '\'' && close == '\''))) {
			return null;
		}
		String value = text.substring(1, text.length() - 1);
		if (value.contains("${") || (value.contains("$") && value.matches(".*\\$[A-Za-z_].*"))) {
			return null;
		}
		return value.isEmpty() ? null : value;
	}

	private static @Nullable String extractAlsoReceiverStringLiteral(KtNameReferenceExpression itRef) {

		KtStringTemplateExpression receiver = findAlsoReceiverStringTemplate(itRef);
		return receiver != null ? getText(receiver) : null;
	}

	static @Nullable KtStringTemplateExpression findAlsoReceiverStringTemplate(KtNameReferenceExpression itRef) {

		KtLambdaExpression lambda = PsiTreeUtil.getParentOfType(itRef, KtLambdaExpression.class);
		if (lambda == null) {
			return null;
		}
		KtCallExpression alsoCall = PsiTreeUtil.getParentOfType(lambda, KtCallExpression.class);
		if (alsoCall == null || !"also".equals(KotlinDslUtils.getKotlinCallName(alsoCall))) {
			return null;
		}
		PsiElement parent = alsoCall.getParent();
		if (parent instanceof KtQualifiedExpression dot) {
			KtExpression recv = dot.getReceiverExpression();
			if (recv instanceof KtStringTemplateExpression st) {
				return st;
			}
		}
		return null;
	}

	static @Nullable KtStringTemplateExpression findBuildStringAppendLiteral(KtCallExpression buildStringCall) {

		if (!"buildString".equals(KotlinDslUtils.getKotlinCallName(buildStringCall))) {
			return null;
		}
		KtLambdaExpression lambda = findTrailingLambda(buildStringCall);
		if (lambda == null) {
			return null;
		}
		KtExpression body = lambda.getBodyExpression();
		if (body == null) {
			return null;
		}
		for (KtCallExpression inner : PsiTreeUtil.collectElementsOfType(body, KtCallExpression.class)) {
			if (!"append".equals(KotlinDslUtils.getKotlinCallName(inner))) {
				continue;
			}
			for (ValueArgument va : inner.getValueArguments()) {
				KtExpression argExpr = va.getArgumentExpression();
				if (argExpr instanceof KtStringTemplateExpression st) {
					return st;
				}
			}
		}
		return null;
	}

	private static @Nullable KtLambdaExpression findTrailingLambda(KtCallExpression call) {

		for (ValueArgument va : call.getValueArguments()) {
			KtExpression expr = va.getArgumentExpression();
			if (expr instanceof KtLambdaExpression lambda) {
				return lambda;
			}
		}
		return null;
	}

}
