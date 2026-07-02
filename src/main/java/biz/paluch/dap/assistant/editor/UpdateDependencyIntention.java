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

package biz.paluch.dap.assistant.editor;

import java.util.List;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link IntentionAction} that upgrades a single dependency declaration to the
 * version carried by its {@link DependencyUpdate}, applying the change through
 * the owning {@link ProjectDependencyContext}.
 *
 * <p>Also implements {@link FileModifier} so the platform can render an
 * intention preview against a copied file via
 * {@link #getFileModifierForPreview(PsiFile)}.
 *
 * @author Mark Paluch
 */
class UpdateDependencyIntention implements IntentionAction, FileModifier {

	private final ProjectDependencyContext dependencyContext;

	private final DependencyUpdate update;

	private final ArtifactDeclaration declaration;

	private final PsiFile file;

	UpdateDependencyIntention(ProjectDependencyContext dependencyContext, DependencyUpdate update,
			ArtifactDeclaration declaration, PsiFile file) {
		this.dependencyContext = dependencyContext;
		this.update = update;
		this.declaration = declaration;
		this.file = file;
	}

	public @IntentionName String getText() {
		return MessageBundle.message("UpgradeDependencyAction.name", update.versionAsString());
	}

	@Override
	public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
		return psiFile.isEquivalentTo(this.file) && dependencyContext.isAvailable();
	}

	@Override
	public boolean startInWriteAction() {
		return true;
	}

	@Override
	public @IntentionFamilyName String getFamilyName() {
		return MessageBundle.message("problemgroup.upgrade-available");
	}

	@Override
	public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
		dependencyContext.applyUpdates(psiFile, List.of(update));
	}

	@Override
	public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
		return new UpdateDependencyIntention(dependencyContext, update, declaration, target);
	}

}
