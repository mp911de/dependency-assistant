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

import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Factory for {@link DependencySite} instances parsed from Groovy DSL plugin
 * declarations.
 *
 * @author Mark Paluch
 */
class GroovyPluginDependencySite {

	private GroovyPluginDependencySite() {
	}

	/**
	 * Parse a Groovy plugin declaration.
	 */
	static @Nullable DependencySite fromMethodCall(GrMethodCall call,
			PropertyResolver propertyResolver) {

		GrLiteral idLiteral = findFirstLiteralArgument(call);
		if (idLiteral == null) {
			return null;
		}

		String id = propertyResolver.resolvePlaceholders(GroovyDslUtils.getText(idLiteral));
		GrLiteral versionLiteral = findInlineVersionLiteral(call);
		if (versionLiteral == null) {
			versionLiteral = findChainedVersionLiteral(call);
		}

		if (versionLiteral == null || !GradlePluginId.isValidPluginId(id)) {
			return null;
		}

		String versionText = GroovyDslUtils.getText(versionLiteral);
		Expression expression = Expression.from(versionText);

		return GradleDependency.of(GradlePluginId.of(id), expression).toDependencySite(call, versionLiteral);
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
					&& GradleUtils.VERSION.equals(referenceExpression.getReferenceName())) {
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

		if (!(call.getParent() instanceof GrReferenceExpression versionRef)
				|| !GradleUtils.VERSION.equals(versionRef.getReferenceName())
				|| !(versionRef.getParent() instanceof GrMethodCall outerCall)) {
			return null;
		}

		return findFirstLiteralArgument(outerCall);
	}

}
