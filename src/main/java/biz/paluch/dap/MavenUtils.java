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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

/**
 * @author Mark Paluch
 */
class MavenUtils {

	/**
	 * Uses the IDE's PSI to detect if the document is a Maven POM: root element must be "project" with Maven POM
	 * namespace (or no namespace).
	 */
	public static boolean isMavenPomFile(Project project, com.intellij.openapi.editor.Document document) {
		PsiElement psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);

		if (!(psiFile instanceof XmlFile xmlFile)) {
			return false;
		}

		return isMavenPomFile(xmlFile);
	}

	public static boolean isMavenPomFile(PsiFile file) {
		return file instanceof XmlFile xmlFile && isMavenPomFile(xmlFile);
	}

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
}
