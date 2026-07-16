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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.assistant.Notifications;
import biz.paluch.dap.plan.PlannedUpgrade;
import biz.paluch.dap.plan.UpgradePlanToolWindowFactory;
import biz.paluch.dap.rule.BranchSource;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.rule.ResolutionContext;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.support.UpgradeResult;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Effects triggered from the dependency review dialog: applying updates to
 * build files with undo, adding entries to {@code dependencyfile.json}, and
 * handing the armed upgrades to the Upgrade Plan.
 *
 * @author Mark Paluch
 */
class AssistantReviewActions {

	private final Project project;

	private final boolean fromEditor;

	private final DependencyfileArtifactWriter writer;

	public AssistantReviewActions(Project project, boolean fromEditor) {
		this.project = project;
		this.fromEditor = fromEditor;
		this.writer = new DependencyfileArtifactWriter(project);
	}

	public boolean canAddToDependencyfile(TableRow row) {
		return writer.canAdd(row);
	}

	public void addToDependencyfile(TableRow row) {
		writer.add(row);
	}

	public void applyUpdates(Collection<VirtualFile> files, List<DependencyUpdate> updates,
			ProgressIndicator indicator) {

		List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll(project);
		DependencyRuleService ruleService = DependencyRuleService.getInstance(project);
		AppliedUpdates applied = new AppliedUpdates();

		// the platform asks before undoing when the batch spans several files or
		// the review was not opened from the editor; an editor-local single-file
		// apply undoes silently like any edit
		UndoConfirmationPolicy undoConfirmationPolicy = files.size() > 1 || !fromEditor
				? UndoConfirmationPolicy.REQUEST_CONFIRMATION
				: UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION;

		new BuildActionDelegate(project, (file, fileUpdates) -> {

			indicator.checkCanceled();
			indicator.setText2(file.getName());
			return applyToSupportingContexts(assistants, file, fileUpdates, context -> {

				for (DependencyUpdate fileUpdate : fileUpdates) {

					DependencyRule rule = ruleService.resolve(ResolutionContext.forAggregate(fileUpdate.artifactId(),
							fileUpdate.declarationSources(), BranchSource.of(file), context.getProjectVersion()));

					applied.record(file.getVirtualFile(), fileUpdate, rule);
				}
			});
		}).withGlobalUndo(undoConfirmationPolicy).updateBuildFiles(files, updates);

		Runnable undoFlagged = () -> new BuildActionDelegate(project,
				(file, fileUpdates) -> applyToSupportingContexts(assistants, file, fileUpdates, context -> {
				})).updateBuildFiles(applied.getReverseFiles(), applied.getReverse());

		// the balloon undo triggers the platform undo of the global apply command:
		// no confirmation of our own, only the platform's per the command's policy
		Runnable undo = () -> {

			UndoManager undoManager = UndoManager.getInstance(project);
			if (undoManager.isUndoAvailable(null)) {
				undoManager.undo(null);
			}
		};

		Notifications.updatesApplied(project, applied.applied(), undo, undoFlagged);
	}

	public void reportApplyError(Throwable error) {
		Notifications.error(project,
				MessageBundle.message("UpdateBuildFile.notification.error.title"),
				Notifications.errorMessage(error));
	}

	public void openInUpgradePlan(Map<PlannedUpgrade, UpgradeSelection> upgrades, FileScope scope) {

		Map<PlannedUpgrade, ArtifactVersion> targets = new LinkedHashMap<>();
		for (Map.Entry<PlannedUpgrade, UpgradeSelection> entry : upgrades.entrySet()) {
			ArtifactVersion target = entry.getValue().getTargetVersion();
			if (target == null) {
				throw new IllegalStateException(
						"Target version for " + entry.getKey().getName() + " is required");
			}
			targets.put(entry.getKey(), target);
		}

		UpgradePlanToolWindowFactory.openWith(project, targets, scope);
	}

	/**
	 * Apply the file's updates through every supporting assistant with an available
	 * context, invoking {@code afterApply} per applied context.
	 */
	private static UpgradeResult applyToSupportingContexts(List<DependencyAssistant> assistants, PsiFile file,
			List<DependencyUpdate> fileUpdates, Consumer<ProjectDependencyContext> afterApply) {

		UpgradeResult result = UpgradeResult.none();

		for (DependencyAssistant assistant : assistants) {

			if (!assistant.supports(file)) {
				continue;
			}

			ProjectDependencyContext context = assistant.createContext(file);
			if (!context.isAvailable()) {
				continue;
			}

			result = result.merge(context.applyUpdates(file, fileUpdates));
			afterApply.accept(context);
		}
		return result;
	}

}
