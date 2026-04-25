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

package biz.paluch.dap.support;

import com.intellij.psi.PsiElement;

/**
 * Descriptor for a resolved build property declaration.
 *
 * <p>A {@code Property} represents a source-level declaration that contributes
 * a named value to property resolution. The {@linkplain #getKey() key} is the
 * lookup name used by {@link PropertyResolver}; the {@linkplain #getValue()
 * value} is the logical string value contributed by the declaration, without
 * source-language quoting. Placeholder expansion, type conversion, and fallback
 * lookup are outside this contract and are handled by resolver implementations.
 *
 * <p>The {@linkplain #getValueLiteral() value literal} identifies the PSI
 * element that owns the textual value. Callers use that element for
 * highlighting, navigation, and in-place updates, so implementations should
 * return the smallest PSI element whose text represents the resolved value.
 *
 * @author Mark Paluch
 * @see PropertyResolver
 */
public interface Property {

	/**
	 * Return the property lookup key.
	 * <p>The key must be the exact name under which the value is made available to
	 * {@link PropertyResolver}; it must not include source syntax such as
	 * {@code ${...}}, {@code extra[...]}, or surrounding quotes.
	 */
	String getKey();

	/**
	 * Return the resolved textual value declared for {@link #getKey()}.
	 * <p>The value is the logical property value as written by the declaration,
	 * stripped of source-language quoting, but otherwise unresolved.
	 * Implementations must not perform recursive property placeholder expansion
	 * here.
	 */
	String getValue();

	/**
	 * Return the PSI element that holds the declared value.
	 * <p>The returned element is the value-side anchor and can be used for
	 * inspections, gutter navigation, and updates. It should point to the concrete
	 * literal or literal-like PSI element whose replacement changes
	 * {@link #getValue()} while preserving the surrounding declaration.
	 */
	PsiElement getValueLiteral();

}
