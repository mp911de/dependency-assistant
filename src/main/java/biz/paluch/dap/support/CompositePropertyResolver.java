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

import org.jspecify.annotations.Nullable;

/**
 * Property resolution with a fallback. The primary resolver wins independently
 * for values and declaration metadata.
 *
 * @author Mark Paluch
 */
class CompositePropertyResolver implements PropertyResolver {

	private final PropertyResolver primary;

	private final PropertyResolver fallback;

	public CompositePropertyResolver(PropertyResolver primary, PropertyResolver fallback) {
		this.primary = primary;
		this.fallback = fallback;
	}

	@Override
	public boolean containsProperty(String key) {
		return primary.containsProperty(key) || fallback.containsProperty(key);
	}

	@Override
	public @Nullable String getProperty(String propertyKey) {
		String value = primary.getProperty(propertyKey);
		return value != null ? value : fallback.getProperty(propertyKey);
	}

	@Override
	public @Nullable Property getPropertyValue(String key) {
		Property element = primary.getPropertyValue(key);
		return element != null ? element : fallback.getPropertyValue(key);
	}


}
