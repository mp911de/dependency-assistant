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

package biz.paluch.dap.maven;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

/**
 * Internal utilities for Maven POM file detection and remote repository
 * assembly.
 *
 * @author Mark Paluch
 */
class MavenUtils {

	/**
	 * Return whether the given file is a Maven POM by filename alone.
	 * <p>This is a lightweight check suitable for action-visibility guards. It does
	 * not inspect file content or PSI structure.
	 *
	 * @param file the file to test; can be {@literal null}.
	 * @return {@literal true} if the filename is {@code pom.xml}; {@literal false}
	 * otherwise.
	 */
	public static boolean isMavenPomFile(@Nullable VirtualFile file) {
		return file != null && "pom.xml".equals(file.getName());
	}

	/**
	 * Return whether the given file is a Maven POM by filename and type.
	 * <p>This is a lightweight check suitable for action-visibility guards. It does
	 * not inspect file content or PSI structure.
	 *
	 * @param file the file to test; can be {@literal null}.
	 * @return {@literal true} if the file is an XML file named {@code pom.xml};
	 * {@literal false} otherwise.
	 */
	public static boolean isMavenPomFile(@Nullable PsiFile file) {
		return file instanceof XmlFile && "pom.xml".equals(file.getName());
	}

	/**
	 * Return whether the given file is a Maven extensions.xml by filename and type.
	 * <p>This is a lightweight check suitable for action-visibility guards. It does
	 * not inspect file content or PSI structure.
	 *
	 * @param file the file to test; can be {@literal null}.
	 * @return {@literal true} if the file is an XML file named
	 * {@code extensions.xml}; {@literal false} otherwise.
	 */
	public static boolean isMavenExtensionsFile(@Nullable VirtualFile file) {
		return file != null && "extensions.xml".equals(file.getName());
	}

	/**
	 * Return whether the given file is a Maven extensions.xml by filename and type.
	 * <p>This is a lightweight check suitable for action-visibility guards. It does
	 * not inspect file content or PSI structure.
	 *
	 * @param file the file to test; can be {@literal null}.
	 * @return {@literal true} if the file is an XML file named
	 * {@code extensions.xml}; {@literal false} otherwise.
	 */
	public static boolean isMavenExtensionsFile(@Nullable PsiFile file) {
		return file instanceof XmlFile && "extensions.xml".equals(file.getName());
	}

	/**
	 * Return whether the given XML file is a Maven POM by root element structure.
	 *
	 * @param xmlFile the XML file to inspect; must not be {@literal null}.
	 * @return {@literal true} if the root element identifies the file as a Maven
	 * POM; {@literal false} otherwise.
	 */
	public static boolean isMavenPomFile(XmlFile xmlFile) {

		XmlTag rootTag = xmlFile.getDocument() != null ? xmlFile.getDocument().getRootTag() : null;
		if (rootTag == null) {
			return false;
		}
		String localName = rootTag.getLocalName();
		if (!"project".equals(localName)) {
			return false;
		}
		String namespace = rootTag.getNamespace();
		return namespace.isEmpty() || "http://maven.apache.org/POM/4.0.0".equals(namespace);
	}

	@Contract("null -> false")
	public static boolean isVersionElement(@Nullable PsiElement element) {

		if (element == null) {
			return false;
		}

		XmlTag currentTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
		if (currentTag == null) {
			return false;
		}

		XmlTag parentTag = currentTag.getParentTag();
		if (parentTag == null) {
			return false;
		}

		String tagName = currentTag.getLocalName();
		String parentName = parentTag.getLocalName();

		if (tagName.equals("properties") || parentName.equals("properties")
				|| tagName.equals("extension") || parentName.equals("extension")
				|| parentName.equals("dependency") || parentName.equals("plugin")
				|| parentName.equals("parent")) {
			return true;
		}

		return "version".equals(tagName)
				&& ("dependency".equals(parentName) || "plugin".equals(parentName)
						|| "extension".equals(parentName) || "parent".equals(parentName));
	}

}
