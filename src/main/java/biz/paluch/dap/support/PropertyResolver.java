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

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Strategy interface for resolving build-time property values.
 *
 * <p>A resolver may expose both the logical string value and the
 * {@link Property} binding that identifies its PSI declaration. Binding support
 * is optional.
 *
 * @author Mark Paluch
 * @see PropertyValue
 */
public interface PropertyResolver {

	default boolean containsProperty(String key) {
		return getProperty(key) != null;
	}

	/**
	 * Resolve the value, or return {@code null} if the key is absent.
	 */
	@Nullable
	String getProperty(String key);

	/**
	 * Return declaration metadata, or {@code null} if absent or unsupported.
	 */
	default @Nullable Property getPropertyValue(String key) {
		return null;
	}

	/**
	 * Expand {@code ${name}} and {@code $name} placeholders, including property
	 * chains. Unknown placeholders, cycles, and chains beyond the resolution limit
	 * remain unresolved.
	 *
	 * @throws IllegalArgumentException if the text is {@code null}.
	 */
	default String resolvePlaceholders(String text) {
		Assert.notNull(text, "Text must not be null");
		return PropertyResolverUtil.resolvePlaceholders(text, this);
	}

	/**
	 * Use the fallback when this resolver has no value or declaration metadata. The
	 * two lookups fall back independently.
	 */
	default PropertyResolver withFallback(PropertyResolver fallback) {
		return new CompositePropertyResolver(this, fallback);
	}

	static PropertyResolver empty() {
		return key -> null;
	}

	/**
	 * Retain the property map as a live source. Invalid PSI declarations are
	 * treated as absent.
	 */
	static PropertyResolver fromMap(Map<String, ? extends Property> properties) {
		return new MapPropertyResolver(properties);
	}

}
