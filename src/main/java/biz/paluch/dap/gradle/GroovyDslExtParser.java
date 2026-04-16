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

import biz.paluch.dap.support.PsiPropertyValueElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * Parser methods for Gradle {@code ext} property declarations using Groovy DSL.
 *
 * @author Mark Paluch
 */
class GroovyDslExtParser {

	/**
	 * Parses all Groovy {@code ext} property declarations from the given file.
	 * <p>Three forms are recognised:
	 *
	 * <pre>
	 * ext {
	 *     springVersion = '6.1.0'              // assignment form
	 *     set('springVersion', '6.1.0')        // set() call form
	 * }
	 * ext.springVersion = '6.1.0'             // dot-qualified assignment form
	 * </pre>
	 *
	 * @param file a Groovy {@code .gradle} file
	 * @return a map of property key to literal string value.
	 */
	public static Map<String, PsiPropertyValueElement> parseExtProperties(PsiFile file) {

		Map<String, PsiPropertyValueElement> elements = new LinkedHashMap<>();
		SyntaxTraverser.psiTraverser(file)
				.filter(it -> it instanceof GrMethodCall || it instanceof GrAssignmentExpression)
				.forEach(it -> {
// ext { key = 'value' } or ext { set('key', 'value') }
					if (it instanceof GrMethodCall call && "ext".equals(GroovyDslUtils.getGroovyMethodName(call))) {
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
	 * Parses all Groovy {@code ext} property declarations from the given file.
	 * <p>This method supports the following syntax variants: <pre>
	 * ext {
	 *     springVersion = '6.1.0'              // assignment form
	 *     set('springVersion', '6.1.0')        // set() call form
	 * }
	 * ext.springVersion = '6.1.0'             // dot-qualified assignment form
	 * </pre>
	 *
	 * @param file a Groovy {@code .gradle} file
	 * @return a map of property key to literal string value.
	 */
	public static Map<String, String> getExtProperties(PsiFile file) {

		Map<String, String> elements = new LinkedHashMap<>();
		parseExtProperties(file).forEach((k, v) -> elements.put(k, v.propertyValue()));

		return elements;
	}

	private static Map<String, PsiPropertyValueElement> collectExtClosureProperties(GrMethodCall extCall) {

		Map<String, PsiPropertyValueElement> elements = new LinkedHashMap<>();

		JBIterable.of(extCall.getClosureArguments())
				.flatMap(SyntaxTraverser::psiTraverser)
				.forEach(child -> {


					// Assignment form: springVersion = '6.1.0'
					if (child instanceof GrAssignmentExpression assign && !assign.isOperatorAssignment()) {
						GrExpression lhs = assign.getLValue();
						GrExpression rhs = assign.getRValue();
						if (lhs instanceof GrReferenceExpression ref && ref.getQualifierExpression() == null) {
							String key = ref.getReferenceName();
							if (key != null && rhs instanceof GrLiteral literal && GroovyDslUtils.hasText(literal)) {
								elements.put(key,
										new PsiPropertyValueElement(literal, key,
												GroovyDslUtils.getRequiredText(literal)));
							}
						}
					}

					// set() call form: set('springVersion', '6.1.0')
					if (child instanceof GrMethodCall setCall
							&& "set".equals(GroovyDslUtils.getGroovyMethodName(setCall))) {
						PsiElement[] args = setCall.getArgumentList().getAllArguments();
						if (args.length >= 2 && args[0] instanceof GrLiteral keyLit
								&& keyLit.getValue() instanceof String key
								&& args[1] instanceof GrLiteral literal && GroovyDslUtils.hasText(literal)) {
							elements.put(key,
									new PsiPropertyValueElement(literal, key, GroovyDslUtils.getRequiredText(literal)));
						}
					}
				});

		return elements;
	}

	private static Map<String, PsiPropertyValueElement> collectExtDotProperty(GrAssignmentExpression assign) {

		GrExpression lhs = assign.getLValue();
		GrExpression rhs = assign.getRValue();

		if (!(lhs instanceof GrReferenceExpression ref)) {
			return Map.of();
		}

		Map<String, PsiPropertyValueElement> elements = new LinkedHashMap<>();
		GrExpression qualifier = ref.getQualifierExpression();
		if (qualifier instanceof GrReferenceExpression qualRef && "ext".equals(qualRef.getReferenceName())) {
			String key = ref.getReferenceName();
			if (key != null && rhs instanceof GrLiteral literal && GroovyDslUtils.hasText(literal)) {
				elements.put(key, new PsiPropertyValueElement(literal, key, GroovyDslUtils.getRequiredText(literal)));
			}
		}

		return elements;
	}

}
