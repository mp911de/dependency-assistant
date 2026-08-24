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

import com.intellij.psi.PsiElement;

/**
 * Descriptor for a resolved build property declaration.
 *
 * <p>A property exposes the lookup key, the logical string value, and the PSI
 * element that owns the value text. Placeholder expansion and fallback lookup
 * are handled by {@link PropertyResolver}.
 *
 * @author Mark Paluch
 * @see PropertyResolver
 */
public interface Property {

	/**
	 * Return the property lookup key.
	 *
	 * @return the key used by a {@link PropertyResolver}.
	 */
	String getKey();

	/**
	 * Return the logical textual value declared for {@link #getKey()}.
	 *
	 * @return the declared property value.
	 */
	String getValue();

	/**
	 * Return whether the declaration this property points to is still valid.
	 * <p>Cached properties can outlive their PSI. Callers that intend to use
	 * {@link #getValueLiteral()} must skip invalid properties.
	 * @return {@literal true} if the declaring PSI can still be used;
	 * {@literal false} otherwise.
	 */
	default boolean isValid() {
		return getValueLiteral().isValid();
	}

	/**
	 * Return the PSI element that holds the declared value.
	 *
	 * @return the value's PSI anchor, which may be invalid when {@link #isValid()}
	 * is {@literal false}.
	 */
	PsiElement getValueLiteral();

}
