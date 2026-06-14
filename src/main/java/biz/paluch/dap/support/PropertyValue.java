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

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jspecify.annotations.Nullable;

/**
 * Location of a property value inside {@code gradle.properties} or a
 * script-local declaration (e.g. {@code ext} / {@code extra}).
 *
 * @author Mark Paluch
 */
public class PropertyValue implements Property {

	private final String key;

	private final String value;

	private final SmartPsiElementPointer<PsiElement> pointer;

	public PropertyValue(String key, String value, PsiElement element) {
		this.key = key;
		this.value = value;
		this.pointer = SmartPointerManager.createPointer(element);
	}

	@Override
	public String getKey() {
		return this.key;
	}

	@Override
	public String getValue() {
		return this.value;
	}

	@Override
	public @Nullable PsiElement getValueLiteral() {
		return this.pointer.getElement();
	}

}
