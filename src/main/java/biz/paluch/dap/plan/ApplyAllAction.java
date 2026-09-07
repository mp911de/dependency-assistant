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

package biz.paluch.dap.plan;

import java.io.IOException;

import java.util.List;

import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.notify.NotificationActions;
import biz.paluch.dap.notify.NotificationBuilder;
import biz.paluch.dap.notify.NotificationChannel;
import biz.paluch.dap.notify.Notifications;
import biz.paluch.dap.notify.UpgradeNotification;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.support.VersionControl;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageConstants;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;


import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Apply selected plan items, or the whole plan when nothing is selected.
 * <p>Dirty files can be shelved before applying. Clean-scope confirmation can
 * be disabled by the user.
 *
 * @author Mark Paluch
 */
public class ApplyAllAction extends UpgradePlanAction {

	private static final Logger LOG = Logger.getInstance(ApplyAllAction.class);

	@Override
	public void perform(Project project) {
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
			Notifications.warning(NotificationChannel.PLAN, MessageBundle.message("plan.apply.missing.title"),
					MessageBundle.message("plan.apply.missing.message", scope.getMissingPaths()))
					.notify(service.getProject());
			return;
		}
		VersionControl vcs = service.getVcs();
		FileScope dirty = vcs.dirtyInScope(scope);
		ApplyDecision decision = confirm(service.getProject(), items.size(), dirty);
		if (decision == ApplyDecision.CANCEL) {
			return;
		}

		boolean shelve = decision == ApplyDecision.SHELVE_AND_APPLY;
		PlanUpdateApplier applier = createApplier(service);

		Task.Modal task = new Task.Modal(project, MessageBundle.message("plan.apply.progress"), true) {

			private @Nullable NotificationAction unshelve;

			private AppliedUpdates appliedUpdates = new AppliedUpdates();

			@Override
			public void run(ProgressIndicator indicator) {

				indicator.setIndeterminate(false);

				try {
					if (shelve) {
						Runnable restore = vcs.shelve(dirty,
								MessageBundle.message("plan.shelve.message"));
						if (restore != null) {
							unshelve = NotificationActions.unshelve(restore);
						}
					}
					appliedUpdates = applier.apply(toApply, indicator);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void onSuccess() {
				notifyDone(service, appliedUpdates, unshelve);
			}

			@Override
			public void onCancel() {
				withUnshelve(Notifications.warning(NotificationChannel.PLAN, "",
						MessageBundle.message("plan.apply.cancelled"))).notify(project);
			}

			@Override
			public void onThrowable(Throwable error) {

				LOG.warn("Apply failed", error);
				withUnshelve(Notifications.error(NotificationChannel.PLAN, MessageBundle.message("plan.apply.error"),
						Notifications.errorMessage(error))).notify(project);
			}

			// offer shelf recovery once a shelf exists
			private NotificationBuilder withUnshelve(NotificationBuilder notification) {
				return unshelve != null ? notification.action(unshelve) : notification;
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
	 * Choose whether to apply over dirty files, shelve them first, or cancel.
	 */
	ApplyDecision confirm(Project project, int itemCount, FileScope dirty) {

		if (!dirty.isEmpty()) {
			return confirmDirtyApply(project, dirty);
		}

		return confirmClean(project, itemCount, "dependencyAssistant.upgradePlan.confirmApply",
				"plan.apply.confirm.title", "plan.apply.confirm.message") ? ApplyDecision.APPLY : ApplyDecision.CANCEL;
	}

	/**
	 * Report applied updates.
	 * @param unshelve shelf recovery action, or {@literal null} if no shelf was
	 * created.
	 */
	void notifyDone(UpgradePlanService service, AppliedUpdates applied,
			@Nullable NotificationAction unshelve) {

		Project project = service.getProject();
		NotificationBuilder notification = Notifications.applied(NotificationChannel.PLAN,
				UpgradeNotification.applied(applied));

		if (!applied.isEmpty()) {
			VersionControl vcs = service.getVcs();
			if (vcs.canCommit()) {
				notification.action(NotificationActions.commit(project, applied, vcs));
			}
			List<String> commandNames = applied.stream()
					.map(update -> UpdateApplier.getCommandName(update.displayName(), update.getTargetVersion()))
					.toList();
			notification.action(NotificationActions.undoAll(project, commandNames));
		}
		if (unshelve != null) {
			notification.action(unshelve);
		}
		notification.notify(project);
	}

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

	enum ApplyDecision {
		APPLY, SHELVE_AND_APPLY, CANCEL
	}

}
