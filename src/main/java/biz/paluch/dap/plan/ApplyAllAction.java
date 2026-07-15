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

import java.util.List;

import biz.paluch.dap.util.MessageBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageConstants;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Apply all planned upgrades to the build files, guarded by a confirmation with
 * a "do not ask again" option as a last resort against accidental clicks.
 *
 * @author Mark Paluch
 */
public class ApplyAllAction extends UpgradePlanAction {

	private static final Logger LOG = Logger.getInstance(ApplyAllAction.class);


	@Override
	void perform(Project project) {
		// no-op
	}

	@Override
	public void actionPerformed(AnActionEvent e) {

		Project project = e.getProject();
		if (project == null) {
			return;
		}

		UpgradePlanService service = UpgradePlanService.getInstance(project);
		if (service.isBusy()) {
			return;
		}

		UpgradePlan plan = service.getUpgradePlan().rebuild();
		List<UpgradePlanItem> items = PlanSelection.from(e)
				.orElseGet(plan::getItems);
		UpgradePlan toApply = plan.withItems(items);

		FileScope scope = toApply.getScope();
		if (scope.hasMissingFiles()) {
			new PlanNotifications().warning(service.getProject(), MessageBundle.message("plan.apply.missing.title"),
					MessageBundle.message("plan.apply.missing.message", scope.getMissingPaths()));
			return;
		}
		FileScope dirty = service.getVcs().dirtyInScope(scope);

		ApplyDecision decision = confirm(service.getProject(), items.size(), dirty);
		if (decision == ApplyDecision.CANCEL) {
			return;
		}
		boolean shelve = decision == ApplyDecision.SHELVE_AND_APPLY;
		PlanUpdateApplier applier = createApplier(service);

		Task.Modal task = new Task.Modal(project, MessageBundle.message("plan.apply.progress"), true) {

			private @Nullable Runnable unshelve;

			private int applied;

			@Override
			public void run(ProgressIndicator indicator) {

				indicator.setIndeterminate(false);

				try {
					if (shelve) {
						ShelvedChangeList shelf = service.getVcs().shelve(dirty,
								MessageBundle.message("plan.shelve.message"));
						if (shelf != null) {
							unshelve = () -> service.getVcs().unshelve(shelf);
						}
					}
					applied = applier.apply(toApply, indicator);
				} catch (VcsException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void onSuccess() {
				notifyDone(service, applied, unshelve);
			}

			@Override
			public void onCancel() {
				new PlanNotifications().cancelled(project, unshelve);
			}

			@Override
			public void onThrowable(Throwable error) {

				LOG.warn("Apply failed", error);
				new PlanNotifications().error(project, MessageBundle.message("plan.apply.error"), error, unshelve);
			}

			@Override
			public void onFinished() {
				try {
					finishRun(service, toApply);
				} finally {
					service.setBusy(false);
				}
			}

		};

		service.setBusy(true);
		try {
			task.queue();
		} catch (RuntimeException ex) {
			service.setBusy(false);
			throw ex;
		}
	}

	PlanUpdateApplier createApplier(UpgradePlanService service) {
		return new UpdateApplier(service);
	}

	void finishRun(UpgradePlanService service, UpgradePlan attempted) {
	}

	/**
	 * Confirm before applying. Plain apply offers to shelve a dirty scope or to
	 * apply on top of it, and falls back to the do-not-ask-again confirmation on a
	 * clean scope.
	 */
	ApplyDecision confirm(Project project, int itemCount, FileScope dirty) {

		if (!dirty.isEmpty()) {
			return confirmDirtyApply(project, dirty);
		}

		return confirmClean(project, itemCount, "dependencyAssistant.upgradePlan.confirmApply",
				"plan.apply.confirm.title", "plan.apply.confirm.message") ? ApplyDecision.APPLY : ApplyDecision.CANCEL;
	}

	/**
	 * Notify that the plan was applied, offering to restore a shelf created for the
	 * run.
	 */
	void notifyDone(UpgradePlanService service, int applied, @Nullable Runnable unshelve) {
		new PlanNotifications().applied(service.getProject(), false, applied, null, unshelve);
	}

	/**
	 * Last-resort confirmation for a clean scope, with the platform's
	 * do-not-ask-again option persisted under the given property.
	 */
	boolean confirmClean(Project project, int itemCount, String doNotAskProperty, String titleKey, String messageKey) {

		if (PropertiesComponent.getInstance().getBoolean(doNotAskProperty)) {
			return true;
		}

		return MessageDialogBuilder
				.yesNo(MessageBundle.message(titleKey), MessageBundle.message(messageKey, itemCount))
				.icon(UIUtil.getQuestionIcon())
				.doNotAsk(new DoNotAskOption.Adapter() {

					@Override
					public void rememberChoice(boolean selected, int exitCode) {

						if (selected && exitCode == Messages.YES) {
							PropertiesComponent.getInstance()
									.setValue(doNotAskProperty, true);
						}
					}

				})
				.ask(project);
	}

	private ApplyDecision confirmDirtyApply(Project project, FileScope dirty) {

		int choice = MessageDialogBuilder.yesNoCancel(MessageBundle.message("plan.apply.dirty.title"),
				MessageBundle.message("plan.apply.dirty.message", dirty.getPresentablePaths()))
				.yesText(MessageBundle.message("plan.dirty.shelve"))
				.noText(MessageBundle.message("plan.dirty.anyway"))
				.icon(UIUtil.getWarningIcon())
				.show(project);

		if (choice == MessageConstants.YES) {
			return ApplyDecision.SHELVE_AND_APPLY;
		}
		return choice == MessageConstants.NO ? ApplyDecision.APPLY : ApplyDecision.CANCEL;
	}

	/**
	 * Outcome of the pre-apply confirmation: proceed, shelve the dirty scope files
	 * first and then proceed, or abort.
	 */
	enum ApplyDecision {
		APPLY, SHELVE_AND_APPLY, CANCEL
	}

}
