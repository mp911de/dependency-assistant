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

import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Interface representing Groovy DSL {@code ext} property declarations.
 * <p>Captures the four supported declaration shapes behind one factory:
 * <ul>
 * <li>{@code ext { set('key', 'value') }} — {@link SetCall set-call form}</li>
 * <li>{@code ext { key = 'value' }} — {@link ExtAssignment plain assignment
 * inside an {@code ext} closure}</li>
 * <li>{@code ext.key = 'value'} — {@link ExtAssignment dot-qualified
 * assignment}</li>
 * <li>{@code def key = 'value'} / {@code String key = 'value'} —
 * {@link ScriptVariable top-level script variable}</li>
 * </ul>
 *
 * @author Mark Paluch
 */
sealed interface GroovyExtAssignment extends ExtraDeclaration {

	/**
	 * @return the literal expression holding the value.
	 */
	@Override
	GrLiteral getValueLiteral();

	/**
	 * Detect a Groovy {@code ext} property declaration anchored at {@code element}.
	 * {@code element} is expected to be the value literal of the declaration.
	 *
	 * @param element the candidate value PSI element; can be {@literal null}.
	 * @return the resolved assignment, or {@literal null} if {@code element} is not
	 * the value literal of a detected {@code ext} declaration shape.
	 */
	static @Nullable GroovyExtAssignment from(@Nullable PsiElement element) {

		if (!(element instanceof GrLiteral literal)) {
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
			if (setCall == null || !"set".equals(GroovyDslUtils.getGroovyMethodName(setCall))) {
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
		 * Extract the property key from an {@code ext} assignment LHS.
		 * <p>The {@code valueContext} is used to determine whether the assignment sits
		 * inside an {@code ext { }} closure when the LHS is a plain reference.
		 *
		 * @param lhs the assignment left-hand side.
		 * @param valueContext the right-hand side literal, used as anchor for the
		 * {@code ext} closure check; may be {@literal null} when the LHS is the only
		 * available context (e.g. when called from updaters that don't yet know the
		 * RHS).
		 * @return the resolved key, or {@literal null} if {@code lhs} is not an
		 * {@code ext} reference shape.
		 */
		static @Nullable String extractKey(@Nullable GrExpression lhs, @Nullable PsiElement valueContext) {

			if (!(lhs instanceof GrReferenceExpression ref)) {
				return null;
			}

			GrExpression qualifier = ref.getQualifierExpression();
			PsiElement anchor = valueContext != null ? valueContext : lhs;

			if (qualifier == null && GroovyDslUtils.isInsideGroovyBlock(anchor, "ext"::equals)) {
				return ref.getReferenceName();
			}

			if (qualifier instanceof GrReferenceExpression qualRef && "ext".equals(qualRef.getReferenceName())) {
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
				return new ScriptVariable(name, literal, variable);
			}
			return null;
		}

		@Override
		public String getValue() {
			return GroovyDslUtils.getText(getValueLiteral());
		}

	}

}
