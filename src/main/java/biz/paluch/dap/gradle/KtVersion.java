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

import biz.paluch.dap.support.Expression;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLambdaExpression;
import org.jspecify.annotations.Nullable;

/**
 * Effective version of a Kotlin DSL dependency declaration whose version is
 * given through a {@code version(...)} call or a {@code version { ... }}
 * constraint block.
 *
 * <p>A direct version argument takes precedence. Otherwise the strongest
 * constraint wins: {@code strictly}, then {@code require}, then {@code prefer}.
 * Range constraints are skipped because callers need a single upgradeable
 * version value. The last declaration of a constraint name wins.
 *
 * @author Mark Paluch
 */
class KtVersion {

	private final Expression expression;

	private final KtExpression element;

	private KtVersion(Expression expression, KtExpression element) {
		this.expression = expression;
		this.element = element;
	}

	/**
	 * Determine the effective version declared within the given dependency call.
	 * @param dependency the dependency declaration to inspect.
	 * @return the effective version, or {@literal null} if the declaration carries
	 * no usable version.
	 */
	public static @Nullable KtVersion fromDependency(KtCallElement dependency) {

		KtCallElement versionCall = SyntaxTraverser.psiTraverser(dependency)
				.filter(KtCallElement.class)
				.filter(it -> GradleUtils.VERSION.equals(KotlinDslUtils.getKotlinCallName(it)))
				.first();

		if (versionCall == null) {
			return null;
		}

		KtExpression argument = KotlinDslUtils.getFirstValueArgument(versionCall);
		KtLiterals literals = KtLiterals.from(argument);
		if (argument != null && literals.hasText()) {
			return new KtVersion(literals.toExpression(), argument);
		}

		Map<String, Constraint> constraints = constraints(versionCall);
		for (String name : GradleVersionConstraint.PRECEDENCE) {

			Constraint constraint = constraints.get(name);
			if (constraint == null || constraint.version() == null || !constraint.hasText()) {
				continue;
			}

			if (constraint.literals().hasProperty() || !constraint.isRange()) {
				return new KtVersion(constraint.literals().toExpression(), constraint.version());
			}
		}

		return null;
	}

	private static Map<String, Constraint> constraints(KtCallElement versionCall) {

		Map<String, Constraint> constraints = new LinkedHashMap<>();
		SyntaxTraverser.psiTraverser(versionCall).filter(KtLambdaExpression.class)
				.flatMap(SyntaxTraverser::psiTraverser)
				.filter(KtCallExpression.class)
				.filter(it -> GradleVersionConstraint.isConstraint(KotlinDslUtils.getKotlinCallName(it)))
				.forEach(it -> {

					Constraint constraint = Constraint.of(it);
					constraints.put(constraint.name(), constraint);
				});
		return constraints;
	}

	/**
	 * @return the effective version as property reference or literal value.
	 */
	public Expression getExpression() {
		return expression;
	}

	/**
	 * @return the PSI element that contributes the effective version.
	 */
	public KtExpression getElement() {
		return element;
	}

	/**
	 * Named version constraint declared inside a Kotlin DSL {@code version { ... }}
	 * block, such as {@code strictly("1.2.3")}.
	 */
	record Constraint(String name, @Nullable KtExpression version, KtLiterals literals)
			implements GradleVersionConstraint {

		static Constraint of(KtCallElement call) {
			KtExpression version = KotlinDslUtils.getFirstValueArgument(call);
			return new Constraint(KotlinDslUtils.getKotlinCallName(call), version, KtLiterals.from(version));
		}

		@Override
		public String getVersion() {
			return literals.toString();
		}

	}

}
