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

import java.util.LinkedHashMap;
import java.util.Map;

import biz.paluch.dap.support.PropertyValue;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * Parser methods for Gradle {@code ext} property declarations using Groovy DSL.
 *
 * <p>Declaration shapes are recognized by {@link GroovyExtAssignment}; this
 * parser only traverses the file.
 *
 * @author Mark Paluch
 */
class GroovyDslExtParser {

	/**
	 * Parse all Groovy {@code ext} property declarations from the given file.
	 * <p>Three forms are supported:
	 *
	 * <pre>
	 * ext {
	 *     springVersion = '6.1.0'              // assignment form
	 *     set('springVersion', '6.1.0')        // set() call form
	 * }
	 * ext.springVersion = '6.1.0'             // dot-qualified assignment form
	 * </pre>
	 *
	 * @param file a Groovy {@code .gradle} file.
	 * @return a map of property key to its {@link PropertyValue}.
	 */
	public static Map<String, PropertyValue> parseExtProperties(PsiFile file) {
		return parse(file, it -> !(it instanceof GroovyExtAssignment.ScriptVariable));
	}

	/**
	 * Collect all Groovy {@code ext} property declarations from the given file as
	 * plain string values.
	 *
	 * @param file a Groovy {@code .gradle} file.
	 * @return a map of property key to literal string value.
	 * @see #parseExtProperties(PsiFile)
	 */
	public static Map<String, String> getExtProperties(PsiFile file) {

		Map<String, String> elements = new LinkedHashMap<>();
		parseExtProperties(file).forEach((k, v) -> elements.put(k, v.getValue()));

		return elements;
	}

	/**
	 * Parse script-level variable declarations from the given file.
	 * <p>Supported forms:
	 *
	 * <pre class="code">
	 * def springVersion = '6.1.0'
	 * String springVersion = '6.1.0'
	 * </pre>
	 *
	 * @param file a Groovy {@code .gradle} file.
	 * @return a map of variable name to its {@link PropertyValue}.
	 */
	public static Map<String, PropertyValue> parseLocalVariables(PsiFile file) {
		return parse(file, GroovyExtAssignment.ScriptVariable.class::isInstance);
	}

	private static Map<String, PropertyValue> parse(PsiFile file, Condition<GroovyExtAssignment> filter) {

		Map<String, PropertyValue> elements = new LinkedHashMap<>();

		SyntaxTraverser.psiTraverser(file)
				.filter(GrLiteral.class)
				.filterMap(GroovyExtAssignment::from)
				.filter(filter)
				.forEach(it -> elements.put(it.getKey(),
						new PropertyValue(it.getKey(), it.getValue(), it.getValueLiteral())));

		return elements;
	}

}
