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

package biz.paluch.dap.assistant.action;

import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

/**
 * Intention action that launches a dependency check over the current build
 * file, opening the upgrade review for the resolved dependencies. Available
 * whenever the file is backed by a supported dependency context.
 *
 * @author Mark Paluch
 */
public class UpgradeDependenciesIntention extends BaseIntentionAction
		implements Iconable, HighPriorityAction {

	/**
	 * Singleton intention instance registered with IntelliJ.
	 */
	public static final UpgradeDependenciesIntention INSTANCE = new UpgradeDependenciesIntention();

	@Override
	public String getFamilyName() {
		return MessageBundle.message("intention.UpgradeDependencies.family");
	}

	@Override
	public String getText() {
		return MessageBundle.message("intention.UpgradeDependencies.text");
	}

	@Override
	public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
		return DependencyAssistantDispatcher.contextSupports(psiFile);
	}

	@Override
	public IntentionPreviewInfo generatePreview(Project project, Editor editor, PsiFile file) {
		return IntentionPreviewInfo.EMPTY;
	}

	@Override
	public void invoke(Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {

		if (DependencyAssistantDispatcher.findFirstContext(project, psiFile).isAvailable()) {
			ProgressManager.getInstance().run(new DependencyCheckTask(project, new UpgradeRequest(List.of(), psiFile)));
		}
	}

	@Override
	public Icon getIcon(int flags) {
		return DependencyAssistantIcons.ICON;
	}

	@Override
	public Priority getPriority() {
		return Priority.NORMAL;
	}

}
