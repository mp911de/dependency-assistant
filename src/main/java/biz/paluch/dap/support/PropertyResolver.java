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

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Strategy interface for resolving build-time property values.
 *
 * <p>
 * A resolver may expose both the resolved string value and the
 * {@link PropertyValue} metadata that identifies its PSI declaration. Metadata
 * support is optional.
 *
 * @author Mark Paluch
 * @see PropertyValue
 */
public interface PropertyResolver {

	/**
	 * Determine whether the given property key is available for resolution.
	 * @param key the property name to resolve.
	 */
	default boolean containsProperty(String key) {
		return getProperty(key) != null;
	}

	/**
	 * Resolve the property value associated with the given key, or {@literal null} if
	 * the key cannot be resolved.
	 * @param key the property name to resolve.
	 * @see #containsProperty(String)
	 */
	@Nullable
	String getProperty(String key);

	/**
	 * Return declaration metadata for the given property key, if available.
	 * @param key the property name.
	 * @return the declaration metadata, or {@literal null}.
	 */
	default @Nullable Property getPropertyValue(String key) {
		return null;
	}

	/**
	 * Resolve {@code ${...}} placeholders in the given text, replacing them with
	 * corresponding property values.
	 * @param text the String to resolve.
	 * @return the resolved String.
	 * @throws IllegalArgumentException if given text is {@literal null}.
	 */
	default String resolvePlaceholders(String text) {
		Assert.notNull(text, "Text must not be null");
		return PropertyResolverUtil.resolvePlaceholders(text, this);
	}

	/**
	 * Compose this resolver with the given fallback resolver.
	 * <p>
	 * This resolver is consulted first for both {@link #getProperty(String)} and
	 * {@link #getPropertyValue(String)} lookups.
	 * @param fallback the resolver to consult if this resolver has no match.
	 * @return a composite resolver with this resolver as primary and
	 * {@code fallback} as secondary.
	 */
	default PropertyResolver withFallback(PropertyResolver fallback) {
		return new CompositePropertyResolver(this, fallback);
	}

	/**
	 * Return an empty {@link PropertyResolver}.
	 */
	static PropertyResolver empty() {
		return key -> null;
	}

	/**
	 * Create a {@link PropertyResolver} backed by the given property map.
	 * @param properties the property entries keyed by property name.
	 * @return a map-backed property resolver.
	 */
	static PropertyResolver fromMap(Map<String, ? extends Property> properties) {
		return new MapPropertyResolver(properties);
	}

}
