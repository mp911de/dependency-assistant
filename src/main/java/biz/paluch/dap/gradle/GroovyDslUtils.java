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

import biz.paluch.dap.artifact.ArtifactId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

import org.springframework.util.StringUtils;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Utility methods for Groovy DSL.
 *
 * @author Mark Paluch
 */
class GroovyDslUtils {

	/**
	 * Returns the first literal string argument.
	 */
	static @Nullable String firstLiteralString(PsiElement[] args) {
		for (PsiElement arg : args) {
			if (arg instanceof GrLiteral lit && lit.getValue() instanceof String s) {
				return s;
			}
		}
		return null;
	}

	static boolean isInsideGradleBlock(PsiElement element, String blockName) {
		PsiElement parent = element.getParent();
		while (parent != null) {
			if (parent instanceof GrMethodCall parentCall) {
				String name = getGroovyMethodName(parentCall);
				if (blockName.equals(name)) {
					return true;
				}
			}
			parent = parent.getParent();
		}
		return false;
	}

	/**
	 * Returns a {@link PropertyVersionLocation} when {@code element} is the <em>value</em> literal of a Groovy
	 * {@code ext} property declaration, or {@code null} otherwise.
	 * <p>
	 * The three recognised forms are:
	 * <ul>
	 * <li>{@code ext { set('key', 'value') }} — set-call form</li>
	 * <li>{@code ext { key = 'value' }} — assignment inside an {@code ext} closure</li>
	 * <li>{@code ext.key = 'value'} — dot-qualified assignment</li>
	 * </ul>
	 */
	public static @Nullable PropertyVersionLocation findGroovyExtPropertyVersionElement(PsiElement element) {

		if (!(element instanceof GrLiteral literal)) {
			return null;
		}

		PropertyVersionLocation setLoc = tryExtSetCallValue(literal);
		if (setLoc != null) {
			return setLoc;
		}
		return tryExtAssignmentValue(literal);
	}

	/**
	 * Returns the version value element for a {@code gradle.properties} property if the element is a property value that
	 * maps to a known dependency artifact in the cache.
	 */
	public static @Nullable PropertyVersionLocation findPropertiesVersionElement(PsiElement element) {

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
		if (valueElement == null || !PsiTreeUtil.isAncestor(valueElement, element, false)) {
			return null;
		}

		return new PropertyVersionLocation(psiElement, property.getKey(), property.getValue());
	}

	/**
	 * Returns the version segment {@link PsiElement} if the caret is inside the version part of a Groovy string-notation
	 * dependency ({@code 'group:artifact:version'}), or {@code null} if the element is not in such a position.
	 * <p>
	 * Handles both plain string literals and GString templates with {@code ${property}} version references. When the
	 * version segment is an interpolation placeholder the returned {@link VersionLocation#isPropertyReference()} is
	 * {@code true} and {@link VersionLocation#rawVersion()} contains the bare property name (without {@code ${}}).
	 */
	public static @Nullable VersionLocation findGroovyVersionElement(PsiElement element) {

		// Only process the GrLiteral node itself. Accepting any descendant (e.g. the leaf
		// token inside a single-quoted string) would produce multiple hits per dependency
		// declaration when iterating all PSI elements, leading to duplicate annotations and
		// gutter icons. Callers that receive a leaf token (cursor position, completion) must
		// first walk up to the containing GrLiteral before invoking this method.
		if (!(element instanceof GrLiteral literal)) {
			return null;
		}

		GrMethodCall call = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		if (call == null) {
			return null;
		}
		String callName = getGroovyMethodName(call);
		if (!GradleUtils.DEPENDENCY_CONFIGS.contains(callName) && !GradleUtils.PLATFORM_FUNCTIONS.contains(callName)) {
			// Not a standard dependency/platform call; check the plugin version pattern:
			// id 'pluginId' version 'x.y.z'
			return tryPluginVersionLiteral(literal, call);
		}

		// Try getValue() first (fast path for single-quoted and non-interpolated double-quoted literals).
		// Fall back to toString() which preserves ${…} placeholders for interpolated GStrings.
		String text = literal.getValue() instanceof String s ? s : GroovyDslUtils.toString(literal);
		if (text == null) {
			return null;
		}

		String[] parts = text.split(":");
		if (parts.length < 3) {
			return null;
		}

		String rawVersion = parts[2].trim();
		boolean isPropertyRef = rawVersion.startsWith("${") && rawVersion.endsWith("}");
		if (isPropertyRef) {
			// Strip the ${…} wrapper; VersionUpgradeLookupService will resolve via getProjectProperty.
			rawVersion = rawVersion.substring(2, rawVersion.length() - 1).trim();
		}

		return new VersionLocation(literal, ArtifactId.of(parts[0].trim(), parts[1].trim()), rawVersion, isPropertyRef);
	}

