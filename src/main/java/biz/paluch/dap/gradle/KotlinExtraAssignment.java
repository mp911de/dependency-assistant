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

import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtArrayAccessExpression;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLambdaExpression;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtQualifiedExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.ValueArgument;
import org.jspecify.annotations.Nullable;

/**
 * Recogniser for Kotlin DSL {@code extra["key"] = value} assignments.
 * <p>Captures the assignment expression, the resolved key, and the value
 * expression for both the plain {@code extra["k"] = "v"} form and the indirect
 * {@code "v".also { extra["k"] = it }} form.
 *
 * @param expression the {@code extra["key"] = value} binary expression.
 * @param key the resolved key string.
 * @param value the right-hand side expression.
 * @author Mark Paluch
 */
record KotlinExtraAssignment(KtBinaryExpression expression, String key, KtExpression value) {

	/**
	 * Recognise a plain {@code extra["key"] = value} assignment.
	 *
	 * @param expression the binary expression to inspect; can be {@literal null}.
	 * @return the assignment, or {@literal null} if {@code expression} is not an
	 * {@code extra[...]} assignment with a string-template key.
	 */
	static @Nullable KotlinExtraAssignment of(@Nullable KtBinaryExpression expression) {

		if (expression == null) {
			return null;
		}

		if (!"=".equals(expression.getOperationReference().getText())) {
			return null;
		}

		if (!(expression.getLeft() instanceof KtArrayAccessExpression arrayAccess)) {
			return null;
		}

		if (!(arrayAccess.getArrayExpression() instanceof KtNameReferenceExpression nameRef)
				|| !"extra".equals(nameRef.getReferencedName())) {
			return null;
		}

		List<KtExpression> indices = arrayAccess.getIndexExpressions();
		if (indices.isEmpty() || !(indices.get(0) instanceof KtStringTemplateExpression keyTemplate)) {
			return null;
		}

		String key = KotlinDslUtils.getText(keyTemplate);
		if (StringUtils.isEmpty(key)) {
			return null;
		}

		KtExpression value = expression.getRight();
		if (value == null) {
			return null;
		}

		return new KotlinExtraAssignment(expression, key, value);
	}

	/**
	 * Recognise the indirect {@code "value".also { extra["key"] = it }} form by
	 * locating the enclosing {@code also} call from a value PSI element living
	 * inside the receiver string template.
	 *
	 * @param valuePsi the PSI element inside the {@code also} receiver.
	 * @return the resolved assignment, or {@literal null} if no matching
	 * {@code also { extra[...] = it }} is found.
	 */
	static @Nullable KotlinExtraAssignment fromAlsoReceiver(PsiElement valuePsi) {

		KtQualifiedExpression qual = PsiTreeUtil.getParentOfType(valuePsi, KtQualifiedExpression.class);
		while (qual != null) {

			KtExpression recv = qual.getReceiverExpression();
			if (recv != null && PsiTreeUtil.isAncestor(recv, valuePsi, false)) {

				if (qual.getSelectorExpression() instanceof KtCallExpression alsoCall
						&& "also".equals(KotlinDslUtils.getKotlinCallName(alsoCall))) {

					KtBinaryExpression assignment = findExtraItAssignment(alsoCall);
					if (assignment != null) {
						return of(assignment);
					}
				}
				return null;
			}
			qual = PsiTreeUtil.getParentOfType(qual, KtQualifiedExpression.class);
		}
		return null;
	}

	/**
	 * Resolve the value-side string template literal to update or highlight,
	 * handling the plain literal, {@code also}-receiver, and {@code buildString}
	 * forms.
	 *
	 * @return the resolved string template literal, or {@literal null} if the value
	 * is not a recognised literal form.
	 */
	@Nullable
	KtStringTemplateExpression valueLiteral() {

		if (value instanceof KtStringTemplateExpression st) {
			return st;
		}

		if (value instanceof KtNameReferenceExpression ref && "it".equals(ref.getReferencedName())) {
			return KotlinDslUtils.findAlsoReceiverStringTemplate(ref);
		}

		if (value instanceof KtCallExpression call) {
			return KotlinDslUtils.findBuildStringAppendLiteral(call);
		}

		return null;
	}

	private static @Nullable KtBinaryExpression findExtraItAssignment(KtCallExpression alsoCall) {

		KtLambdaExpression lambda = firstLambdaArgument(alsoCall);
		if (lambda == null || lambda.getBodyExpression() == null) {
			return null;
		}

		for (KtBinaryExpression assign : PsiTreeUtil.collectElementsOfType(lambda.getBodyExpression(),
				KtBinaryExpression.class)) {

			KotlinExtraAssignment candidate = of(assign);
			if (candidate == null) {
				continue;
			}
			if (candidate.value() instanceof KtNameReferenceExpression ref
					&& "it".equals(ref.getReferencedName())) {
				return assign;
			}
		}
		return null;
	}

	private static @Nullable KtLambdaExpression firstLambdaArgument(KtCallExpression call) {

		for (ValueArgument va : call.getValueArguments()) {
			if (va.getArgumentExpression() instanceof KtLambdaExpression lam) {
				return lam;
			}
		}
		return null;
	}

}
