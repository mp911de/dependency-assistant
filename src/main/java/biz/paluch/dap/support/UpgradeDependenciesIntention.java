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
package biz.paluch.dap.support;

import javax.swing.*;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.ProjectDependencyContext;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Light-bulb intention action for supported dependency build files.
 *
 * @author Mark Paluch
 */
public class UpgradeDependenciesIntention extends BaseElementAtCaretIntentionAction implements Iconable {

	public static final UpgradeDependenciesIntention INSTANCE = new UpgradeDependenciesIntention();

	@Override
	public String getFamilyName() {
		return MessageBundle.message("intention.family.name");
	}

	@Override
	public String getText() {
		return MessageBundle.message("biz.paluch.dap.UpdateDependencies.text");
	}

	@Override
	public boolean isAvailable(Project project, Editor editor, @Nullable PsiElement element) {

		if (element == null) {
			return false;
		}

		ProjectDependencyContext context = context(project, element);
		return context != null && context.resolveReference(element).isResolved();
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

		VirtualFile buildFile = element.getContainingFile().getVirtualFile();
		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project,
				element.getContainingFile());
		if (buildFile != null && context != null) {
			ProgressManager.getInstance().run(new DependencyCheckTask(project, buildFile, context));
		}
	}

	@Override
	public Icon getIcon(int flags) {
		return DependencyAssistantIcons.ICON;
	}

	private static @Nullable ProjectDependencyContext context(Project project, PsiElement element) {

		PsiFile file = element.getContainingFile();
		return file != null ? DependencyAssistantDispatcher.findFirstContext(project, file.getContainingFile()) : null;
	}

}
