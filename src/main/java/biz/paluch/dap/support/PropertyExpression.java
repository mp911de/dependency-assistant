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
package biz.paluch.dap.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Value object representing either a property expression such as {@code ${name}} or a literal value.
 * <p>
 * Use {@link #from(String)} to create an instance and inspect its actual type through {@link #isProperty()} or subtype
 * checks.
 *
 * @author Mark Paluch
 */
public abstract class PropertyExpression {

	private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]*)\\}");

	private final String value;

	private PropertyExpression(String value) {
		this.value = value;
	}

	/**
	 * Create a {@link PropertyExpression} from the given value.
	 *
	 * @param value the source value
	 * @return a {@link PropertyReference} if the value is a property expression; otherwise a {@link LiteralValue}
	 */
	public static PropertyExpression from(@Nullable String value) {

		Assert.notNull(value, "Value must not be null");

		if (StringUtils.hasText(value)) {

			Matcher matcher = PROPERTY_PATTERN.matcher(value);
			if (matcher.matches()) {
				return new PropertyReference(matcher.group(1));
			}

			return new LiteralValue(value);
		}

		return new LiteralValue("");
	}

	/**
	 * Create a {@link PropertyExpression} from the given value.
	 *
	 * @param value the source value.
	 */
	public static PropertyExpression property(@Nullable String value) {

		Assert.notNull(value, "Value must not be null");

		return new PropertyReference(value);
	}

	/**
	 * Return whether this value is a property expression.
	 *
	 * @return {@code true} if this value is a property expression; {@code false} otherwise
	 */
	public abstract boolean isProperty();

	/**
	 * Return the property name.
	 *
	 * @return the property name.
	 */
	public abstract String getPropertyName();

	@Override
	public String toString() {
		return value;
	}

	/**
	 * {@link PropertyExpression} representing a property reference such as {@code ${spring.version}}.
	 */
	private static class PropertyReference extends PropertyExpression {

		private PropertyReference(String propertyName) {
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

	}

	/**
	 * {@link PropertyExpression} representing a literal, non-expression value.
	 */
	private static class LiteralValue extends PropertyExpression {

		private LiteralValue(String value) {
			super(value);
		}

		@Override
		public boolean isProperty() {
			return false;
		}

		@Override
		public @Nullable String getPropertyName() {
			throw new IllegalStateException("Not a property expression");
		}

	}

}
