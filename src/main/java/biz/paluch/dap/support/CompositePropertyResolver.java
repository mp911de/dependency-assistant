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

import org.jspecify.annotations.Nullable;

/**
 * {@link PropertyResolver} implementation that delegates to a primary resolver
 * and then to a fallback resolver.
 *
 * <p>The primary resolver wins for both value lookups and declaration metadata.
 * The fallback resolver is consulted only if the primary resolver does not
 * resolve a property value or {@link PropertyValue} element. This is typically
 * used to layer local script properties over broader project or global
 * properties without merging the underlying sources.
 *
 * @author Mark Paluch
 */
class CompositePropertyResolver implements PropertyResolver {

	private final PropertyResolver primary;

	private final PropertyResolver fallback;

	/**
	 * Create a new composite resolver.
	 * @param primary the resolver to consult first
	 * @param fallback the resolver to consult if the primary resolver has no match
	 */
	public CompositePropertyResolver(PropertyResolver primary, PropertyResolver fallback) {
		this.primary = primary;
		this.fallback = fallback;
	}

	@Override
	public boolean containsProperty(String propertyKey) {
		return primary.containsProperty(propertyKey) || fallback.containsProperty(propertyKey);
	}

	@Override
	public @Nullable String getProperty(String propertyKey) {
		String value = primary.getProperty(propertyKey);
		return value != null ? value : fallback.getProperty(propertyKey);
	}

	@Override
	public @Nullable PropertyValue getElement(String propertyKey) {
		PropertyValue element = primary.getElement(propertyKey);
		return element != null ? element : fallback.getElement(propertyKey);
	}


}
