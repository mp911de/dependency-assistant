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

package biz.paluch.dap.lookup;

import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.psi.PsiElement;
import org.springframework.util.ObjectUtils;

/**
 * Located Dependency Site combining a navigable PSI element, its
 * {@link SiteRole role}, and concise display text.
 *
 * <p>The supplied PSI element is retained directly and remains subject to
 * normal PSI validity rules. Equality includes the element, role, and label,
 * which is also the identity used when aggregating
 * {@link DependencySearchResults}.
 *
 * @author Mark Paluch
 * @see ArtifactReferenceResolver#search(DependencySiteQuery)
 * @see #declaration(PsiElement)
 * @see #declaration(PsiElement, String)
 * @see #usage(PsiElement)
 * @see #usage(PsiElement, String)
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
	 * @param element the PSI element to navigate to and preview.
	 * @return the declaration hit.
	 */
	public static DependencySiteSearchHit declaration(PsiElement element) {
		return declaration(element, element.getText());
	}

	/**
	 * Create a {@link SiteRole#DECLARATION} search hit with an explicit display
	 * label.
	 *
	 * @param element the PSI element to navigate to and preview.
	 * @param label the concise display text.
	 * @return the declaration hit.
	 */
	public static DependencySiteSearchHit declaration(PsiElement element, String label) {
		return new DependencySiteSearchHit(element, SiteRole.DECLARATION, label);
	}

	/**
	 * Create a {@link SiteRole#DECLARATION} search hit whose label is the
	 * declaration's version, or the element text for an unversioned declaration.
	 *
	 * @param element the PSI element to navigate to and preview.
	 * @param declaration the originating {@link ArtifactDeclaration}.
	 * @return the declaration hit.
	 */
	public static DependencySiteSearchHit declaration(PsiElement element, ArtifactDeclaration declaration) {

		String label = declaration.isVersioned() ? declaration.getVersion().toString() : element.getText();
		return new DependencySiteSearchHit(element, SiteRole.DECLARATION, label);
	}

	/**
	 * Create a {@link SiteRole#VERSION_USAGE} search hit whose display label
	 * defaults to the element's own text.
	 *
	 * @param element the PSI element to navigate to and preview.
	 * @return the version-usage hit.
	 */
	public static DependencySiteSearchHit usage(PsiElement element) {
		return usage(element, element.getText());
	}

	/**
	 * Create a {@link SiteRole#VERSION_USAGE} search hit with an explicit display
	 * label.
	 *
	 * @param element the PSI element to navigate to and preview.
	 * @param label the concise display text.
	 * @return the version-usage hit.
	 */
	public static DependencySiteSearchHit usage(PsiElement element, String label) {
		return new DependencySiteSearchHit(element, SiteRole.VERSION_USAGE, label);
	}

	/**
	 * Create a {@link SiteRole#VERSION_USAGE} search hit whose label is the bare
	 * version-property name, or the element text for another version source.
	 *
	 * @param element the PSI element to navigate to and preview.
	 * @param declaration the originating {@link ArtifactDeclaration}.
	 * @return the version-usage hit.
	 */
	public static DependencySiteSearchHit usage(PsiElement element, ArtifactDeclaration declaration) {

		String label = declaration.getVersionSource() instanceof VersionSource.VersionProperty property
				? property.getProperty()
				: element.getText();
		return usage(element, label);
	}

	public PsiElement element() {
		return element;
	}

	public SiteRole role() {
		return role;
	}

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
