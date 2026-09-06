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

import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Groovy {@code ext} assignment or top-level script variable.
 *
 * @author Mark Paluch
 */
sealed interface GroovyExtAssignment extends ExtraDeclaration {

	String EXT = "ext";

	String SET = "set";

	@Override
	GrLiteral getValueLiteral();

	/**
	 * Find the declaration whose value is this string literal.
	 * @return {@literal null} for unsupported declaration shapes.
	 */
	static @Nullable GroovyExtAssignment from(@Nullable PsiElement element) {

		if (!(element instanceof GrLiteral literal) || !(literal.getValue() instanceof String)) {
			return null;
		}

		SetCall setCall = SetCall.from(literal);
		if (setCall != null) {
			return setCall;
		}

		ExtAssignment extAssignment = ExtAssignment.from(literal);
		if (extAssignment != null) {
			return extAssignment;
		}

		return ScriptVariable.from(literal);
	}

	/**
	 * {@code ext { set('key', 'value') }} declaration.
	 */
	record SetCall(String getKey, GrLiteral getValueLiteral, GrMethodCall getDeclaration)
			implements GroovyExtAssignment {

		static @Nullable SetCall from(GrLiteral literal) {

			GrMethodCall setCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
			if (setCall == null || !SET.equals(GroovyDslUtils.getGroovyMethodName(setCall))
					|| !GroovyDslUtils.isInsideGroovyBlock(setCall, EXT::equals)) {
				return null;
			}

			PsiElement[] args = setCall.getArgumentList().getAllArguments();
			if (args.length < 2 || !(args[0] instanceof GrLiteral keyLiteral)
					|| !(args[1] instanceof GrLiteral valueLiteral)) {
				return null;
			}

			if (literal != valueLiteral) {
				return null;
			}

			String key = GroovyDslUtils.getText(keyLiteral);
			if (StringUtils.isEmpty(key)) {
				return null;
			}

			return new SetCall(key, literal, setCall);
		}

		@Override
		public String getValue() {
			return GroovyDslUtils.getText(getValueLiteral());
		}

	}

	/**
	 * {@code ext { key = 'value' }} or {@code ext.key = 'value'} declaration.
	 */
	record ExtAssignment(String getKey, GrLiteral getValueLiteral,
			GrAssignmentExpression getDeclaration) implements GroovyExtAssignment {

		static @Nullable ExtAssignment from(GrLiteral literal) {

			GrAssignmentExpression assign = PsiTreeUtil.getParentOfType(literal, GrAssignmentExpression.class);
			if (assign == null || assign.isOperatorAssignment() || assign.getRValue() != literal) {
				return null;
			}

			String key = extractKey(assign.getLValue(), literal);
			if (key == null) {
				return null;
			}

			return new ExtAssignment(key, literal, assign);
		}

		/**
		 * Extract an {@code ext} key.
		 * @param valueContext the value used to establish closure scope, or
		 * {@literal null} to use the left-hand side.
		 * @return {@literal null} if the assignment is not an {@code ext} property.
		 */
		static @Nullable String extractKey(@Nullable GrExpression lhs, @Nullable PsiElement valueContext) {

			if (!(lhs instanceof GrReferenceExpression ref)) {
				return null;
			}

			GrExpression qualifier = ref.getQualifierExpression();
			PsiElement anchor = valueContext != null ? valueContext : lhs;

			if (qualifier == null && GroovyDslUtils.isInsideGroovyBlock(anchor, EXT::equals)) {
				return ref.getReferenceName();
			}

			if (qualifier instanceof GrReferenceExpression qualRef
					&& EXT.equals(qualRef.getReferenceName())) {
				return ref.getReferenceName();
			}

			return null;
		}

		@Override
		public String getValue() {
			return GroovyDslUtils.getText(getValueLiteral());
		}

	}

	/**
	 * Top-level script variable declaration ({@code def key = 'v'} /
	 * {@code String key = 'v'}).
	 */
	record ScriptVariable(String getKey, GrLiteral getValueLiteral, GrVariable getDeclaration)
			implements GroovyExtAssignment {

		static @Nullable ScriptVariable from(GrLiteral literal) {

			GrVariableDeclaration declaration = PsiTreeUtil.getParentOfType(literal, GrVariableDeclaration.class);
			if (declaration == null || !(declaration.getParent() instanceof GroovyFile)) {
				return null;
			}

			for (GrVariable variable : declaration.getVariables()) {

				String name = variable.getName();
				if (variable.getInitializerGroovy() == literal && StringUtils.hasText(name)) {
					return new ScriptVariable(name, literal, variable);
				}
			}
			return null;
		}

		@Override
		public String getValue() {
			return GroovyDslUtils.getText(getValueLiteral());
		}

	}

}
