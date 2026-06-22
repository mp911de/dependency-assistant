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

package biz.paluch.dap.assistant;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.support.AvailableUpgrades;
import biz.paluch.dap.support.UpgradeSuggestion;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

/**
 * Test driver that exercises the user-facing single-update entry points
 * ({@link UpdateDependencyAction} ModCommand and
 * {@link UpdateDependencyVersionQuickFix} editor quick fix) end-to-end through
 * an open editor and moves the editor caret exactly the way the platform does
 * in production.
 *
 * <p>Lives in {@code biz.paluch.dap.assistant} so it can reach the
 * package-private {@link UpgradeSuggestions} helper and the {@code protected}
 * action constructors the {@code DependencyVersionAnnotator} uses.
 *
 * @author Mark Paluch
 */
public class UpdateCaretTester {

	private final CodeInsightTestFixture fixture;

	public UpdateCaretTester(CodeInsightTestFixture fixture) {
		this.fixture = fixture;
	}

	/**
	 * Resolve the best available upgrade at the editor caret and apply it through
	 * the {@link UpdateDependencyAction} ModCommand path, moving the editor caret
	 * via the {@link biz.paluch.dap.artifact.VersionCaretRemap remap}.
	 */
	public void applyBestUpgradeViaModCommand() {

		Editor editor = fixture.getEditor();
		PsiFile file = fixture.getFile();

		Resolved resolved = resolve();
		UpdateDependencyAction action = new UpdateDependencyAction(resolved.versionElement(),
				resolved.suggestion().getStrategy(), resolved.context(),
				resolved.suggestion().toDependencyUpdate(), resolved.suggestion().getArtifactDeclaration());

		ActionContext context = ReadAction.compute(() -> ActionContext.from(editor, file));
		ModCommand command = ReadAction.compute(() -> ModCommand.psiUpdate(resolved.versionElement(),
				(element, updater) -> action.invoke(context, element, updater)));
		WriteCommandAction.runWriteCommandAction(fixture.getProject(),
				() -> ModCommandExecutor.getInstance().executeInteractively(context, command, editor));
	}

	/**
	 * Resolve the best available upgrade at the editor caret and apply it through
	 * the {@link UpdateDependencyVersionQuickFix} editor path, moving the editor
	 * caret via the {@link biz.paluch.dap.artifact.VersionCaretRemap remap}.
	 */
	public void applyBestUpgradeViaQuickFix() {

		Editor editor = fixture.getEditor();
		PsiFile file = fixture.getFile();

		Resolved resolved = resolve();
		UpdateDependencyVersionQuickFix fix = new UpdateDependencyVersionQuickFix(resolved.versionLiteral(),
				resolved.context(), resolved.suggestion());

		WriteCommandAction.runWriteCommandAction(fixture.getProject(),
				() -> fix.invoke(fixture.getProject(), editor, file));
	}

	/**
	 * Apply every available upgrade of the given strategy in the editor file
	 * through the {@link ApplyAllUpgradesIntention} batch path, which rewrites many
	 * sites and moves no caret.
	 */
	public void applyAllUpgrades(UpgradeStrategy strategy) {

		Editor editor = fixture.getEditor();
		PsiFile file = fixture.getFile();

		ProjectDependencyContext context = ReadAction
				.compute(() -> DependencyAssistantDispatcher.findFirstContext(file));
		ApplyAllUpgradesIntention intention = new ApplyAllUpgradesIntention(context, strategy);

		WriteCommandAction.runWriteCommandAction(fixture.getProject(),
				() -> intention.invoke(fixture.getProject(), editor, file));
	}

	private Resolved resolve() {
		return ReadAction.compute(() -> {

			Editor editor = fixture.getEditor();
			PsiFile file = fixture.getFile();
			int caret = editor.getCaretModel().getOffset();

			ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(file);
			if (context.isAbsent()) {
				throw new IllegalStateException("No dependency context for file at caret");
			}

			for (PsiElement candidate : PsiTreeUtil.collectElements(file, context::isVersionElement)) {

				TextRange range = candidate.getTextRange();
				if (range == null || !range.containsOffset(caret)) {
					continue;
				}

				AvailableUpgrades upgrades = UpgradeSuggestions.suggest(context, candidate);
				if (!upgrades.isPresent()) {
					continue;
				}

				UpgradeSuggestion suggestion = upgrades.getUpgradeSuggestion();
				PsiElement versionLiteral = suggestion.getArtifactDeclaration().getVersionLiteral();
				if (versionLiteral == null) {
					continue;
				}
				return new Resolved(context, suggestion, candidate, versionLiteral);
			}

			throw new IllegalStateException("No available upgrade at caret offset " + caret);
		});
	}

	private record Resolved(ProjectDependencyContext context, UpgradeSuggestion suggestion,
			PsiElement versionElement, PsiElement versionLiteral) {
	}

}
