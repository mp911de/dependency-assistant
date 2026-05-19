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
 * Strategy interface for locating {@link GradleVersionSite version sites} from
 * language-specific Gradle PSI.
 *
 * @param <T> the supported PSI element type.
 * @author Mark Paluch
 * @see GradleVersionSite
 */
interface VersionSiteLocator<T extends PsiElement> {

	/**
	 * Locate the semantic {@link GradleVersionSite} for the given PSI element.
	 * @param element the PSI element to inspect.
	 * @return the resolved version site, or {@link GradleVersionSite#absent()} if
	 * the element does not belong to a supported declaration.
	 */
	GradleVersionSite locate(T element);

}
