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

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.MessageBundle;

import javax.swing.Icon;

import org.jspecify.annotations.Nullable;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Light-bulb intention action for Gradle build files. Launches the dependency version check dialog when the caret is
 * inside a version string or property value.
 *
 * @author Mark Paluch
 */
public class UpdateGradleDependenciesIntention extends BaseElementAtCaretIntentionAction implements Iconable {

	public static final UpdateGradleDependenciesIntention INSTANCE = new UpdateGradleDependenciesIntention();

	@Override
	public String getFamilyName() {
		return MessageBundle.message("gradle.intention.family.name");
	}

	@Override
	public String getText() {
		return MessageBundle.message("biz.paluch.dap.gradle.UpdateDependencies.text");
	}

	@Override
	public boolean isAvailable(Project project, Editor editor, @Nullable PsiElement element) {
		if (element == null) {
			return false;
		}
		PsiFile file = element.getContainingFile();
		return GradleUtils.isGradleFile(file);
	}

	@Override
	public boolean startInWriteAction() {
		return false;
	}

	@Override
	public IntentionPreviewInfo generatePreview(Project project, Editor editor, PsiFile file) {
		return IntentionPreviewInfo.EMPTY;
	}

	@Override
	public void invoke(Project project, Editor editor, @Nullable PsiElement element) {

		if (element == null) {
			return;
		}

		PsiFile buildFile = element.getContainingFile();
		ProgressManager.getInstance().run(new DependencyCheckTask(project, buildFile));
	}

	@Override
	public Icon getIcon(int i) {
		return DependencyAssistantIcons.GRADLE_ICON;
	}

}
