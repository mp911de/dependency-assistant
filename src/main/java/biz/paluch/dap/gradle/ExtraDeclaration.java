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

package biz.paluch.dap.gradle;

import biz.paluch.dap.support.Property;
import com.intellij.psi.PsiElement;

/**
 * Gradle script-local property declaration backed by {@code ext} or
 * {@code extra} syntax.
 *
 * <p>This contract models declarations that live in build scripts rather than
 * external property files. Implementations normalize the supported Groovy and
 * Kotlin DSL declaration forms into a {@link Property}: a non-empty key, the
 * logical string value, and the PSI element that owns the value text.
 *
 * <p>The declaration element and value literal serve different purposes. The
 * declaration element identifies the source construct that declares the extra
 * property; the value literal is the element to highlight or update when the
 * value changes. Callers must not assume that the value literal is a descendant
 * of the declaration element. For example, in Kotlin {@code "1.0".also {
 * extra["version"] = it }}, the value literal is the {@code also} receiver
 * while the declaration is the {@code extra["version"] = it} assignment.
 *
 * @author Mark Paluch
 * @see GroovyExtAssignment
 * @see KotlinExtraAssignment
 */
interface ExtraDeclaration extends Property {

	/**
	 * Return the PSI element representing the source-level declaration.
	 * <p>The concrete element depends on the DSL shape: it can be a Groovy
	 * {@code set(...)} call, a Groovy or Kotlin assignment, or a script variable
	 * declaration. The element should be suitable for recognizing the declaration
	 * as a whole, but it is not necessarily the element whose text should be
	 * replaced for value updates.
	 */
	PsiElement getDeclaration();

}
