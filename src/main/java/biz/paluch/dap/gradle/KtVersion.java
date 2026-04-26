/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
 * Value object that exposes the version contract of a Kotlin DSL dependency
 * declaration.
 * <p>Instances are created from a dependency call such as
 * {@code implementation("g:a") { version { strictly("1.2.3") } }} or directly
 * from a {@code version(...)} call. The contract intentionally focuses on the
 * version information that callers need for navigation and upgrade logic:
 * <ul>
 * <li>a direct version literal or property reference declared on the dependency
 * itself,</li>
 * <li>version constraints declared within a nested {@code version { ... }}
 * block, and</li>
 * <li>a consistent precedence model where the direct version expression is
 * consulted before any constraint.</li>
 * </ul>
 * The type does not attempt to validate Gradle semantics beyond extracting
 * these supported structures.
 *
 * @author Mark Paluch
 */
class KtVersion {

	private final @Nullable KtCallElement version;

	private final @Nullable KtExpression versionLiteral;

	private final @Nullable KtLiterals versionLiterals;

	private final Map<String, Constraint> constraints;

	private KtVersion(@Nullable KtCallElement version, @Nullable KtExpression versionLiteral,
			Map<String, Constraint> constraints) {
		this.version = version;
		this.versionLiteral = versionLiteral;
		this.versionLiterals = versionLiteral != null ? KtLiterals.from(versionLiteral) : null;
		this.constraints = constraints;
	}

	/**
	 * Create a {@link KtVersion} from a dependency declaration.
	 * <p>The dependency is inspected for a nested {@code version(...)} call first.
	 * If no such call is present, the method falls back to a directly declared
	 * version/property expression in the dependency argument list.
	 *
	 * @param dependency the dependency declaration to inspect; never {@code null}.
	 * @return a {@link KtVersion} if the dependency exposes version information in
	 * one of the supported forms; {@code null} if the dependency does not declare a
	 * version.
	 */
	public static @Nullable KtVersion fromDependency(KtCallElement dependency) {

		KtCallElement version = SyntaxTraverser.psiTraverser(dependency)
				.filter(KtCallElement.class)
				.filter(it -> "version".equals(KotlinDslUtils.getKotlinCallName(it)))
				.first();

		if (version == null) {

			KtExpression property = getVersionLiteral(dependency.getValueArgumentList());
			if (property != null) {
				return new KtVersion(null, property, Map.of());
			}

			return null;
		}

		return fromVersion(version);
	}

	/**
	 * Create a {@link KtVersion} from a Kotlin DSL {@code version(...)} call.
	 * <p>Supported nested constraints are collected from {@code version {
	 * strictly(...) }} and {@code version { prefer(...) }} declarations. Calls with
	 * a different name are rejected.
	 *
	 * @param versionCall the call to inspect.
	 * @return a {@link KtVersion} describing the call, or {@code null} if the
	 * supplied element is not a {@code version} call.
	 */
	public static @Nullable KtVersion fromVersion(KtCallElement versionCall) {

		if (!"version".equals(KotlinDslUtils.getKotlinCallName(versionCall))) {
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
		return new KtVersion(versionCall, versionLiteral, constraints);
	}

	@Contract("null -> null")
	private static @Nullable KtExpression getVersionLiteral(@Nullable KtElement element) {

		if (element == null) {
			return null;
		}

		return SyntaxTraverser.psiTraverser(element)
				.filter(it -> !KtLambdaExpression.class.isInstance(it))
				.filter(it -> {
					return it instanceof KtReferenceExpression || it instanceof KtDotQualifiedExpression dotQualified
							&& dotQualified.getSelectorExpression() instanceof KtCallExpression selectorCall;
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
	 * Return the named version constraint that was declared within the
	 * {@code version { ... }} block.
	 *
	 * @param name the constraint name, for example {@code strictly} or
	 * {@code prefer}.
	 * @return the matching constraint or {@code null} if no such constraint is
	 * present.
	 */
	public @Nullable Constraint getConstraint(String name) {
		return constraints.get(name);
	}

	/**
	 * Return whether this declaration resolves to a property reference.
	 * <p>A direct version/property expression declared on the dependency takes
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
	 * <p>A direct version/property expression declared on the dependency takes
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
	 * <p>A direct version/property expression declared on the dependency takes
	 * precedence. If no direct version is present, constraints are inspected in
	 * declaration order. Range constraints are intentionally ignored because
	 * callers use this method for a single upgradeable version value.
	 *
	 * @return the version text, or {@code null} if no concrete non-range version
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
	 * <p>A directly declared version/property expression takes precedence.
	 * Otherwise, the first non-range constraint value is returned in declaration
	 * order.
	 *
	 * @return the contributing version element, or {@code null} if no effective
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
	 * <p>This includes a direct version/property expression or a constraint-backed
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
	 * <p>The contract is intentionally narrow: the record exposes the constraint
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
