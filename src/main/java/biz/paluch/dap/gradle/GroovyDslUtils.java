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
import java.util.List;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
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
	public static @Nullable DependencyLocation findGroovyVersionElement(PsiElement element,
			PropertyResolver scriptProperties) {

		// Only process the GrLiteral node itself. Accepting any descendant (e.g. the
		// leaf
		// token inside a single-quoted string) would produce multiple hits per
		// dependency
		// declaration when iterating all PSI elements, leading to duplicate annotations
		// and
		// gutter icons. Callers that receive a leaf token (cursor position, completion)
		// must
		// first walk up to the containing GrLiteral before invoking this method.
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
		String text = GroovyDslUtils.toString(literal);
		if (StringUtils.isEmpty(text)) {
			return null;
		}

		String[] parts = text.split(":");
		if (parts.length < 3) {
			return null;
		}
		GradleDependency dependency = GradleDependency.parse(text);
		if (dependency == null || !dependency.getVersionSource().isDefined()) {
			return null;
		}

		return new DependencyLocation(literal, dependency);
	}

	/**
	 * Returns a {@link DependencyLocation} when {@code literal} is the version
	 * value in a Groovy plugin declaration of the form
	 * {@code id 'pluginId' version 'x.y.z'} inside a {@code plugins {}} block, or
	 * {@code null} otherwise.
	 * <p>The plugin ID is used as both {@link ArtifactId#groupId()} and
	 * {@link ArtifactId#artifactId()}, matching the convention used throughout the
	 * rest of the Gradle plugin support.
	 */
	public static @Nullable DependencyLocation resolvePluginVersionLiteral(GrLiteral literal, GrMethodCall call,
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
		String version = GroovyDslUtils.toString(id.version);
		PropertyExpression versionExpression = PropertyExpression.from(version);

		return new DependencyLocation(literal, GradleDependency.of(artifactId, versionExpression));
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

		String key = toString(keyLiteral);
		String value = GroovyDslUtils.toString(literal);
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

		String value = literal.getValue() instanceof String v ? v : GroovyDslUtils.toString(literal);
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

	public static String getGroovyMethodName(GrMethodCall call) {
		return call.getInvokedExpression().getText().trim();
	}

	/**
	 * Returns the string content of a Groovy literal, preserving any ${…}
	 * placeholders in interpolated GStrings.
	 * @param literal the literal to extract the string content from.
	 * @return the string value.
	 */
	public static String toString(GrLiteral literal) {

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
	static @Nullable GrMethodCall findEnclosingGroovyCatalogAccessorCall(PsiElement element) {

		for (PsiElement p = element; p != null && !(p instanceof PsiFile); p = p.getParent()) {

			if (!(p instanceof GrMethodCall call)) {
				continue;
			}
			if (!isGroovyCatalogConsumerCall(call)) {
				continue;
			}
			GrExpression arg = getFirstGroovyCatalogArgumentExpression(call);
			if (arg == null || !isGroovyLibsCatalogRootExpression(arg)) {
				continue;
			}
			if (!PsiTreeUtil.isAncestor(arg, element, false)) {
				continue;
			}
			return call;
		}
		return null;
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

	static boolean isGroovyLibsCatalogRootExpression(GrExpression expr) {

		List<String> segs = collectGroovyCatalogReferenceSegments(expr);
		return segs != null && !segs.isEmpty() && "libs".equals(segs.get(0));
	}

	static @Nullable List<String> collectGroovyCatalogReferenceSegments(GrExpression expr) {

		GrExpression e = unwrapGroovyParentheses(expr);
		if (e == null) {
			return null;
		}
		ArrayList<String> segments = new ArrayList<>();
		GrExpression cur = e;
		while (cur instanceof GrReferenceExpression ref) {
			String name = ref.getReferenceName();
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
		return segments;
	}

	/**
	 * {@code true} for PSI under a {@code libs.…} catalog argument except the root
	 * {@link GrExpression} of that argument (avoids duplicate gutter/annotator hits
	 * per segment).
	 */
	static boolean isRedundantGroovyCatalogHighlightAnchor(PsiElement element) {

		GrMethodCall catalogCall = findEnclosingGroovyCatalogAccessorCall(element);
		if (catalogCall == null) {
			return false;
		}
		GrExpression firstArg = getFirstGroovyCatalogArgumentExpression(catalogCall);
		return PsiTreeUtil.isAncestor(firstArg, element, false) && !firstArg.equals(element);
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
					String raw = GroovyDslUtils.toString(literal);
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
			return GroovyDslUtils.toString(version);
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
