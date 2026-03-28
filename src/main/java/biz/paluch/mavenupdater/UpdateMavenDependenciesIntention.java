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

import javax.swing.Icon;

import org.jspecify.annotations.Nullable;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

/**
 * Quick fix / intention in the POM XML editor when the caret is inside the &lt;version&gt; element of a
 * &lt;properties&gt;.
 */
public class UpdateMavenDependenciesIntention extends BaseElementAtCaretIntentionAction implements Iconable {

	@Override
	public String getFamilyName() {
		return MessageBundle.message("intention.family.name");
	}

	@Override
	public String getText() {
		return MessageBundle.message("action.title");
	}

	@Override
	public boolean isAvailable(Project project, Editor editor, @Nullable PsiElement element) {

		if (element == null || !(element.getContainingFile() instanceof XmlFile xmlFile)) {
			return false;
		}

		if (!MavenUtils.isMavenPomFile(xmlFile)) {
			return false;
		}

		XmlTag currentTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);
		if (currentTag == null) {
			return false;
		}

		if (currentTag.getLocalName().equals("properties")) {
			return true;
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
		VirtualFile pomFile = element.getContainingFile() != null ? element.getContainingFile().getVirtualFile() : null;

		ProgressManager.getInstance().run(new DependencyCheckTask(project, pomFile, pomContent));
	}

	@Override
	public Icon getIcon(int i) {
		return MavenUpdater.ICON;
	}
}
