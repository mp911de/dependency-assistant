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

import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLambdaExpression;
import org.jetbrains.kotlin.psi.KtReferenceExpression;
import org.jspecify.annotations.Nullable;

/**
 * Value object for version information in a Kotlin DSL dependency declaration.
 *
 * <p>
 * Supports direct version expressions and constraints declared in a nested
 * {@code version { ... }} block. It extracts only the PSI needed for navigation
 * and upgrade logic.
 *
 * @author Mark Paluch
 */
class KtVersion {

	private final @Nullable KtExpression versionLiteral;

	private final @Nullable KtLiterals versionLiterals;

	private final Map<String, Constraint> constraints;

	private KtVersion(@Nullable KtExpression versionLiteral,
			Map<String, Constraint> constraints) {
		this.versionLiteral = versionLiteral;
		this.versionLiterals = versionLiteral != null ? KtLiterals.from(versionLiteral) : null;
		this.constraints = constraints;
	}

	/**
	 * Create a {@link KtVersion} from a dependency declaration.
	 * @param dependency the dependency declaration to inspect.
	 * @return a {@link KtVersion} if the dependency exposes version information in
	 * one of the supported forms.
	 */
	public static @Nullable KtVersion fromDependency(KtCallElement dependency) {

		KtCallElement version = SyntaxTraverser.psiTraverser(dependency)
				.filter(KtCallElement.class)
				.filter(it -> GradleUtils.VERSION.equals(KotlinDslUtils.getKotlinCallName(it)))
				.first();

		if (version == null) {

			KtExpression property = getVersionLiteral(dependency.getValueArgumentList());
			if (property != null) {
				return new KtVersion(property, Map.of());
			}

			return null;
		}

		return fromVersion(version);
	}

	/**
	 * Create a {@link KtVersion} from a Kotlin DSL {@code version(...)} call.
	 * @param versionCall the call to inspect.
	 * @return a {@link KtVersion} describing the call, or {@literal null}.
	 */
	public static @Nullable KtVersion fromVersion(KtCallElement versionCall) {

		if (!GradleUtils.VERSION.equals(KotlinDslUtils.getKotlinCallName(versionCall))) {
			return null;
		}

		Map<String, Constraint> constraints = new LinkedHashMap<>();
		SyntaxTraverser.psiTraverser(versionCall).filter(KtLambdaExpression.class)
				.flatMap(SyntaxTraverser::psiTraverser)
				.filter(KtCallExpression.class)
				.filter(it -> {
					return GradleVersionConstraint.isConstraint(KotlinDslUtils.getKotlinCallName(it));
				}).forEach(it -> {

					Constraint constraint = Constraint.of(it);
					constraints.put(constraint.name, constraint);
				});

		KtExpression versionLiteral = getVersionLiteral(versionCall.getValueArgumentList());
		return new KtVersion(versionLiteral, constraints);
	}

	@Contract("null -> null")
	private static @Nullable KtExpression getVersionLiteral(@Nullable KtElement element) {

		if (element == null) {
			return null;
		}

		return SyntaxTraverser.psiTraverser(element)
				.filter(it -> !(it instanceof KtLambdaExpression))
				.filter(it -> {
					return it instanceof KtReferenceExpression || it instanceof KtDotQualifiedExpression dotQualified
							&& dotQualified.getSelectorExpression() instanceof KtCallExpression;
				}).filter(KtExpression.class)
				.flatMap(it -> {

					String propertyName = KtLiterals.getText(it);
					if (StringUtils.hasText(propertyName)) {
						return JBIterable.of(it);
					}

					return JBIterable.empty();
				}).first();
	}

