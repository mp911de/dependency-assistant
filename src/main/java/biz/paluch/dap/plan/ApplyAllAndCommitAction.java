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

import java.util.HashSet;
import java.util.Set;

import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.notify.NotificationActions;
import biz.paluch.dap.notify.NotificationBuilder;
import biz.paluch.dap.notify.NotificationChannel;
import biz.paluch.dap.notify.Notifications;
import biz.paluch.dap.notify.UpgradeNotification;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Apply plan items with one commit per changed item.
 * <p>Selection narrows the run. Dirty files must be shelved before proceeding.
 *
 * @author Mark Paluch
 */
public class ApplyAllAndCommitAction extends ApplyAllAction {

	private static final Logger LOG = Logger.getInstance(ApplyAllAndCommitAction.class);

	@Override
	public void update(AnActionEvent e, @Nullable UpgradePlanService service) {

		super.update(e, service);

		if (service == null || !service.hasVcs()) {
			e.getPresentation().setEnabled(false);
		}
	}

	@Override
	ApplyDecision confirm(Project project, int itemCount, FileScope dirty) {

		if (!dirty.isEmpty()) {
			boolean shelve = MessageDialogBuilder.yesNo(MessageBundle.message("plan.commit.dirty.title"),
					MessageBundle.message("plan.commit.dirty.message", dirty.getPresentablePaths()))
					.yesText(MessageBundle.message("plan.dirty.shelve"))
					.noText(Messages.getCancelButton())
					.icon(UIUtil.getWarningIcon())
					.ask(project);
			return shelve ? ApplyDecision.SHELVE_AND_APPLY : ApplyDecision.CANCEL;
		}

		return confirmClean(project, itemCount, "dependencyAssistant.upgradePlan.confirmApplyAndCommit",
				"plan.commit.confirm.title", "plan.commit.confirm.message") ? ApplyDecision.APPLY
						: ApplyDecision.CANCEL;
	}

	@Override
	PlanUpdateApplier createApplier(UpgradePlanService service) {
		return new VcsUpdateApplier(service);
	}

	@Override
	void finishRun(UpgradePlanService service, UpgradePlan attempted) {
		Set<UpgradePlanItem> remaining = new HashSet<>(service.getUpgradePlan().getItems());
		if (attempted.stream().anyMatch(item -> !remaining.contains(item))) {
			service.vcsApplied(attempted.getScope());
		}
	}

	@Override
	void notifyDone(UpgradePlanService service, AppliedUpdates applied,
			@Nullable NotificationAction unshelve) {

		NotificationBuilder notification = Notifications.applied(NotificationChannel.PLAN,
				UpgradeNotification.committed(applied));

		// nothing to push when nothing was committed
		if (!applied.isEmpty() && service.getVcs().canPush()) {
			notification.action(NotificationActions.push(() -> push(service)));
		}
		if (unshelve != null) {
			notification.action(unshelve);
		}
		notification.notify(service.getProject());
	}

	private static void push(UpgradePlanService service) {
		try {
			service.getVcs().push();
		} catch (IllegalStateException e) {
			LOG.warn("Push failed", e);
			Notifications.error(NotificationChannel.PLAN, MessageBundle.message("plan.push.error"),
					Notifications.errorMessage(e)).notify(service.getProject());
		}
	}

}
