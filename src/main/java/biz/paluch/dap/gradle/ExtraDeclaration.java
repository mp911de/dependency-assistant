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

package biz.paluch.dap.gradle;

import biz.paluch.dap.support.Property;
import com.intellij.psi.PsiElement;

/**
 * Gradle script-local property declaration backed by {@code ext} or
 * {@code extra} syntax.
 *
 * <p>The declaration element identifies the source construct that declares the
 * property. The value literal is the element to highlight or update when the
 * value changes, and is not necessarily a child of the declaration element.
 *
 * @author Mark Paluch
 * @see GroovyExtAssignment
 * @see KotlinExtraAssignment
 */
interface ExtraDeclaration extends Property {

	/**
	 * Return the PSI element representing the source-level declaration.
	 */
	PsiElement getDeclaration();

}
