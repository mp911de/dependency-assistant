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

import java.util.HashSet;
import java.util.Set;

import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Apply all planned upgrades and create one commit per upgrade. When touched
 * files already carry uncommitted changes the run offers to shelve them and
 * continue on the clean scope; a clean scope falls back to a confirmation with
 * a "do not ask again" option. Disabled when the project has no version control
 * to commit into.
 *
 * @author Mark Paluch
 */
public class ApplyAllAndCommitAction extends ApplyAllAction {

	private static final Logger LOG = Logger.getInstance(ApplyAllAndCommitAction.class);

	/**
	 * Disabled when the project has no version control to commit into.
	 */
	@Override
	void update(AnActionEvent e, @Nullable UpgradePlanService service) {

		super.update(e, service);

		if (service == null || !service.hasVcs()) {
			e.getPresentation().setEnabled(false);
		}
	}

	@Override
	ApplyDecision confirm(Project project, int itemCount, FileScope dirty) {

		if (!dirty.isEmpty()) {
			// applying on top of a dirty scope would fold foreign edits into the
			// per-item commits; shelving is the only way to continue
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

	/**
	 * Notify that the upgrades were applied and committed.
	 */
	@Override
	void notifyDone(UpgradePlanService service, int applied, @Nullable Runnable unshelve) {
		new PlanNotifications().applied(service.getProject(), true, applied,
				service.getVcs().canPush() ? () -> push(service) : null, unshelve);
	}

	// the push support queues its own background task and posts its own result
	// notification; only the synchronous "nothing to push to" case surfaces here
	private static void push(UpgradePlanService service) {

		try {
			service.getVcs().push();
		} catch (IllegalStateException e) {
			LOG.warn("Push failed", e);
			new PlanNotifications().error(service.getProject(), MessageBundle.message("plan.push.error"), e);
		}
	}

}
