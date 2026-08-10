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

/**
 * {@link PropertyResolver} backed by a {@link Map}.
 *
 * <p>Entries whose declaration PSI has been invalidated (for example after a
 * reparse that dropped the declaring element) are treated as absent rather than
 * handed out to callers.
 *
 * @author Mark Paluch
 */
record MapPropertyResolver(Map<String, ? extends Property> properties) implements PropertyResolver {

	@Override
	public @Nullable Property getPropertyValue(String key) {
		Property property = properties.get(key);
		return property != null && property.isValid() ? property : null;
	}

	@Override
	public boolean containsProperty(String key) {
		Property property = properties.get(key);
		return property != null && property.isValid();
	}

	@Override
	public @Nullable String getProperty(String key) {
		Property property = getPropertyValue(key);
		return property != null ? property.getValue() : null;
	}

}
