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

package biz.paluch.dap.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Parsed build-file value that is either a whole-value property reference or a
 * literal.
 *
 * <p>{@link #from(String)} recognizes only values consisting entirely of
 * {@code ${name}} or {@code $name}. It does not interpolate placeholders within
 * a larger literal. Callers distinguish the two forms through
 * {@link #isProperty()}.
 *
 * @author Mark Paluch
 */
public abstract class Expression {

	private static final Pattern PROPERTY_PATTERN = Pattern
			.compile("\\$\\{([^}]*)\\}|\\$([a-zA-Z_][a-zA-Z0-9_]*)");

	private final String value;

	private Expression(String value) {
		this.value = value;
	}

	/**
	 * Create a {@link Expression} from the given value.
	 * <p>A value is a property expression when it consists entirely of a braced
	 * ({@code ${name}}) or unbraced ({@code $name}) placeholder. Any other
	 * non-blank value is preserved as a literal, while a blank value becomes an
	 * empty literal.
	 *
	 * @param value the source value.
	 * @return a property-reference expression when the whole value is a
	 * placeholder, or a literal expression otherwise.
	 * @throws IllegalArgumentException if {@code value} is {@literal null}.
	 */
	@Contract("null -> fail; _ -> new")
	public static Expression from(@Nullable String value) {

		Assert.notNull(value, "Value must not be null");

		if (StringUtils.hasText(value)) {

			Matcher matcher = PROPERTY_PATTERN.matcher(value);
			if (matcher.matches()) {
				return new Reference(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
			}

			return new LiteralValue(value);
		}

		return new LiteralValue("");
	}

	/**
	 * Create a property {@link Expression} from the given value.
	 * <p>The value is always treated as a property reference, regardless of whether
	 * it contains placeholder syntax.
	 *
	 * @param value the property name.
	 * @return a property-reference expression for the given property name.
	 * @throws IllegalArgumentException if {@code value} is {@literal null}.
	 */
	@Contract("null -> fail; _ -> new")
	public static Expression property(@Nullable String value) {

		Assert.notNull(value, "Value must not be null");
		return new Reference(value);
	}

	/**
	 * Return whether this value is a property expression.
	 *
	 * @return {@literal true} if this value is a property expression;
	 * {@literal false} otherwise.
	 */
	public abstract boolean isProperty();

	/**
	 * Return the property name represented by this expression.
	 *
	 * @return the property name.
	 * @throws IllegalStateException if this expression is a literal.
	 */
	public abstract String getPropertyName();

	/**
	 * Return the version source represented by this expression.
	 *
	 * @return a property source for a reference or a declared source for a literal.
	 */
	public abstract VersionSource asVersionSource();

	/**
	 * Resolve the value of this expression using the given
	 * {@link PropertyResolver}. If this expression is a property reference, the
	 * resolver will be used to resolve the property value; otherwise, the literal
	 * value will be returned as-is.
	 * @param propertyResolver the property resolver to use for resolving property
	 * references.
	 * @return the resolved property value, the literal value, or {@literal null}
	 * when a referenced property is absent.
	 */
	public @Nullable String resolve(PropertyResolver propertyResolver) {
		return isProperty() ? propertyResolver.getProperty(getPropertyName()) : toString();
	}

	/**
	 * Resolve the value of this expression using the given
	 * {@link PropertyResolver}. If this expression is a property reference, the
	 * resolver will be used to resolve the property value; otherwise, the literal
	 * value will be returned as-is.
	 * @param propertyResolver the property resolver to use for resolving property
	 * references.
	 * @return the resolved value.
	 * @throws IllegalStateException if the expression is a property reference but
	 * the resolver cannot resolve it.
	 */
	public String resolveRequired(PropertyResolver propertyResolver) {

		if (isProperty()) {
			String value = propertyResolver.getProperty(getPropertyName());
			if (value == null) {
				throw new IllegalStateException("Unable to resolve property '%s'".formatted(getPropertyName()));
			}
			return value;
		}

		return toString();
	}

	@Override
	public String toString() {
		return value;
	}

	/**
	 * {@link Expression} representing a property reference such as
	 * {@code ${spring.version}}.
	 */
	private static class Reference extends Expression {

		private Reference(String propertyName) {
			super(propertyName);
		}

		@Override
		public boolean isProperty() {
			return true;
		}

		@Override
		public String getPropertyName() {
			return toString();
		}

		@Override
		public VersionSource asVersionSource() {
			return VersionSource.property(getPropertyName());
		}

	}

	/**
	 * {@link Expression} representing a literal, non-expression value.
	 */
	private static class LiteralValue extends Expression {

		private LiteralValue(String value) {
			super(value);
		}

		@Override
		public boolean isProperty() {
			return false;
		}

		@Override
		public String getPropertyName() {
			throw new IllegalStateException("Not a property expression");
		}

		@Override
		public VersionSource asVersionSource() {
			return VersionSource.declared(toString());
		}

	}

}
