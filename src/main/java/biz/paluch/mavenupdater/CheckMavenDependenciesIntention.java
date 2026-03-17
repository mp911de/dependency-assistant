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
package biz.paluch.mavenupdater;

import org.jspecify.annotations.Nullable;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

/**
 * Quick fix / intention in the POM XML editor when the caret is inside the &lt;version&gt; element of a
 * &lt;dependency&gt;. Runs the same "Check Maven Dependency Versions" flow.
 */
public class CheckMavenDependenciesIntention extends BaseElementAtCaretIntentionAction {

	@Override
	public String getFamilyName() {
		return "Maven dependency versions";
	}

	@Override
	public String getText() {
		return "Check Maven dependency versions";
	}

	@Override
	public boolean isAvailable(Project project, Editor editor, @Nullable PsiElement element) {

		if (element == null || !(element.getContainingFile() instanceof XmlFile xmlFile)) {
			return false;
		}

		if (!MavenUtils.isMavenPomFile(project, xmlFile)) {
			return false;
		}

		XmlTag currentTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
		if (currentTag == null) {
			return false;
		}

		XmlTag parentTag = currentTag.getParentTag();

		if (parentTag != null && parentTag.getLocalName().equals("properties")) {
			return true;
		}

		return parentTag != null && "version".equals(currentTag.getLocalName())
				&& ("dependency".equals(parentTag.getLocalName()) || "plugin".equals(parentTag.getLocalName()));
	}

	@Override
	public void invoke(Project project, Editor editor, @Nullable PsiElement element) {

		if (element == null) {
			return;
		}

		String pomContent = editor.getDocument().getText();
		var pomFile = element.getContainingFile() != null ? element.getContainingFile().getVirtualFile() : null;

		ProgressManager.getInstance().run(new DependencyCheckTask(project, pomFile, pomContent));
	}

}
