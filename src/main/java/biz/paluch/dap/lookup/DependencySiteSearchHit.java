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

package biz.paluch.dap.lookup;

import com.intellij.psi.PsiElement;
import org.springframework.util.ObjectUtils;

/**
 * Search hit during a dependency site search: a navigable PSI element together
 * with the {@link SiteRole role} it plays for the dependency's version and a
 * concise {@code label} (the version or property expression) for display.
 *
 * <p>Instances are immutable and created through the role-named factories
 * {@link #declaration(PsiElement)} and {@link #usage(PsiElement)} (or their
 * label-carrying overloads).
 *
 * @author Mark Paluch
 * @see ArtifactReferenceResolver#search(DependencySiteQuery)
 * @see SiteRole
 */
public class DependencySiteSearchHit {

	private final PsiElement element;

	private final SiteRole role;

	private final String label;

	private DependencySiteSearchHit(PsiElement element, SiteRole role, String label) {
		this.element = element;
		this.role = role;
		this.label = label;
	}

	/**
	 * Create a {@link SiteRole#DECLARATION} search hit whose display label defaults
	 * to the element's own text.
	 *
	 * @param element the PSI element to navigate to and preview; must not be
	 * {@literal null}.
	 * @return the definition hit.
	 */
	public static DependencySiteSearchHit declaration(PsiElement element) {
		return declaration(element, element.getText());
	}

	/**
	 * Create a {@link SiteRole#DECLARATION} search hit with an explicit display
	 * label.
	 *
	 * @param element the PSI element to navigate to and preview; must not be
	 * {@literal null}.
	 * @param label the concise display text (the version); must not be
	 * {@literal null}.
	 * @return the definition hit.
	 */
	public static DependencySiteSearchHit declaration(PsiElement element, String label) {
		return new DependencySiteSearchHit(element, SiteRole.DECLARATION, label);
	}

	/**
	 * Create a {@link SiteRole#VERSION_USAGE} search hit whose display label
	 * defaults to the element's own text.
	 *
	 * @param element the PSI element to navigate to and preview; must not be
	 * {@literal null}.
	 * @return the version-usage hit.
	 */
	public static DependencySiteSearchHit usage(PsiElement element) {
		return usage(element, element.getText());
	}

	/**
	 * Create a {@link SiteRole#VERSION_USAGE} search hit with an explicit display
	 * label.
	 *
	 * @param element the PSI element to navigate to and preview; must not be
	 * {@literal null}.
	 * @param label the concise display text (the version-property expression); must
	 * not be {@literal null}.
	 * @return the version-usage hit.
	 */
	public static DependencySiteSearchHit usage(PsiElement element, String label) {
		return new DependencySiteSearchHit(element, SiteRole.VERSION_USAGE, label);
	}

	/**
	 * Return the PSI element to navigate to and preview.
	 *
	 * @return the located element.
	 */
	public PsiElement element() {
		return element;
	}

	/**
	 * Return the role the element plays for the dependency's version.
	 *
	 * @return the site role.
	 */
	public SiteRole role() {
		return role;
	}

	/**
	 * Return the concise display text (the version or property expression).
	 *
	 * @return the display label.
	 */
	public String label() {
		return label;
	}

	@Override
	public boolean equals(Object o) {

		if (!(o instanceof DependencySiteSearchHit that)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(element, that.element) && role == that.role
				&& ObjectUtils.nullSafeEquals(label, that.label);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(element, role, label);
	}

	@Override
	public String toString() {
		return "DependencySiteSearchHit{role=" + role + ", label=" + label + '}';
	}

}
