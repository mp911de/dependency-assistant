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

import com.intellij.psi.PsiElement;

/**
 * Detects the role a PSI element plays in a Gradle dependency or plugin version
 * declaration.
 *
 * <p>It models the version-declaration grammar of a Gradle build file
 * regardless of the underlying script language: where an element sits within a
 * version declaration and, for call-inline styles, which call owns it.
 *
 * @author Mark Paluch
 * @see DeclarationStyle
 */
interface DeclarationStyleDetector {

	/**
	 * Detect the declaration style in which the given element carries a version.
	 * <p>Accepts either the version element itself (literal, template, or
	 * reference) or a caret leaf nested inside one; the implementation walks up to
	 * the enclosing candidate.
	 * @param element the element to introspect.
	 * @return the detected declaration style, or {@link DeclarationStyle#absent()}
	 * if the element is not part of a recognized version declaration.
	 */
	DeclarationStyle detect(PsiElement element);

}
