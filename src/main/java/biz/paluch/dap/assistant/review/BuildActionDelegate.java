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

package biz.paluch.dap.assistant.review;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.assistant.Notifications;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeResult;
import biz.paluch.dap.upgrade.BuildFileUpdater;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

/**
 * Delegate to update build files providing write action guarding.
 *
 * <p>Every write runs inside a {@link WriteCommandAction} tagged with a shared
 * command group, so a fan-out of writes issued back-to-back (one chosen target
 * routed to several files) coalesces into a single undoable command.
 *
 * @author Mark Paluch
 * @see BuildFileUpdater
 */
public class BuildActionDelegate implements BuildFileUpdater {

	private static final Logger LOG = Logger.getInstance(BuildActionDelegate.class);

	private final Project project;

	private final BiFunction<PsiFile, List<DependencyUpdate>, UpgradeResult> updateAction;

	private final BetterPsiManager psiManager;

	private UndoConfirmationPolicy undoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;

	private boolean globalUndo;

	/**
	 * Create a delegate using the update action from the given dependency context.
	 */
	public BuildActionDelegate(Project project, ProjectDependencyContext dependencyContext) {
		this(project, dependencyContext::applyUpdates);
	}

	public BuildActionDelegate(Project project,
			BiFunction<PsiFile, List<DependencyUpdate>, UpgradeResult> updateAction) {
		this.project = project;
		this.updateAction = updateAction;
		this.psiManager = BetterPsiManager.getInstance(project);
	}

	/**
	 * Configure the write commands for a batch driven from outside a single editor:
	 * global undo so the whole batch reverts as one step from any undo context,
	 * with the given policy deciding whether the platform asks for confirmation
	 * before undoing.
	 *
	 * @param undoConfirmationPolicy the confirmation policy applied on undo.
	 * @return {@code this} delegate.
	 */
	public BuildActionDelegate withGlobalUndo(UndoConfirmationPolicy undoConfirmationPolicy) {
		this.undoConfirmationPolicy = undoConfirmationPolicy;
		this.globalUndo = true;
		return this;
	}

	@Override
	public UpgradeResult updateBuildFile(VirtualFile file, List<DependencyUpdate> updates) {

		if (updates.isEmpty()) {
			return UpgradeResult.none();
		}

		return runCommand(() -> applyToFile(file, updates));
	}

	@Override
	public UpgradeResult updateBuildFiles(Collection<VirtualFile> files, List<DependencyUpdate> updates) {

		if (files.isEmpty() || updates.isEmpty()) {
			return UpgradeResult.none();
		}

		return runCommand(() -> {
			UpgradeResult result = UpgradeResult.none();
			for (VirtualFile file : files) {
				result = result.merge(applyToFile(file, updates));
			}
			return result;
		});
	}

	private UpgradeResult runCommand(Supplier<UpgradeResult> command) {

		Ref<UpgradeResult> result = Ref.create(UpgradeResult.none());
		WriteCommandAction.Builder action = WriteCommandAction.writeCommandAction(project)
				.withName(MessageBundle.message("UpdateBuildFile.title"))
				.withGroupId(UPDATE_COMMAND_GROUP)
				.withUndoConfirmationPolicy(undoConfirmationPolicy);
		if (globalUndo) {
			action = action.withGlobalUndo();
		}
		action.run(() -> result.set(command.get()));
		return result.get();
	}

	private UpgradeResult applyToFile(VirtualFile file, List<DependencyUpdate> updates) {

		Document document = FileDocumentManager.getInstance().getDocument(file);
		if (document != null) {
			PsiDocumentManager.getInstance(project).commitDocument(document);
		}

		PsiFile psiFile = psiManager.findFile(file);
		if (psiFile == null) {
			Notifications.error(project, MessageBundle.message("UpdateBuildFile.notification.error.title"),
					MessageBundle.message("UpdateBuildFile.notification.no-file", file.getPresentableUrl()));
			return UpgradeResult.none();
		}

		try {
			return updateAction.apply(psiFile, updates);
		} catch (ProcessCanceledException ex) {
			throw ex;
		} catch (Exception ex) {
			LOG.warn("Build file update failed", ex);
			Notifications.error(project, MessageBundle.message("UpdateBuildFile.notification.error.title"),
					MessageBundle.message("UpdateBuildFile.notification.failed", file.getPresentableUrl(),
							Notifications.errorMessage(ex)));
			return UpgradeResult.none();
		}
	}

}
