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

package biz.paluch.dap.maven;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

/**
 * Locates supported Maven property and version tags from a PSI context element.
 *
 * @author Mark Paluch
 */
public class XmlUtil {

	/**
	 * Return whether the element is the text of a Maven version or property value
	 * that the Maven reference resolvers accept.
	 *
	 * <p>Eligibility follows {@link #findVersionTag(PsiElement)} and
	 * {@link #findPropertyTag(PsiElement)}: dependency, plugin, build extension,
	 * and external parent versions in a POM, extension versions in
	 * {@code extensions.xml}, and {@code properties} children in a POM. Whether a
	 * property is a tracked version property is decided by the resolver.
	 *
	 * @param element the PSI element to classify, or {@literal null}.
	 * @return {@code true} if the element is recognized as a Maven version
	 * candidate.
	 */
	@Contract("null -> false")
	public static boolean isVersionElement(@Nullable PsiElement element) {
		return element instanceof XmlText
				&& (findVersionTag(element) != null || findPropertyTag(element) != null);
	}

	/**
	 * Return the property tag for the given context element if the element is a
	 * property within the {@code properties} tag.
	 *
	 * @param contextElement the element at or inside the candidate property tag.
	 * @return the property tag, or {@literal null} when the context is outside a
	 * Maven POM property.
	 */
	public static @Nullable XmlTag findPropertyTag(PsiElement contextElement) {

		PsiFile file = contextElement.getContainingFile();
		if (!(file instanceof XmlFile xmlFile) || !MavenUtils.isMavenPomFile(xmlFile)) {
			return null;
		}

		XmlTag propertyTag = PsiTreeUtil.getParentOfType(contextElement, XmlTag.class, false);
		if (propertyTag == null) {
			return null;
		}

		XmlTag propertiesTag = propertyTag.getParentTag();
		if (propertiesTag == null || !"properties".equals(propertiesTag.getLocalName())) {
			return null;
		}

		return propertyTag;
	}

	/**
	 * Return the supported Maven version tag containing the given context element.
	 *
	 * <p>POM versions are resolved through {@link #findPomVersionTag(PsiElement)}.
	 * Maven extension versions are resolved through
	 * {@link #findExtensionVersionTag(PsiElement)}.
	 *
	 * @param contextElement the element at or inside the candidate version tag.
	 * @return the version tag, or {@literal null} when the context is not a
	 * supported Maven version site.
	 */
	public static @Nullable XmlTag findVersionTag(PsiElement contextElement) {

		PsiFile file = contextElement.getContainingFile();
		if (MavenUtils.isMavenPomFile(file)) {
			return findPomVersionTag(contextElement);
		}

		if (MavenUtils.isMavenExtensionsFile(file)) {
			return findExtensionVersionTag(contextElement);
		}

		return null;
	}

	/**
	 * Return the version tag for the given context element if the element is a
	 * version tag within a dependency, plugin, build extension, or supported
	 * external parent declaration.
	 *
	 * @param contextElement the element at or inside the candidate version tag.
	 * @return the version tag, or {@literal null} when the context is not a
	 * supported POM version site.
	 */
	public static @Nullable XmlTag findPomVersionTag(PsiElement contextElement) {

		PsiFile file = contextElement.getContainingFile();
		if (!MavenUtils.isMavenPomFile(file)) {
			return null;
		}

		XmlTag versionTag = PsiTreeUtil.getParentOfType(contextElement, XmlTag.class, false);
		if (versionTag == null || !"version".equals(versionTag.getLocalName())) {
			return null;
		}

		XmlTag owner = versionTag.getParentTag();
		if (owner == null) {
			return null;
		}

		String tagName = owner.getLocalName();
		if ("dependency".equals(tagName) || "plugin".equals(tagName)
				|| "extension".equals(tagName)) {
			return versionTag;
		}

		if ("parent".equals(tagName) && MavenParser.isParentDependencyCandidate(owner.getParentTag(), owner)) {
			return versionTag;
		}

		return null;
	}

	/**
	 * Return the version tag for the given context element if the element is a
	 * version tag within an {@code extensions.xml} extension declaration.
	 *
	 * @param contextElement the element at or inside the candidate version tag.
	 * @return the version tag, or {@literal null} when the context is not an
	 * extension version site.
	 */
	public static @Nullable XmlTag findExtensionVersionTag(PsiElement contextElement) {

		PsiFile file = contextElement.getContainingFile();
		if (!MavenUtils.isMavenExtensionsFile(file)) {
			return null;
		}

		XmlTag versionTag = PsiTreeUtil.getParentOfType(contextElement, XmlTag.class, false);
		if (versionTag == null || !"version".equals(versionTag.getLocalName())) {
			return null;
		}

		XmlTag owner = versionTag.getParentTag();
		if (owner == null) {
			return null;
		}

		if ("extension".equals(owner.getLocalName())) {
			return versionTag;
		}

		return null;
	}

}
