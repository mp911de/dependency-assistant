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

package biz.paluch.dap.assistant.review;

import java.util.List;
import java.util.function.BiConsumer;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.assistant.Notifications;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.upgrade.FileUpdateEngine;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StepsProgressIndicator;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Applies dependency updates to build files inside platform write commands.
 *
 * <p>By default, each file is routed through the registered build-tool
 * integrations. A multi-file update uses one write command per file and can be
 * configured as one globally undoable operation through
 * {@link #withGlobalUndo(UndoConfirmationPolicy)}.
 *
 * @author Mark Paluch
 */
public class FileUpdateDelegate {

	public static final String UPDATE_COMMAND_GROUP = "biz.paluch.dap.UpdateDependencies";

	private static final Logger LOG = Logger.getInstance(FileUpdateDelegate.class);

	private final Project project;

	private final FileUpdateEngine engine;

	private final BetterPsiManager psiManager;

	private UndoConfirmationPolicy undoConfirmationPolicy = UndoConfirmationPolicy.DEFAULT;

	private boolean globalUndo;

	/**
	 * Create a delegate that routes each file through the registered build-tool
	 * integrations.
	 *
	 * @param project the project whose files are updated.
	 */
	public FileUpdateDelegate(Project project) {
		this(project, new FileUpdateEngine(project));
	}

	/**
	 * Create a delegate that applies every update through the given dependency
	 * context.
	 *
	 * @param project the project whose files are updated.
	 * @param dependencyContext the context that performs the updates.
	 */
	public FileUpdateDelegate(Project project, ProjectDependencyContext dependencyContext) {
		this(project, new FileUpdateEngine(project, FileUpdateEngine.context(dependencyContext)));
	}

	public FileUpdateDelegate(Project project,
			BiConsumer<PsiFile, DependencyUpdates> updateFunction) {
		this.project = project;
		this.engine = new FileUpdateEngine(project,
				(source, target, updates) -> updateFunction.accept(target, updates));
		this.psiManager = BetterPsiManager.getInstance(project);
	}

	public FileUpdateDelegate(Project project,
			FileUpdateEngine engine) {
		this.project = project;
		this.engine = engine;
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
	public FileUpdateDelegate withGlobalUndo(UndoConfirmationPolicy undoConfirmationPolicy) {
		this.undoConfirmationPolicy = undoConfirmationPolicy;
		this.globalUndo = true;
		return this;
	}

	/**
	 * Update the given files with the given updates.
	 *
	 * <p>Every file's writes run inside their own {@link WriteCommandAction} tagged
	 * with a shared command group, so a fan-out of writes issued back-to-back (one
	 * chosen target routed to several files) coalesces into a single undoable step
	 * while the EDT stays free between files. A file-specific failure is reported
	 * and does not prevent later files from being processed.
	 *
	 * @param indicator the progress and cancellation indicator.
	 * @param files the build-file scope to update.
	 * @param updates the updates to route to each file.
	 */
	public void updateFiles(ProgressIndicator indicator, FileScope files, DependencyUpdates updates) {

		List<VirtualFile> filesList = files.toList();
		if (filesList.isEmpty() || updates.isEmpty()) {
			return;
		}
		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, filesList.size());
		steps.setIndeterminate(false);
		steps.setFraction(0);
		CommandProcessor commandProcessor = CommandProcessor.getInstance();
		Application application = ApplicationManager.getApplication();

		for (VirtualFile file : filesList) {
			steps.checkCanceled();
			steps.setText2(file.getName());

			Runnable runnable = () -> applyUpdates(file, updates);

			application.invokeAndWait(
					() -> {
						commandProcessor.allowMergeGlobalCommands(() -> {
							runCommand(runnable);
						});
					});
			steps.nextStep();
		}
	}

	protected void runCommand(Runnable command) {

		WriteCommandAction.Builder action = WriteCommandAction.writeCommandAction(project)
				.withName(MessageBundle.message("UpdateBuildFile.title"))
				.withGroupId(UPDATE_COMMAND_GROUP)
				.withUndoConfirmationPolicy(undoConfirmationPolicy);
		if (globalUndo) {
			action = action.withGlobalUndo();
		}
		action.run(command::run);
	}

	protected void applyUpdates(VirtualFile file, DependencyUpdates updates) {

		PsiFile psiFile = psiManager.findFile(file);
		if (psiFile == null) {
			Notifications.error(project, MessageBundle.message("UpdateBuildFile.notification.error.title"),
					MessageBundle.message("UpdateBuildFile.notification.no-file", file.getPresentableUrl()));
			return;
		}

		try {
			engine.applyUpdates(file, updates);
		} catch (ProcessCanceledException ex) {
			throw ex;
		} catch (Exception ex) {
			LOG.warn("Build file update failed", ex);
			Notifications.error(project, MessageBundle.message("UpdateBuildFile.notification.error.title"),
					MessageBundle.message("UpdateBuildFile.notification.failed", file.getPresentableUrl(),
							Notifications.errorMessage(ex)));
		}
	}

}
