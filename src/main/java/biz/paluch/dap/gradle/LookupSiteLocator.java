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
 * Strategy interface for locating semantic {@link LookupSite lookup sites} from
 * language-specific PSI.
 *
 * <p>{@code LookupSiteLocator} forms the bridge between syntax-specific PSI
 * traversal and semantic artifact resolution. Implementations inspect a PSI
 * element of a particular Gradle-related language and identify the most
 * specific declaration, property indirection, or version catalog reference that
 * should be exposed as a {@link LookupSite}.
 *
 * <p>Implementations are typically scoped to a concrete PSI model such as
 * Kotlin DSL, Groovy DSL, or TOML version catalogs. Callers are expected to
 * invoke the locator matching the current file type and pass the resulting
 * {@link LookupSite} directly to {@link GradleLookupSiteResolver}. This
 * contract uses {@link LookupSite#absent()} instead of {@code null} to signal
 * that no meaningful lookup site could be derived for the supplied element.
 *
 * @param <T> the supported PSI element type
 * @author Mark Paluch
 * @see LookupSite
 */
interface LookupSiteLocator<T extends PsiElement> {

	/**
	 * Locate the semantic {@link LookupSite} for the given PSI element.
	 * <p>The supplied element may be the declaration itself, a nested version
	 * literal, a property reference, or another PSI node contained within the
	 * owning declaration. Implementations may inspect surrounding PSI as needed to
	 * recover the effective lookup site.
	 * @param element the PSI element to inspect
	 * @return the resolved lookup site, or {@link LookupSite#absent()} if the
	 * element does not belong to a supported dependency or version declaration
	 */
	LookupSite locate(T element);

}
