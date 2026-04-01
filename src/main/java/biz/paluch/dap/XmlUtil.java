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
package biz.paluch.dap;

import biz.paluch.dap.artifact.ArtifactId;

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

/**
 * XML handling utility methods.
 *
 * @author Mark Paluch
 */
class XmlUtil {

	/**
	 * Return the property tag for the given context element if the element is a property within the {@code properties}
	 * tag.
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
	 * Return the version tag for the given context element if the element is a version tag within a dependency or plugin.
	 */
	public static @Nullable XmlTag findVersionTag(PsiElement contextElement) {

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

		if ("dependency".equals(owner.getLocalName()) || "plugin".equals(owner.getLocalName())) {
			return versionTag;
		}

		return null;
	}

	/**
	 * Return the artifact id of the given plugin or dependency.
	 */
	static @Nullable ArtifactId getArtifactId(@Nullable XmlTag pluginOrDependency) {

		if (pluginOrDependency == null) {
			return null;
		}

		String groupId = pluginOrDependency.getSubTagText("groupId");
		String artifactId = pluginOrDependency.getSubTagText("artifactId");
		if (!StringUtils.hasText(groupId) || !StringUtils.hasText(artifactId)) {
			return null;
		}

		return ArtifactId.of(groupId, artifactId);
	}

}
