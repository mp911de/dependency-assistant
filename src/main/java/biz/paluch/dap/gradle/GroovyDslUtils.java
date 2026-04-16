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
import biz.paluch.dap.gradle.GradleParserSupport.NamedDependencyDeclaration;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PsiPropertyValueElement;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Predicates;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
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
	 * Returns a {@link PsiPropertyValueElement} when {@code element} is the
	 * <em>value</em> literal of a Groovy {@code ext} property declaration, or
	 * {@code null} otherwise.
	 * <p>The three supported forms are:
	 * <ul>
	 * <li>{@code ext { set('key', 'value') }} — set-call form</li>
	 * <li>{@code ext { key = 'value' }} — assignment inside an {@code ext}
	 * closure</li>
	 * <li>{@code ext.key = 'value'} — dot-qualified assignment</li>
	 * </ul>
	 */
	public static @Nullable PsiPropertyValueElement findGroovyExtPropertyVersionElement(PsiElement element) {

		if (!(element instanceof GrLiteral literal)) {
			return null;
		}

		PsiPropertyValueElement setLoc = resolvePropertyLocation(literal);
		if (setLoc != null) {
			return setLoc;
		}
		return tryExtAssignmentValue(literal);
	}

	/**
	 * Returns the version value element for a {@code gradle.properties} property if
	 * the element is a property value that maps to a known dependency artifact in
	 * the cache.
	 */
	public static @Nullable PsiPropertyValueElement findPropertiesVersionElement(PsiElement element) {

		PsiFile file = element.getContainingFile();

		if (!(file instanceof PropertiesFile)) {
			return null;
		}

		// Walk up to the IProperty PSI element
		IProperty property = PsiTreeUtil.getParentOfType(element, com.intellij.lang.properties.psi.Property.class);
		if (property == null) {
			return null;
		}

		// We only suggest versions for value elements (not key elements)
		PsiElement psiElement = property.getPsiElement();
		PsiElement valueElement = psiElement.getLastChild();
		if (!PsiTreeUtil.isAncestor(valueElement, element, false)) {
			return null;
		}

		return new PsiPropertyValueElement(psiElement, property.getKey(), property.getValue());
	}

	/**
	 * Returns the version segment {@link PsiElement} if the caret is inside the
	 * version part of a Groovy string-notation dependency
	 * ({@code 'group:artifact:version'}), or {@code null} if the element is not in
	 * such a position.
	 */
	public static @Nullable DependencyAndVersionLocation findGroovyVersionElement(PsiElement element,
			PropertyResolver scriptProperties) {

		if (!(element instanceof GrLiteral literal)) {
			return null;
		}

		GrMethodCall call = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		if (call == null) {
			return null;
		}
		String callName = getGroovyMethodName(call);
		if (!GradleUtils.isDependencySection(callName) && !GradleUtils.isPlatformSection(callName)) {
			// Not a standard dependency/platform call; check the plugin version pattern:
			// id 'pluginId' version 'x.y.z'
			return resolvePluginVersionLiteral(literal, call, scriptProperties);
		}

		// Try getValue() first (fast path for single-quoted and non-interpolated
		// double-quoted literals).
		// Fall back to toString() which preserves ${…} placeholders for interpolated
		// GStrings.
		String text = GroovyDslUtils.getText(literal);
		if (StringUtils.isEmpty(text)) {
			return null;
		}

		GrNamedArgument[] namedArguments = call.getNamedArguments();
		if (namedArguments.length > 2 && literal.getParent() instanceof GrNamedArgument namedArgument) {
			String labelName = namedArgument.getLabelName();
			if ("version".equals(labelName)) {
				NamedDependencyDeclaration declaration = GradleParser.parseMapDependency(call, namedArguments,
						scriptProperties);
				if (declaration.isComplete()) {
					GradleDependency dependency = declaration.toDependency(scriptProperties);
					return new DependencyAndVersionLocation(dependency, declaration.getRequiredVersionLiteral());
				}
			}
		}

		String[] parts = text.split(":");
		if (parts.length < 3) {
			return null;
		}
		GradleDependency dependency = GradleDependency.parse(text);
		if (dependency == null || !dependency.getVersionSource().isDefined()) {
			return null;
		}

		return new DependencyAndVersionLocation(dependency, literal);
	}

	/**
	 * Returns a {@link DependencyAndVersionLocation} when {@code literal} is the
	 * version value in a Groovy plugin declaration of the form
	 * {@code id 'pluginId' version 'x.y.z'} inside a {@code plugins {}} block, or
	 * {@code null} otherwise.
	 * <p>The plugin ID is used as both {@link ArtifactId#groupId()} and
	 * {@link ArtifactId#artifactId()}, matching the convention used throughout the
	 * rest of the Gradle plugin support.
	 */
	public static @Nullable DependencyAndVersionLocation resolvePluginVersionLiteral(GrLiteral literal,
			GrMethodCall call,
			PropertyResolver scriptProperties) {
		// The 'x.y.z' literal is an argument of the outer `version` method call.
		// call.invokedExpression must be a `version` GrReferenceExpression whose
		// qualifier
		// is the inner `id 'pluginId'` method call.
		if (!(call.getInvokedExpression() instanceof GrReferenceExpression versionRef)) {
			return null;
		}
		if (!"version".equals(versionRef.getReferenceName())) {
			return null;
		}

		if (!(versionRef.getQualifierExpression() instanceof GrMethodCall idCall) || !isInsidePluginsBlock(idCall)) {
			return null;
		}

		if (!GradleUtils.isPlugin(getGroovyMethodName(idCall))) {
			return null;
		}

		PluginId id = PluginId.fromMethodCall(idCall, scriptProperties);
		if (id == null) {
			return null;
		}
		ArtifactId artifactId = id.toValidatedArtifactId();
		if (artifactId == null) {
			return null;
		}
		String version = GroovyDslUtils.getText(id.version);
		PropertyExpression versionExpression = PropertyExpression.from(version);

		return new DependencyAndVersionLocation(GradleDependency.of(artifactId, versionExpression), literal);
	}

	public static @Nullable PsiPropertyValueElement resolvePropertyLocation(GrLiteral literal) {

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
		return new PsiPropertyValueElement(literal, key, value);
	}

	/**
	 * Detects {@code ext { key = 'value' }} and {@code ext.key = 'value'} forms.
	 */
	public static @Nullable PsiPropertyValueElement tryExtAssignmentValue(GrLiteral literal) {

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
		return new PsiPropertyValueElement(literal, key, value);
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
	 * Returns the required text associated with {@code expression} or throw
	 * {@link IllegalArgumentException} if the text could not be obtained.
	 *
	 * @return the required text.
	 * @throws IllegalArgumentException if the expression is not supported.
	 */
	static String getRequiredText(GrExpression expression) {

		Assert.notNull(expression, "Expression must not be null");

		String text = getText(expression);

		if (text == null) {
			throw new IllegalArgumentException(
					"Unexpected expression: %s (%s)".formatted(expression, expression.getClass()
							.getName()));
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
	 * Returns the string content of a Groovy literal, preserving any ${…}
	 * placeholders in interpolated GStrings.
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
		if (StringUtils.isEmpty(name)) {
			return false;
		}
		if ("alias".equals(name)) {
			return true;
		}
		if (GradleUtils.isPlugin(name) && isInsidePluginsBlock(call)) {
			return true;
		}
		return GradleUtils.isDependencySection(name) || GradleUtils.isPlatformSection(name);
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
	static void updateText(GrLiteral lit, String text) {

		String content = lit.getText();

		if (!StringUtils.hasText(text) || text.length() < 2) {
			return;
		}

		char quote = content.charAt(0);
		// Use the same quote character (single or double) that was originally used.
		String newLiteralText = quote + text + quote;
		lit.updateText(newLiteralText);
	}

	record PluginId(GrLiteral id, GrLiteral version, String resolvedPluginId) {

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

			GrLiteral idLiteral = null;
			String resolvedId = null;
			GrLiteral version = null;
			boolean sawVersion = false;

			for (GroovyPsiElement arg : call.getArgumentList().getAllArguments()) {

				if (idLiteral == null && arg instanceof GrLiteral literal) {
					String raw = GroovyDslUtils.getText(literal);
					String resolved = properties.resolvePlaceholders(raw);
					if (!idPredicate.test(resolved)) {
						return null;
					}
					idLiteral = literal;
					resolvedId = resolved;
				}

				if (idLiteral != null && version == null) {
					if (!sawVersion && arg instanceof GrReferenceExpression ref && "version".equals(ref.getText())) {
						sawVersion = true;
					} else if (sawVersion && arg instanceof GrLiteral literal) {
						version = literal;
					}
				}
			}

			if (version == null) {
				PsiElement parent = call.getParent();
				if (parent instanceof GrReferenceExpression versionRef
						&& "version".equals(versionRef.getReferenceName())
						&& versionRef.getParent() instanceof GrMethodCall outerApp) {
					version = PsiTreeUtil.getChildOfType(outerApp.getArgumentList(), GrLiteral.class);
				}
			}

			if (idLiteral == null || version == null || resolvedId == null) {
				return null;
			}
			return new PluginId(idLiteral, version, resolvedId);
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

			if (!BuildFileParserSupport.isValidPluginId(resolvedPluginId)) {
				LOG.debug("Skipping plugin entry: cannot use resolved id '%s'".formatted(resolvedPluginId));
				return null;
			}
			return GradlePlugin.of(resolvedPluginId);
		}

	}

}
