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

package biz.paluch.dap.plan;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;

/**
 * Preview the planned changes as a multi-file diff without modifying any file:
 * the covered items run against in-memory copies of the scope build files, and
 * the changed files show as a VCS-style diff chain of immutable before/after
 * snapshots. A tree selection narrows the preview to the selected items (a
 * group always as a whole); without a selection the whole plan previews. Files
 * no planned update matches stay out of the diff.
 *
 * @author Mark Paluch
 */
public class PreviewChangesAction extends UpgradePlanAction {

	@Override
	public void actionPerformed(AnActionEvent e) {

		Project project = e.getProject();
		if (project == null) {
			return;
		}

		UpgradePlanService service = UpgradePlanService.getInstance(project);
		UpgradePlan plan = service.getUpgradePlan();
		preview(service, plan.withItems(PlanSelection.from(e).orElseGet(plan::getItems)));
	}

	/**
	 * Selection-less entry: previews the whole plan.
	 */
	@Override
	void perform(Project project) {

		UpgradePlanService service = UpgradePlanService.getInstance(project);
		preview(service, service.getUpgradePlan());
	}

	/**
	 * Preview the planned changes of the given plan as a multi-file diff; callers
	 * narrow to a selection through {@link UpgradePlan#withItems}. Also the
	 * double-click entry from the plan tree; no-op while a plan run is in flight,
	 * matching the muted actions.
	 */
	static void preview(UpgradePlanService service, UpgradePlan plan) {

		if (plan.isEmpty() || service.isBusy()) {
			return;
		}

		Project project = service.getProject();
		UpgradePlan rebuilt = plan.rebuild();
		FileScope scope = rebuilt.getScope();
		if (scope.hasMissingFiles()) {
			// preview the resolvable rest; unlike apply, reading a partial scope is safe
			new PlanNotifications().warning(project, MessageBundle.message("plan.apply.missing.title"),
					MessageBundle.message("plan.preview.missing.message", scope.getMissingPaths()));
		}

		PlanPreview preview = new PlanPreview(service, rebuilt);
		ReadAction.nonBlocking(preview::createChanges)
				.inSmartMode(project)
				.expireWhen(service::isDisposed)
				.finishOnUiThread(ModalityState.nonModal(), changes -> show(project, changes))
				.submit(AppExecutorUtil.getAppExecutorService());
	}

	private static void show(Project project, List<FileChange> changes) {

		DiffContentFactory factory = DiffContentFactory.getInstance();
		List<DiffRequest> requests = new ArrayList<>(changes.size());
		for (FileChange change : changes) {

			DiffContent before = factory.create(project, change.before(), change.file());
			DiffContent after = factory.create(project, change.after(), change.file());
			requests.add(new SimpleDiffRequest(change.file().getPresentableUrl(), before, after,
					MessageBundle.message("plan.preview.current"), MessageBundle.message("plan.preview.after")));
		}

		DiffManager.getInstance()
				.showDiff(project, new SimpleDiffRequestChain(requests), DiffDialogHints.DEFAULT);
	}

	/**
	 * Creates Plan changes and renders these as {@link FileChange}.
	 *
	 * @author Mark Paluch
	 */
	static class PlanPreview {

		private final UpgradePlanService service;

		private final BetterPsiManager psiManager;

		private final UpgradePlan plan;

		PlanPreview(UpgradePlanService service, UpgradePlan plan) {
			this.psiManager = BetterPsiManager.getInstance(service.getProject());
			this.service = service;
			this.plan = plan;
		}

		List<FileChange> createChanges() {

			FileUpdateEngine engine = new FileUpdateEngine(service.getProject());

			List<DependencyUpdate> updates = new ArrayList<>();
			for (UpgradePlanItem item : plan) {
				updates.addAll(item.createUpdates());
			}

			List<FileChange> changes = new ArrayList<>();
			for (VirtualFile file : plan.getScope()) {

				ProgressManager.checkCanceled();
				psiManager.doWithFile(file, original -> {
					PsiFile copy = (PsiFile) original.copy();
					engine.applyToFile(original, copy, updates);
					String beforeText = original.getText();
					String afterText = copy.getText();
					if (!afterText.equals(beforeText)) {
						changes.add(new FileChange(file, beforeText, afterText));
					}
				});
			}

			return changes;
		}

	}

	/**
	 * Before/after text snapshot of one build file the plan would change.
	 */
	record FileChange(VirtualFile file, String before, String after) {

	}

}