	/**
	 * Return whether this declaration resolves to a property reference.
	 * <p>
	 * A direct version/property expression declared on the dependency takes
	 * precedence. Otherwise, constraints are consulted in declaration order.
	 *
	 * @return {@literal true} if a property-backed version is available.
	 */
	public boolean hasProperty() {

		if (versionLiteral instanceof KtReferenceExpression ref) {
			return StringUtils.hasText(ref.getText());
		}

		if (versionLiterals != null) {
			return versionLiterals.hasProperty();
		}

		for (Constraint value : constraints.values()) {
			if (value.hasProperty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return the referenced property name.
	 * <p>
	 * A direct version/property expression declared on the dependency takes
	 * precedence. Otherwise, constraints are consulted in declaration order.
	 *
	 * @return the property name without decoration.
	 * @throws IllegalStateException if {@link #hasProperty()} is {@literal false}.
	 */
	public String getProperty() {

		if (versionLiteral instanceof KtReferenceExpression ref) {
			return ref.getText();
		}

		if (versionLiterals != null && versionLiterals.hasProperty()) {
			return versionLiterals.getProperty();
		}

		for (Constraint value : constraints.values()) {
			if (value.hasProperty()) {
				return value.literals.getProperty();
			}
		}

		throw new IllegalStateException("No property found");
	}

	/**
	 * Return the concrete version text, if available.
	 * <p>
	 * A direct version/property expression declared on the dependency takes
	 * precedence. If no direct version is present, constraints are inspected in
	 * declaration order. Range constraints are intentionally ignored because
	 * callers need a single upgradeable version value.
	 *
	 * @return the version text, or {@literal null} if no concrete non-range version
	 * can be obtained.
	 */
	public @Nullable String getVersion() {

		if (versionLiteral != null) {
			String version = KtLiterals.getText(versionLiteral);
			if (StringUtils.hasText(version)) {
				return version;
			}
		}

		for (Constraint value : constraints.values()) {
			if (value.hasText() && !value.isRange()) {
				String version = KtLiterals.getText(value.version());
				if (StringUtils.hasText(version)) {
					return version;
				}
			}
		}

		return null;
	}

	/**
	 * Return the PSI element that contributes the effective version value.
	 * <p>
	 * A directly declared version/property expression takes precedence.
	 * Otherwise, the first non-range constraint value is returned in declaration
	 * order.
	 *
	 * @return the contributing version element, or {@literal null} if no effective
	 * version is available.
	 */
	public @Nullable KtExpression getVersionElement() {

		if (versionLiteral != null) {
			return versionLiteral;
		}

		for (Constraint value : constraints.values()) {
			if (value.literals.hasProperty()) {
				return value.version();
			}
			if (value.hasText() && !value.isRange()) {
				return value.version();
			}
		}

		return null;
	}

	/**
	 * Return whether the declaration contains any usable version information.
	 * <p>
	 * This includes a direct version/property expression or a constraint-backed
	 * version/property.
	 *
	 * @return {@literal true} if callers can resolve either a property or a
	 * concrete version value.
	 */
	public boolean containsVersion() {

		if (versionLiterals != null) {
			return versionLiterals.hasText();
		}

		for (Constraint value : constraints.values()) {
			if (value.literals.hasProperty()) {
				return true;
			}
			if (value.hasText() && !value.isRange()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Representation of a named version constraint declared inside a Kotlin DSL
	 * {@code version { ... }} block.
	 * <p>
	 * The contract is intentionally narrow: the record exposes the constraint
	 * name, the underlying PSI elements, and the literal rendering used by
	 * {@link GradleVersionConstraint}.
	 */
	record Constraint(String name, KtCallElement call, KtExpression version, KtLiterals literals)
			implements GradleVersionConstraint {

		/**
		 * Create a {@link Constraint} from a single constraint call such as
		 * {@code strictly("1.2.3")}.
		 *
		 * @param call the constraint call.
		 * @return the corresponding {@link Constraint}.
		 */
		public static Constraint of(KtCallElement call) {
			KtExpression version = KotlinDslUtils.getFirstValueArgument(call);
			KtLiterals from = KtLiterals.from(version);
			return new Constraint(KotlinDslUtils.getKotlinCallName(call), call, version,
					version instanceof KtReferenceExpression ? from.asProperty() : from);
		}

		/**
		 * Return whether the constraint renders textual content.
		 *
		 * @return {@literal true} if the constraint contributes text.
		 */
		@Override
		public boolean hasText() {
			return literals.hasText();
		}

		/**
		 * Return whether the constraint resolves to a property reference.
		 *
		 * @return {@literal true} if the constraint value is property-backed.
		 */
		public boolean hasProperty() {
			return literals.hasProperty();
		}

		/**
		 * Return the rendered version text for this constraint.
		 *
		 * @return the literal representation, potentially including property
		 * placeholders.
		 */
		@Override
		public String getVersion() {
			return literals().toString();
		}

	}

}