	/**
	 * Returns a {@link VersionLocation} when {@code literal} is the version value in a Groovy plugin declaration of the
	 * form {@code id 'pluginId' version 'x.y.z'} inside a {@code plugins {}} block, or {@code null} otherwise.
	 * <p>
	 * The plugin ID is used as both {@link ArtifactId#groupId()} and {@link ArtifactId#artifactId()}, matching the
	 * convention used throughout the rest of the Gradle plugin support.
	 */
	public static @Nullable VersionLocation tryPluginVersionLiteral(GrLiteral literal, GrMethodCall call) {
		// The 'x.y.z' literal is an argument of the outer `version` method call.
		// call.invokedExpression must be a `version` GrReferenceExpression whose qualifier
		// is the inner `id 'pluginId'` method call.
		if (!(call.getInvokedExpression() instanceof GrReferenceExpression versionRef)) {
			return null;
		}
		if (!"version".equals(versionRef.getReferenceName())) {
			return null;
		}
		if (!(versionRef.getQualifierExpression() instanceof GrMethodCall idCall)) {
			return null;
		}
		if (!"id".equals(getGroovyMethodName(idCall))) {
			return null;
		}
		if (!isInsideGradleBlock(idCall, "plugins")) {
			return null;
		}
		String pluginId = firstLiteralString(idCall.getArgumentList().getAllArguments());
		if (pluginId == null) {
			return null;
		}
		String version = literal.getValue() instanceof String v ? v : GroovyDslUtils.toString(literal);
		if (version == null) {
			return null;
		}
		return new VersionLocation(literal, ArtifactId.of(pluginId, pluginId), version, false);
	}

	public static @Nullable PropertyVersionLocation tryExtSetCallValue(GrLiteral literal) {

		GrMethodCall setCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);

		if (setCall == null || !"set".equals(getGroovyMethodName(setCall))) {
			return null;
		}

		PsiElement[] args = setCall.getArgumentList().getAllArguments();
		if (args.length < 2 || !(args[0] instanceof GrLiteral keyLit) || !(args[1] instanceof GrLiteral valLit)) {
			return null;
		}

		// Only process the value argument (args[1]); reject the key literal (args[0]).
		if (literal != valLit) {
			return null;
		}

		String key = keyLit.getValue() instanceof String k ? k : null;
		String value = literal.getValue() instanceof String v ? v : GroovyDslUtils.toString(literal);
		if (key == null || value == null) {
			return null;
		}
		return new PropertyVersionLocation(literal, key, value);
	}

	/** Detects {@code ext { key = 'value' }} and {@code ext.key = 'value'} forms. */
	public static @Nullable PropertyVersionLocation tryExtAssignmentValue(GrLiteral literal) {

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
		if (qualifier == null && isInsideGroovyExtBlock(literal)) {
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
		return new PropertyVersionLocation(literal, key, value);
	}

	/**
	 * Returns {@code true} when {@code element} is a descendant of a Groovy {@code ext { }} closure.
	 */
	public static boolean isInsideGroovyExtBlock(PsiElement element) {

		PsiElement parent = element.getParent();

		while (parent != null) {
			if (parent instanceof GrClosableBlock) {
				PsiElement blockParent = parent.getParent();
				if (blockParent instanceof GrMethodCall call && "ext".equals(getGroovyMethodName(call))) {
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

	public static String toString(GrLiteral lit) {

		if (lit.getValue() instanceof String s) {
			return s;
		}

		StringBuilder builder = new StringBuilder();

		for (PsiElement child : lit.getChildren()) {
			builder.append(child.getText());
		}

		return builder.toString();
	}

	// -------------------------------------------------------------------------
	// Version catalog (Groovy {@code libs.…})
	// -------------------------------------------------------------------------

	static @Nullable GrExpression unwrapGroovyParentheses(@Nullable GrExpression expr) {

		GrExpression e = expr;
		while (e instanceof GrParenthesizedExpression p) {
			e = p.getOperand();
		}
		return e;
	}

	/**
	 * Innermost {@code alias}/{@code id}/{@code implementation}/… call whose first argument is a {@code libs.…} reference
	 * chain and that contains {@code element}.
	 */
	static @Nullable GrMethodCall findEnclosingGroovyCatalogAccessorCall(PsiElement element) {

		for (PsiElement p = element; p != null; p = p.getParent()) {
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
		if (!StringUtils.hasText(name)) {
			return false;
		}
		if ("alias".equals(name)) {
			return true;
		}
		if ("id".equals(name) && isInsideGradleBlock(call, "plugins")) {
			return true;
		}
		return GradleUtils.DEPENDENCY_CONFIGS.contains(name) || GradleUtils.PLATFORM_FUNCTIONS.contains(name);
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
	 * {@code true} for PSI under a {@code libs.…} catalog argument except the root {@link GrExpression} of that argument
	 * (avoids duplicate gutter/annotator hits per segment).
	 */
	static boolean isRedundantGroovyCatalogHighlightAnchor(PsiElement element) {

		GrMethodCall catalogCall = findEnclosingGroovyCatalogAccessorCall(element);
		if (catalogCall == null) {
			return false;
		}
		GrExpression firstArg = getFirstGroovyCatalogArgumentExpression(catalogCall);
		return firstArg != null && PsiTreeUtil.isAncestor(firstArg, element, false) && !firstArg.equals(element);
	}

	/**
	 * Location of a version string in a dependency declaration.
	 */
	public record VersionLocation(PsiElement element, ArtifactId artifactId, String rawVersion,
			boolean isPropertyReference) {
	}

	/**
	 * Location of a version string inside a {@code gradle.properties} entry.
	 */
	public record PropertyVersionLocation(PsiElement element, String propertyKey, String propertyValue) {
	}
}
