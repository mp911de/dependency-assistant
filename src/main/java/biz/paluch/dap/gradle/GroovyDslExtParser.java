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

import java.util.LinkedHashMap;
import java.util.Map;

import biz.paluch.dap.support.PropertyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;

/**
 * Parser methods for Gradle {@code ext} property declarations using Groovy DSL.
 *
 * @author Mark Paluch
 */
class GroovyDslExtParser {

	/**
	 * Parse all Groovy {@code ext} property declarations from the given file.
	 * <p>
	 * Three forms are supported:
	 *
	 * <pre>
	 * ext {
	 *     springVersion = '6.1.0'              // assignment form
	 *     set('springVersion', '6.1.0')        // set() call form
	 * }
	 * ext.springVersion = '6.1.0'             // dot-qualified assignment form
	 * </pre>
	 *
	 * @param file a Groovy {@code .gradle} file; must not be {@literal null}.
	 * @return a map of property key to literal string value.
	 */
	public static Map<String, PropertyValue> parseExtProperties(PsiFile file) {

		Map<String, PropertyValue> elements = new LinkedHashMap<>();
		SyntaxTraverser.psiTraverser(file)
				.filter(it -> it instanceof GrMethodCall || it instanceof GrAssignmentExpression)
				.forEach(it -> {
// ext { key = 'value' } or ext { set('key', 'value') }
					if (it instanceof GrMethodCall call
							&& GradleUtils.EXT.equals(GroovyDslUtils.getGroovyMethodName(call))) {
						elements.putAll(collectExtClosureProperties(call));
					}
					// ext.key = 'value'
					if (it instanceof GrAssignmentExpression assign && !assign.isOperatorAssignment()) {
						elements.putAll(collectExtDotProperty(assign));
					}
				});

		return elements;
	}

	/**
	 * Collect all Groovy {@code ext} property declarations from the given file as
	 * plain string values.
	 * <p>
	 * Supported syntax variants:
	 * <pre class="code">
	 * ext {
	 *     springVersion = '6.1.0'              // assignment form
	 *     set('springVersion', '6.1.0')        // set() call form
	 * }
	 * ext.springVersion = '6.1.0'             // dot-qualified assignment form
	 * </pre>
	 *
	 * @param file a Groovy {@code .gradle} file.
	 * @return a map of property key to literal string value.
	 */
	public static Map<String, String> getExtProperties(PsiFile file) {

		Map<String, String> elements = new LinkedHashMap<>();
		parseExtProperties(file).forEach((k, v) -> elements.put(k, v.getValue()));

		return elements;
	}

	/**
	 * Parse script-level variable declarations from the given file.
	 * <p>
	 * Supported forms:
	 *
	 * <pre class="code">
	 * def springVersion = '6.1.0'
	 * val springVersion = '6.1.0'
	 * String springVersion = '6.1.0'
	 * </pre>
	 *
	 * @param file a Groovy {@code .gradle} file.
	 * @return a map of variable name to its literal string value.
	 */
	public static Map<String, PropertyValue> parseLocalVariables(PsiFile file) {

		Map<String, PropertyValue> elements = new LinkedHashMap<>();

		SyntaxTraverser.psiTraverser(file)
				.expand(it -> !(it instanceof GrVariableDeclaration))
				.filter(GrVariableDeclaration.class)
				.forEach(decl -> {

					if (!(decl.getParent() instanceof GroovyFile)) {
						return;
					}

					for (GrVariable variable : decl.getVariables()) {
						String name = variable.getName();
						GrExpression initializer = variable.getInitializerGroovy();
						if (initializer instanceof GrLiteral literal
								&& isStringLiteral(literal)) {
							elements.put(name,
									new PropertyValue(name, GroovyDslUtils.getRequiredText(literal), literal));
							continue;
						}

						if (initializer instanceof GrString gstr && gstr.getInjections().length == 0) {
							GrLiteral innerLiteral = PsiTreeUtil.findChildOfType(gstr, GrLiteral.class);
							if (innerLiteral != null && isStringLiteral(innerLiteral)) {
								elements.put(name,
										new PropertyValue(name, GroovyDslUtils.getRequiredText(innerLiteral),
												innerLiteral));
							}
						}
					}
				});

		return elements;
	}

	private static Map<String, PropertyValue> collectExtClosureProperties(GrMethodCall extCall) {

		Map<String, PropertyValue> elements = new LinkedHashMap<>();

		JBIterable.of(extCall.getClosureArguments())
				.flatMap(SyntaxTraverser::psiTraverser)
				.forEach(child -> {

					// Assignment form: springVersion = '6.1.0'
					if (child instanceof GrAssignmentExpression assign && !assign.isOperatorAssignment()) {
						GrExpression lhs = assign.getLValue();
						GrExpression rhs = assign.getRValue();
						if (lhs instanceof GrReferenceExpression ref && ref.getQualifierExpression() == null) {
							String key = ref.getReferenceName();
							if (key != null && rhs instanceof GrLiteral literal && isStringLiteral(literal)) {
								elements.put(key,
										new PropertyValue(key, GroovyDslUtils.getRequiredText(literal), literal));
							}
						}
					}

					// set() call form: set('springVersion', '6.1.0')
					if (child instanceof GrMethodCall setCall
							&& GradleUtils.SET.equals(GroovyDslUtils.getGroovyMethodName(setCall))) {
						PsiElement[] args = setCall.getArgumentList().getAllArguments();
						if (args.length >= 2 && args[0] instanceof GrLiteral keyLit
								&& keyLit.getValue() instanceof String key
								&& args[1] instanceof GrLiteral literal && isStringLiteral(literal)) {
							elements.put(key,
									new PropertyValue(key, GroovyDslUtils.getRequiredText(literal), literal));
						}
					}
				});

		return elements;
	}

	private static Map<String, PropertyValue> collectExtDotProperty(GrAssignmentExpression assign) {

		GrExpression lhs = assign.getLValue();
		GrExpression rhs = assign.getRValue();

		if (!(lhs instanceof GrReferenceExpression ref)) {
			return Map.of();
		}

		Map<String, PropertyValue> elements = new LinkedHashMap<>();
		GrExpression qualifier = ref.getQualifierExpression();
		if (qualifier instanceof GrReferenceExpression qualRef
				&& GradleUtils.EXT.equals(qualRef.getReferenceName())) {
			String key = ref.getReferenceName();
			if (key != null && rhs instanceof GrLiteral literal && isStringLiteral(literal)) {
				elements.put(key, new PropertyValue(key, GroovyDslUtils.getRequiredText(literal), literal));
			}
		}

		return elements;
	}

	private static boolean isStringLiteral(GrLiteral literal) {
		return literal.getValue() instanceof String;
	}

}
