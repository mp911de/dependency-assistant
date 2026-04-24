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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Predicates;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
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

	private static final Logger LOG = Logger.getInstance(GroovyDslUtils.class);

	/**
	 * Returns a {@link PropertyValue} when {@code element} is the <em>value</em>
	 * literal of a Groovy {@code ext} property declaration, or {@code null}
	 * otherwise.
	 * <p>The three supported forms are:
	 * <ul>
	 * <li>{@code ext { set('key', 'value') }} — set-call form</li>
	 * <li>{@code ext { key = 'value' }} — assignment inside an {@code ext}
	 * closure</li>
	 * <li>{@code ext.key = 'value'} — dot-qualified assignment</li>
	 * </ul>
	 */
	public static @Nullable PropertyValue findGroovyExtPropertyVersionElement(PsiElement element) {

		if (!(element instanceof GrLiteral literal)) {
			return null;
		}

		PropertyValue setLoc = resolvePropertyLocation(literal);
		if (setLoc != null) {
			return setLoc;
		}
		PropertyValue extAssignment = tryExtAssignmentValue(literal);
		if (extAssignment != null) {
			return extAssignment;
		}
		return findGroovyScriptVariableLiteralValue(literal);
	}

	/**
	 * Returns the variable binding when {@code literal} is the initializer of a
	 * top-level script variable ({@code def}/{@code val}/{@code String} … forms).
	 */
	static @Nullable PropertyValue findGroovyScriptVariableLiteralValue(GrLiteral literal) {

		GrVariableDeclaration declaration = PsiTreeUtil.getParentOfType(literal, GrVariableDeclaration.class);
		if (declaration == null) {
			return null;
		}

		for (GrVariable variable : declaration.getVariables()) {
			GrExpression initializer = variable.getInitializerGroovy();
			if (initializer == null || !PsiTreeUtil.isAncestor(initializer, literal, false)) {
				continue;
			}
			String name = variable.getName();
			if (!StringUtils.hasText(name)) {
				continue;
			}
			String value = GroovyDslUtils.getText(literal);
			if (StringUtils.isEmpty(value)) {
				continue;
			}
			return new PropertyValue(literal, name, value);
		}

		return null;
	}

	public static @Nullable PropertyValue resolvePropertyLocation(GrLiteral literal) {

		GrMethodCall setCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);

		if (setCall == null || !"set".equals(getGroovyMethodName(setCall))) {
			return null;
		}

		PsiElement[] args = setCall.getArgumentList().getAllArguments();
		if (args.length < 2 || !(args[0] instanceof GrLiteral keyLiteral)
				|| !(args[1] instanceof GrLiteral valueLiteral)) {
			return null;
		}

		// Only process the value argument (args[1]); reject the key literal (args[0]).
		if (literal != valueLiteral) {
			return null;
		}

		String key = getText(keyLiteral);
		String value = GroovyDslUtils.getText(literal);
		if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
			return null;
		}
		return new PropertyValue(literal, key, value);
	}

	/**
	 * Detects {@code ext { key = 'value' }} and {@code ext.key = 'value'} forms.
	 */
	public static @Nullable PropertyValue tryExtAssignmentValue(GrLiteral literal) {

		GrAssignmentExpression assign = PsiTreeUtil.getParentOfType(literal, GrAssignmentExpression.class);

		if (assign == null || assign.isOperatorAssignment()) {
			return null;
		}

		// Guard: only process when the literal IS the RHS value.
		if (assign.getRValue() != literal) {
			return null;
		}

		GrExpression lhs = assign.getLValue();
		if (!(lhs instanceof GrReferenceExpression ref)) {
			return null;
		}

		GrExpression qualifier = ref.getQualifierExpression();

		String key = null;
		if (qualifier == null && isInsideGroovyBlock(literal, "ext"::equals)) {
			// Plain assignment inside ext {} closure: springVersion = '1.0'
			key = ref.getReferenceName();
		} else if (qualifier instanceof GrReferenceExpression qualRef && "ext".equals(qualRef.getReferenceName())) {
			// Dot-qualified: ext.springVersion = '1.0'
			key = ref.getReferenceName();
		}

		if (key == null) {
			return null;
		}

		String value = literal.getValue() instanceof String v ? v : GroovyDslUtils.getText(literal);
		return new PropertyValue(literal, key, value);
	}


	public static boolean isInsidePluginsBlock(PsiElement element) {
		return isInsideGroovyBlock(element, GradleUtils::isPluginSection);
	}

	public static boolean isInsideGroovyBlock(PsiElement element, Predicate<String> predicate) {

		PsiElement parent = element.getParent();

		while (parent != null && !(parent instanceof PsiFile)) {
			if (parent instanceof GrClosableBlock) {
				PsiElement blockParent = parent.getParent();
				if (blockParent instanceof GrMethodCall call && predicate.test(getGroovyMethodName(call))) {
					return true;
				}
			}
			parent = parent.getParent();
		}

		return false;
	}

	/**
	 * Return the name of the Groovy method call.
	 *
	 * @param call method call.
	 * @return the name of the method call.
	 */
	public static String getGroovyMethodName(GrMethodCall call) {
		return getRequiredText(call.getInvokedExpression()).trim();
	}

	/**
	 * Return the required text associated with {@code expression}.
	 *
	 * @param expression the expression to inspect.
	 * @return the required text.
	 * @throws IllegalArgumentException if the expression is not supported.
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
	 * Return the text of a Groovy literal or expression.
	 *
	 * @param expression the expression to extract the text from.
	 * @return the extracted text.
	 * @throws IllegalArgumentException if {@code expression} is not a literal.
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
	 * Check whether the given {@code GrExpression} contains actual <em>text</em>.
	 *
	 * @param expression the expression to check.
	 * @return {@code true} if the expression contains actual text; {@code false}
	 * otherwise.
	 * @see StringUtils#hasText
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
	 * Returns the plain string content of a Groovy literal.
	 * @param literal the literal to extract the text from.
	 * @return the string value.
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

	// -------------------------------------------------------------------------
	// Version catalog (Groovy {@code libs.…})
	// -------------------------------------------------------------------------

	public static @Nullable GrExpression unwrapGroovyParentheses(@Nullable GrExpression expr) {

		GrExpression e = expr;
		while (e instanceof GrParenthesizedExpression p) {
			e = p.getOperand();
		}
		return e;
	}

	/**
	 * Innermost {@code alias}/{@code id}/{@code implementation}/… call whose first
	 * argument is a {@code libs.…} reference chain and that contains
	 * {@code element}.
	 */
	static @Nullable GrMethodCall findEnclosingGroovyCatalogAccessorCall(PsiElement element,
			VersionCatalogRegistry registry) {

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
		if (arg == null || !isGroovyLibsCatalogRootExpression(arg, registry)) {
			return null;
		}

		return call;
	}

	private static boolean isGroovyCatalogConsumerCall(GrMethodCall call) {

		String name = getGroovyMethodName(call);

		return GradleUtils.isCatalogConsumerCall(name)
				&& (!GradleUtils.isPlugin(name) || KotlinDslUtils.isInsidePluginsBlock(call));
	}

	static @Nullable GrExpression getFirstGroovyCatalogArgumentExpression(GrMethodCall call) {

		GrArgumentList argList = call.getArgumentList();
		if (argList == null) {
			return null;
		}
		for (PsiElement a : argList.getAllArguments()) {
			if (a instanceof GrNamedArgument named) {
				return unwrapGroovyParentheses(named.getExpression());
			}
			if (a instanceof GrExpression ex) {
				return unwrapGroovyParentheses(ex);
			}
		}
		return null;
	}

	static boolean isGroovyLibsCatalogRootExpression(GrExpression expr, VersionCatalogRegistry registry) {

		TomlReference reference = getTomlReference(expr, registry.catalogPaths().keySet());
		return reference != null;
	}

	static @Nullable TomlReference getTomlReference(GrExpression expr, Set<String> knownAliases) {

		GrExpression e = unwrapGroovyParentheses(expr);
		if (e == null) {
			return null;
		}
		ArrayList<String> segments = new ArrayList<>();
		GrExpression cur = e;
		while (cur instanceof GrReferenceExpression ref) {
			String name = GroovyDslUtils.getText(ref);
			if (name == null) {
				return null;
			}
			segments.add(name);
			cur = ref.getQualifierExpression();
		}
		if (cur != null) {
			return null;
		}
		Collections.reverse(segments);
		return TomlReference.from(segments, knownAliases);
	}

	/**
	 * Replaces the string content of a Groovy literal while preserving its quote
	 * style.
	 */
	static void updateText(GrLiteral literal, String text) {

		String content = literal.getText();

		if (!StringUtils.hasText(text) || text.length() < 2) {
			return;
		}

		char quote = content.charAt(0);
		// Use the same quote character (single or double) that was originally used.
		String newLiteralText = quote + text + quote;
		literal.updateText(newLiteralText);
	}

	public record PluginId(GrLiteral id, GrLiteral version, String resolvedPluginId) {

		// Three forms appear in Gradle Groovy DSL plugins {} blocks:
		//
		// (1) Flat command args (non-chained):
		// id 'x' version 'y' GrApplicationStatement(id, ['x', version_ref, 'y'])
		//
		// (2) Chained command expression (most common):
		// id 'x' version 'y' inner GrApplicationStatement(id, ['x'])
		// whose parent is GrReferenceExpression('version')
		// whose parent is outer GrApplicationStatement(version, ['y'])
		//
		// (3) Explicit-paren + command chain:
		// id('x') version 'y' same chained structure as (2) but inner uses explicit
		// parens
		public static @Nullable PluginId fromMethodCall(GrMethodCall call, PropertyResolver properties) {
			return fromMethodCall(call, Predicates.alwaysTrue(), properties);
		}

		public static @Nullable PluginId fromMethodCall(GrMethodCall call, ArtifactId plugin,
				PropertyResolver properties) {
			return fromMethodCall(call, id -> plugin.groupId().equals(id), properties);
		}

		public static @Nullable PluginId fromMethodCall(GrMethodCall call, Predicate<String> idPredicate,
				PropertyResolver properties) {

			GrLiteral idLiteral = findFirstLiteralArgument(call);
			if (idLiteral == null) {
				return null;
			}

			String resolvedId = properties.resolvePlaceholders(GroovyDslUtils.getText(idLiteral));
			if (!idPredicate.test(resolvedId)) {
				return null;
			}

			GrLiteral versionLiteral = findInlineVersionLiteral(call);
			if (versionLiteral == null) {
				versionLiteral = findChainedVersionLiteral(call);
			}

			return versionLiteral != null ? new PluginId(idLiteral, versionLiteral, resolvedId) : null;
		}


		public String getVersionAsString() {
			return GroovyDslUtils.getText(version);
		}

		/**
		 * Returns a plugin {@link ArtifactId} when {@link #resolvedPluginId()} is safe
		 * to use as a coordinate, or {@code null} otherwise.
		 */
		@Nullable
		ArtifactId toValidatedArtifactId() {

			if (!GradlePlugin.isValidPluginId(resolvedPluginId)) {
				LOG.debug("Skipping plugin entry: cannot use resolved id '%s'".formatted(resolvedPluginId));
				return null;
			}
			return GradlePlugin.of(resolvedPluginId);
		}

		private static @Nullable GrLiteral findFirstLiteralArgument(GrMethodCall call) {

			for (GroovyPsiElement argument : call.getArgumentList().getAllArguments()) {
				if (argument instanceof GrLiteral literal) {
					return literal;
				}
			}

			return null;
		}

		private static @Nullable GrLiteral findInlineVersionLiteral(GrMethodCall call) {

			boolean sawVersionKeyword = false;

			for (GroovyPsiElement argument : call.getArgumentList().getAllArguments()) {
				if (!sawVersionKeyword && argument instanceof GrReferenceExpression referenceExpression
						&& "version".equals(referenceExpression.getReferenceName())) {
					sawVersionKeyword = true;
					continue;
				}

				if (sawVersionKeyword && argument instanceof GrLiteral literal) {
					return literal;
				}
			}

			return null;
		}

		private static @Nullable GrLiteral findChainedVersionLiteral(GrMethodCall call) {

			PsiElement parent = call.getParent();
			if (!(parent instanceof GrReferenceExpression versionRef)
					|| !"version".equals(versionRef.getReferenceName())
					|| !(versionRef.getParent() instanceof GrMethodCall outerCall)) {
				return null;
			}

			return findFirstLiteralArgument(outerCall);
		}

	}

}
