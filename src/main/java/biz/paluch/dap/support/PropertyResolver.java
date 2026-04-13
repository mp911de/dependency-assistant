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
 * Resolves build-time property keys to string values and, when supported, to
 * the declaring PSI for documentation and navigation.
 *
 * @author Mark Paluch
 */
public interface PropertyResolver {

	/**
	 * Whether a non-{@code null} value exists for {@code propertyKey}.
	 */
	default boolean containsProperty(String propertyKey) {
		return getProperty(propertyKey) != null;
	}

	/**
	 * Resolves the property value for {@code propertyKey}, or {@code null} if the
	 * key is not defined in this resolver.
	 *
	 * @param propertyKey the property name
	 */
	@Nullable
	String getProperty(String propertyKey);

	/**
	 * PSI element that carries the resolved value for {@code propertyKey}, when
	 * this resolver tracks declaration sites (e.g. merged Gradle script
	 * properties).
	 *
	 * @param propertyKey the property name
	 * @return the value literal or entry PSI, or {@code null} when not tracked here
	 */
	default @Nullable PsiPropertyValueElement getElement(String propertyKey) {
		return null;
	}

}
