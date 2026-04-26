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

import biz.paluch.dap.support.PropertyResolver;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Factory for {@link PluginId} instances parsed from Groovy DSL plugin
 * declarations.
 * <p>Three forms appear in Gradle Groovy DSL {@code plugins {}} blocks:
 *
 * <ol>
 * <li>Flat command args (non-chained): {@code id 'x' version 'y'} - single
 * {@code GrApplicationStatement(id, ['x', version_ref, 'y'])}</li>
 * <li>Chained command expression (most common): {@code id 'x' version 'y'} -
 * inner {@code GrApplicationStatement(id, ['x'])} whose parent is the
 * {@code GrReferenceExpression('version')} whose parent is the outer
 * {@code GrApplicationStatement(version, ['y'])}</li>
 * <li>Explicit-paren + command chain: {@code id('x') version 'y'} - same
 * chained structure as (2) but inner uses explicit parens</li>
 * </ol>
 *
 * @author Mark Paluch
 */
final class GroovyPluginIds {

	private GroovyPluginIds() {
	}

	/**
	 * Parse a Groovy plugin declaration, accepting ids that satisfy
	 * {@code idPredicate}.
	 */
	static @Nullable PluginId fromMethodCall(GrMethodCall call,
			PropertyResolver properties) {

		GrLiteral idLiteral = findFirstLiteralArgument(call);
		if (idLiteral == null) {
			return null;
		}

		String resolvedId = properties.resolvePlaceholders(GroovyDslUtils.getText(idLiteral));
		GrLiteral versionLiteral = findInlineVersionLiteral(call);
		if (versionLiteral == null) {
			versionLiteral = findChainedVersionLiteral(call);
		}

		if (versionLiteral == null) {
			return null;
		}

		String versionText = GroovyDslUtils.getText(versionLiteral);
		return new PluginId(idLiteral, versionLiteral, resolvedId, versionText, null);
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

		if (!(call.getParent() instanceof GrReferenceExpression versionRef)
				|| !"version".equals(versionRef.getReferenceName())
				|| !(versionRef.getParent() instanceof GrMethodCall outerCall)) {
			return null;
		}

		return findFirstLiteralArgument(outerCall);
	}

}
