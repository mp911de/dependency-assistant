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
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

/**
 * Interface representing Kotlin DSL {@code extra} property declarations.
 * <p>Captures the supported declaration shapes behind one factory:
 * <ul>
 * <li>{@code extra["key"] = "value"} / {@code extra["key"] = """value"""} -
 * {@link StringLiteralAssignment plain string assignment}</li>
 * <li>{@code "value".also { extra["key"] = it }} -
 * {@link AlsoReceiverAssignment also-receiver assignment}</li>
 * <li>{@code extra["key"] = buildString { append("value") }} -
 * {@link BuildStringAssignment buildString assignment}</li>
 * </ul>
 *
 * @author Mark Paluch
 */
sealed interface KotlinExtraAssignment extends ExtraDeclaration {

	/**
	 * Return the receiver string template for an {@code also { ... = it }}
	 * assignment.
	 */
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

	static void from(KtArrayAccessExpression arrayAccess) {

	}

	/**
	 * Return the first string template appended within a {@code buildString { }}
	 * call.
	 */
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

	public static @Nullable String extractAlsoReceiverStringLiteral(KtNameReferenceExpression itRef) {

		KtStringTemplateExpression receiver = KotlinExtraAssignment.findAlsoReceiverStringTemplate(itRef);
		return receiver != null ? KtLiterals.getText(receiver) : null;
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

	/**
	 * @return the string template literal that represents the declared value.
	 */
	@Override
	KtStringTemplateExpression getValueLiteral();

	/**
	 * @return the binary assignment expression declaring the property.
	 */
	@Override
	KtBinaryExpression getDeclaration();

	/**
	 * Detect a Kotlin {@code extra} property declaration anchored at the assignment
	 * expression.
	 *
	 * @param expression the candidate element; can be {@literal null}.
	 * @return the resolved declaration, or {@literal null} if {@code expression} is
	 * not a supported {@code extra["key"] = value} shape.
	 */
	@Contract("null -> null")
	static @Nullable KotlinExtraAssignment from(@Nullable KtBinaryExpression expression) {

		if (expression == null || !"=".equals(expression.getOperationReference().getText())) {
			return null;
		}

		String key = extractKey(expression);
		if (StringUtils.isEmpty(key)) {
			return null;
		}

		AlsoReceiverAssignment alsoReceiver = AlsoReceiverAssignment.from(expression, key);
		if (alsoReceiver != null) {
			return alsoReceiver;
		}

		BuildStringAssignment buildString = BuildStringAssignment.from(expression, key);
		if (buildString != null) {
			return buildString;
		}

		return StringLiteralAssignment.from(expression, key);
	}

	/**
	 * Detect the indirect {@code "value".also { extra["key"] = it }} form by
	 * locating the enclosing {@code also} call from a PSI element living inside the
	 * receiver string template.
	 *
	 * @param valuePsi the PSI element inside the {@code also} receiver.
	 * @return the resolved declaration, or {@literal null} if no matching
	 * {@code also { extra[...] = it }} declaration is found.
	 */
	static @Nullable KotlinExtraAssignment fromAlsoReceiver(PsiElement valuePsi) {

		KtQualifiedExpression qualified = PsiTreeUtil.getParentOfType(valuePsi, KtQualifiedExpression.class);
		if (qualified == null) {
			return null;
		}

		if (qualified.getSelectorExpression() instanceof KtCallExpression alsoCall
				&& "also".equals(KotlinDslUtils.getKotlinCallName(alsoCall))) {
			return findAlsoAssignment(alsoCall);
		}
		return null;
	}

	private static @Nullable KotlinExtraAssignment findAlsoAssignment(KtCallExpression alsoCall) {

		KtLambdaExpression lambda = firstLambdaArgument(alsoCall);
		if (lambda == null) {
			return null;
		}

		KtBlockExpression bodyExpression = lambda.getBodyExpression();
		if (bodyExpression == null) {
			return null;
		}

		return SyntaxTraverser.psiTraverser(bodyExpression)
				.filter(KtBinaryExpression.class)
				.filterMap(it -> from(it) instanceof AlsoReceiverAssignment also ? also : null)
				.first();
	}

	private static @Nullable String extractKey(KtBinaryExpression expression) {

		if (!(expression.getLeft() instanceof KtArrayAccessExpression arrayAccess)) {
			return null;
		}

		if (!(arrayAccess.getArrayExpression() instanceof KtNameReferenceExpression nameRef)
				|| !"extra".equals(nameRef.getReferencedName())) {
			return null;
		}

		List<KtExpression> indices = arrayAccess.getIndexExpressions();
		if (indices.isEmpty() || !(indices.getFirst() instanceof KtStringTemplateExpression keyTemplate)) {
			return null;
		}

		String key = KtLiterals.getText(keyTemplate);
		return StringUtils.hasText(key) ? key : null;
	}

	private static @Nullable KtLambdaExpression firstLambdaArgument(KtCallExpression call) {
		for (ValueArgument argument : call.getValueArguments()) {
			if (argument.getArgumentExpression() instanceof KtLambdaExpression lambda) {
				return lambda;
			}
		}
		return null;
	}

	/**
	 * {@code extra["key"] = "value"} or {@code extra["key"] = """value"""}
	 * declaration.
	 * <p>Example: <pre class="code">
	 * extra["springVersion"] = "6.2.0"
	 * </pre>
	 */
	record StringLiteralAssignment(String getKey, KtStringTemplateExpression getValueLiteral,
			KtBinaryExpression getDeclaration) implements KotlinExtraAssignment {

		static @Nullable StringLiteralAssignment from(KtBinaryExpression expression, String key) {
			if (expression.getRight() instanceof KtStringTemplateExpression stringTemplate) {
				return new StringLiteralAssignment(key, stringTemplate, expression);
			}
			return null;
		}

		@Override
		public String getValue() {
			return KtLiterals.from(getValueLiteral()).toString();
		}

	}

	/**
	 * {@code "value".also { extra["key"] = it }} declaration.
	 * <p>Example: <pre class="code">
	 * "6.2.0".also { extra["springVersion"] = it }
	 * </pre>
	 */
	record AlsoReceiverAssignment(String getKey, KtStringTemplateExpression getValueLiteral,
			KtBinaryExpression getDeclaration, KtNameReferenceExpression itReference) implements KotlinExtraAssignment {

		static @Nullable AlsoReceiverAssignment from(KtBinaryExpression expression, String key) {
			if (!(expression.getRight() instanceof KtNameReferenceExpression reference)
					|| !"it".equals(reference.getReferencedName())) {
				return null;
			}

			KtStringTemplateExpression receiver = findAlsoReceiverStringTemplate(reference);
			if (receiver == null) {
				return null;
			}

			return new AlsoReceiverAssignment(key, receiver, expression, reference);
		}

		@Override
		public String getValue() {
			return KtLiterals.from(getValueLiteral()).toString();
		}

	}

	/**
	 * {@code extra["key"] = buildString { append("value") }} declaration.
	 * <p>Example: <pre class="code">
	 * extra["springVersion"] = buildString {
	 *     append("6.2.0")
	 * }
	 * </pre>
	 */
	record BuildStringAssignment(String getKey, KtStringTemplateExpression getValueLiteral,
			KtBinaryExpression getDeclaration,
			KtCallExpression buildStringCall) implements KotlinExtraAssignment {

		static @Nullable BuildStringAssignment from(KtBinaryExpression expression, String key) {
			if (!(expression.getRight() instanceof KtCallExpression call)) {
				return null;
			}

			KtStringTemplateExpression literal = findBuildStringAppendLiteral(call);
			if (literal == null) {
				return null;
			}

			return new BuildStringAssignment(key, literal, expression, call);
		}

		@Override
		public String getValue() {
			return KtLiterals.from(getValueLiteral()).toString();
		}

	}

}
