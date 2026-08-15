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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyPresentation;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.assistant.DependencyPresentationFactory;
import biz.paluch.dap.assistant.Notifications;
import biz.paluch.dap.metadata.ProjectMetadata;
import biz.paluch.dap.metadata.ProjectMetadataService;
import biz.paluch.dap.plan.PlannedUpgrade;
import biz.paluch.dap.plan.UpgradePlanToolWindowFactory;
import biz.paluch.dap.rule.BranchSource;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleService;
import biz.paluch.dap.rule.ResolutionContext;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.upgrade.FileUpdateEngine;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StepsProgressIndicator;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;

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

	public AssistantReviewActions(Project project, boolean fromEditor) {
		this.project = project;
		this.fromEditor = fromEditor;
	}

	public void applyUpdates(Collection<VirtualFile> files, List<DependencyUpdate> updates,
			ProgressIndicator indicator) {

		DependencyRuleService ruleService = DependencyRuleService.getInstance(project);
		AppliedUpdates applied = new AppliedUpdates();

		// the platform asks before undoing when the batch spans several files or
		// the review was not opened from the editor; an editor-local single-file
		// apply undoes silently like any edit
		UndoConfirmationPolicy undoConfirmationPolicy = files.size() > 1 || !fromEditor
				? UndoConfirmationPolicy.REQUEST_CONFIRMATION
				: UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION;

		FileScope scope = FileScope.of(files);
		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, files.size());
		AtomicInteger updateCount = new AtomicInteger(files.size());
		ProjectMetadataService metadataService = ProjectMetadataService.getInstance(project);

		DependencyUpdates dependencyUpdates = new DependencyUpdates(updates) {

			@Override
			public void update(PsiFile file, DependencyUpdate update, Consumer<DependencyUpdate> updateTask) {
				FileUpdateEngine.ChangeTracker changeTracker = FileUpdateEngine.ChangeTracker.of(file);
				updateTask.accept(update);
				if (changeTracker.update(file)) {
					afterDependencyUpdate(file, update);
				}
			}

			@Override
			protected void afterDependencyUpdate(PsiFile file, DependencyUpdate update) {

				ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(file);
				ProjectMetadata metadata = metadataService.getMetadata(update.artifactId());
				DependencyRule rule = ruleService.resolve(ResolutionContext.forAggregate(update.artifactId(),
						update.declarationSources(), BranchSource.of(file), context.getProjectVersion(),
						metadata));

				PackageIdentity pkg = PackageIdentity.of(update.artifactId(), context.getPackageSystem());
				DependencyPresentation presentation = DependencyPresentationFactory.create(pkg,
						metadata.getProjectName(), rule, context.getInterfaceAssistant());

				applied.record(file.getVirtualFile(), update, rule, presentation);
			}

		};

		new BuildActionDelegate(project) {

			@Override
			protected void applyToFile(VirtualFile file, DependencyUpdates updates) {

				if (updateCount.decrementAndGet() >= 0) {
					steps.nextStep();
				}
				indicator.checkCanceled();
				indicator.setText2(file.getName());
				super.applyToFile(file, updates);
			}

		}.withGlobalUndo(undoConfirmationPolicy).updateBuildFiles(scope, dependencyUpdates);

		Runnable undoFlagged = () -> new BuildActionDelegate(project).updateBuildFiles(applied.getReverseFiles(),
				applied.getReverse());

		Runnable undo = () -> {

			UndoManager undoManager = UndoManager.getInstance(project);
			if (undoManager.isUndoAvailable(null)) {
				undoManager.undo(null);
			}
		};

		Notifications.updatesApplied(project, applied, undo, undoFlagged);
	}

	public void reportApplyError(Throwable error) {
		Notifications.error(project,
				MessageBundle.message("UpdateBuildFile.notification.error.title"),
				Notifications.errorMessage(error));
	}

	public void openInUpgradePlan(Map<PlannedUpgrade, ArtifactVersion> upgrades, FileScope scope) {
		UpgradePlanToolWindowFactory.openWith(project, upgrades, scope);
	}

}
