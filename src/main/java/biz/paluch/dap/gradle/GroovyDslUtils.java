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

import java.util.List;
import java.util.function.Predicate;

import biz.paluch.dap.support.Expression;
import biz.paluch.dap.util.PsiElements;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Utility methods for Groovy DSL.
 *
 * @author Mark Paluch
 */
class GroovyDslUtils {

	public static boolean isInsidePluginsBlock(PsiElement element) {
		return isInsideGroovyBlock(element, GradleUtils::isPluginSection);
	}

	public static boolean isInsidePlatformBlock(PsiElement element) {
		return isInsideGroovyBlock(element, GradleUtils::isPlatformSection);
	}

	/**
	 * Return whether an enclosing block or call name matches the predicate.
	 */
	public static boolean isInsideGroovyBlock(PsiElement element, Predicate<String> predicate) {

		return PsiElements.findFirstParent(element, true, parent -> {
			if (parent instanceof GrClosableBlock) {
				PsiElement blockParent = parent.getParent();
				return blockParent instanceof GrMethodCall call && predicate.test(getGroovyMethodName(call));
			}

			if (parent instanceof GrMethodCall methodCall) {
				return predicate.test(getGroovyMethodName(methodCall));
			}
			return false;
		}) != null;
	}

	public static String getGroovyMethodName(GrMethodCall call) {
		return getRequiredText(call.getInvokedExpression()).trim();
	}

	/**
	 * Return expression text.
	 * @throws IllegalArgumentException if no supported text is available.
	 */
	static String getRequiredText(GrExpression expression) {

		Assert.notNull(expression, "Expression must not be null");
		String text = getText(expression);
		if (text == null) {
			throw new IllegalArgumentException(
					"No text available: %s (%s)".formatted(expression, expression.getClass().getName()));
		}
		return text;
	}

	/**
	 * Return a reference name or literal text.
	 * @throws IllegalArgumentException if the expression is neither a reference nor
	 * a literal.
	 */
	public static @Nullable String getText(GrExpression expression) {

		if (expression instanceof GrReferenceExpression ref) {
			return ref.getReferenceName();
		}

		if (expression instanceof GrLiteral literal) {
			return getText(literal);
		}

		throw new IllegalArgumentException("Expected GrLiteral, got %s (%s)".formatted(expression, expression.getClass()
				.getName()));
	}

	/**
	 * Return whether the reference or string literal contains non-whitespace text.
	 */
	public static boolean hasText(@Nullable GrExpression expression) {

		if (expression instanceof GrReferenceExpression ref && StringUtils.hasText(ref.getReferenceName())) {
			return true;
		}

		if (expression instanceof GrLiteral literal && literal.getValue() instanceof String s
				&& StringUtils.hasText(s)) {
			return true;
		}

		return false;
	}

	/**
	 * Return whether the literal is a constant string rather than an interpolated
	 * GString.
	 */
	public static boolean isConstantString(@Nullable GrLiteral literal) {
		return literal != null && literal.getValue() instanceof String;
	}

	/**
	 * Return string content, preserving interpolation text.
	 */
	public static String getText(GrLiteral literal) {

		if (literal.getValue() instanceof String s) {
			return s;
		}

		StringBuilder builder = new StringBuilder();
		for (PsiElement child : literal.getChildren()) {
			builder.append(child.getText());
		}
		return builder.toString();
	}

	/**
	 * Convert a version value to an {@link Expression}.
	 * @return {@literal null} for unsupported shapes or empty text.
	 */
	static @Nullable Expression toExpression(@Nullable GrExpression expression) {

		if (expression instanceof GrReferenceExpression reference) {
			String name = reference.getReferenceName();
			return StringUtils.hasText(name) ? Expression.property(name) : null;
		}

		if (expression instanceof GrLiteral literal) {
			String text = getText(literal);
			return StringUtils.hasText(text) ? Expression.from(text) : null;
		}

		return null;
	}

	// -------------------------------------------------------------------------
	// Version catalog (Groovy accessor)
	// -------------------------------------------------------------------------

	public static @Nullable GrExpression unwrapGroovyParentheses(@Nullable GrExpression expr) {

		GrExpression e = expr;
		while (e instanceof GrParenthesizedExpression p) {
			e = p.getOperand();
		}
		return e;
	}

