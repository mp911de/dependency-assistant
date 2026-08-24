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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

/**
 * Classifies Maven POM and extension files and their recognized version PSI
 * elements.
 *
 * @author Mark Paluch
 */
class MavenUtils {

	/**
	 * Return whether the given file is a Maven POM by filename and type.
	 * <p>This is a lightweight check suitable for action-visibility guards. It does
	 * not inspect file content or PSI structure.
	 *
	 * @param file the file to test, or {@literal null}.
	 * @return {@code true} if the file is an XML file named {@code pom.xml}.
	 */
	public static boolean isMavenPomFile(@Nullable PsiFile file) {
		return file instanceof XmlFile && "pom.xml".equals(file.getName());
	}

	/**
	 * Return whether the given file is a Maven extensions.xml by filename.
	 * <p>This is a lightweight check suitable for action-visibility guards. It only
	 * inspects the filename and does not check the file type, content, or PSI
	 * structure.
	 *
	 * @param file the file to test, or {@literal null}.
	 * @return {@code true} if the file is named {@code extensions.xml}.
	 */
	public static boolean isMavenExtensionsFile(@Nullable VirtualFile file) {
		return file != null && "extensions.xml".equals(file.getName());
	}

	/**
	 * Return whether the given file is a Maven extensions.xml by filename and type.
	 * <p>This is a lightweight check suitable for action-visibility guards. It does
	 * not inspect file content or PSI structure.
	 *
	 * @param file the file to test, or {@literal null}.
	 * @return {@code true} if the file is an XML file named {@code extensions.xml}.
	 */
	public static boolean isMavenExtensionsFile(@Nullable PsiFile file) {
		return file instanceof XmlFile && "extensions.xml".equals(file.getName());
	}

	/**
	 * Return whether the given XML file is a Maven POM by root element structure.
	 *
	 * @param xmlFile the XML file to inspect.
	 * @return {@code true} if the root element is {@code project} with no namespace
	 * or the Maven POM 4.0.0 namespace.
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

	/**
	 * Return whether the element is the text of a recognized Maven version or
	 * property value.
	 *
	 * <p>Recognized version owners are dependencies, plugins, build extensions, and
	 * parents. Any direct child of a {@code properties} tag is a supported property
	 * value.
	 *
	 * @param element the PSI element to classify, or {@literal null}.
	 * @return {@code true} if the element is recognized as a Maven version
	 * candidate.
	 */
	@Contract("null -> false")
	public static boolean isVersionElement(@Nullable PsiElement element) {

		if (element == null || !(element instanceof XmlText)) {
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

		return "properties".equals(parentName) || "version".equals(tagName)
				&& ("dependency".equals(parentName) || "plugin".equals(parentName)
						|| "extension".equals(parentName) || "parent".equals(parentName));
	}

}