	/**
	 * Find the enclosing catalog consumer call, or {@literal null} if absent.
	 */
	static @Nullable GrMethodCall findEnclosingGroovyCatalogAccessorCall(PsiElement element) {

		if (!(element instanceof GrReferenceExpression) || !(element.getParent() instanceof GrArgumentList args)) {
			return null;
		}

		if (!(args.getParent() instanceof GrMethodCall call)) {
			return null;
		}

		if (!isGroovyCatalogConsumerCall(call)) {
			return null;
		}

		GrExpression arg = getFirstGroovyCatalogArgumentExpression(call);
		if (arg == null) {
			return null;
		}

		return call;
	}

	public static boolean isGroovyCatalogConsumerCall(GrMethodCall call) {

		String name = getGroovyMethodName(call);
		return GradleUtils.isCatalogConsumerCall(name)
				&& (!GradleUtils.isPlugin(name) || GroovyDslUtils.isInsidePluginsBlock(call));
	}

	static @Nullable GrExpression getFirstGroovyCatalogArgumentExpression(GrMethodCall call) {

		GrArgumentList argList = call.getArgumentList();

		for (PsiElement arg : argList.getAllArguments()) {
			if (arg instanceof GrNamedArgument named) {
				return unwrapGroovyParentheses(named.getExpression());
			}
			if (arg instanceof GrExpression ex) {
				return unwrapGroovyParentheses(ex);
			}
		}
		return null;
	}

	public static List<String> getVersionCatalogSegments(GrExpression expr) {
		return SyntaxTraverser.psiTraverser(expr)
				.expand(it -> it instanceof GrReferenceExpression)
				.filterTypes(GroovyElementTypes.IDENTIFIER::equals)
				.map(PsiElement::getText).toList();
	}

	// -------------------------------------------------------------------------
	// Command-style platform notation: implementation platform 'g:a:1.0'
	// -------------------------------------------------------------------------

	/**
	 * Find the coordinate in {@code implementation platform "g:a:1.0"}.
	 * <p>Groovy represents the coordinate as a property name on
	 * {@code implementation(platform)}.
	 * @return {@literal null} if the call has no command-style platform coordinate.
	 */
	static @Nullable GrReferenceExpression getCommandPlatformString(GrMethodCall call) {

		if (!GradleUtils.isDependencySection(getGroovyMethodName(call)) || !hasPlatformArgument(call)) {
			return null;
		}

		if (call.getParent() instanceof GrReferenceExpression reference) {
			PsiElement name = reference.getReferenceNameElement();
			String text = name != null ? name.getText() : "";
			if (text.length() > 1 && (text.charAt(0) == '\'' || text.charAt(0) == '"')) {
				return reference;
			}
		}

		return null;
	}

	/**
	 * Find the command-style platform coordinate containing the element, or
	 * {@literal null}.
	 */
	static @Nullable GrReferenceExpression findCommandPlatformString(PsiElement element) {

		if (element instanceof GrMethodCall call) {
			return getCommandPlatformString(call);
		}

		GrReferenceExpression reference = PsiTreeUtil.getParentOfType(element, GrReferenceExpression.class, false);
		return reference != null && getCommandPlatformCall(reference) != null ? reference : null;
	}

	/**
	 * Find the call owning a command-style platform coordinate, or {@literal null}.
	 */
	static @Nullable GrMethodCall getCommandPlatformCall(GrReferenceExpression string) {
		return string.getQualifierExpression() instanceof GrMethodCall call && getCommandPlatformString(call) == string
				? call
				: null;
	}

	private static boolean hasPlatformArgument(GrMethodCall call) {

		for (GrExpression argument : call.getExpressionArguments()) {
			if (argument instanceof GrReferenceExpression reference
					&& GradleUtils.isPlatformSection(reference.getReferenceName())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Replaces the string content of a Groovy literal while preserving its quote
	 * style.
	 */
	static void updateText(GrLiteral literal, String text) {

		String content = literal.getText();

		if (!StringUtils.hasText(text)) {
			return;
		}

		char quote = content.charAt(0);
		// Use the same quote character (single or double) that was originally used.
		String newLiteralText = quote + text + quote;
		literal.updateText(newLiteralText);
	}

}
