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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.support.UpgradeResult;
import biz.paluch.dap.upgrade.FileUpdateEngine;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;

/**
 * Apply-and-commit transaction that creates one commit per changed plan item.
 *
 * <p>Ticketed items are applied before unticketed items. Plan Order is
 * preserved within each partition.
 *
 * @author Mark Paluch
 */
class VcsUpdateApplier implements PlanUpdateApplier {

	private final UpgradePlanService service;

	private final PlanVcs vcs;

	private final FileUpdateEngine engine;

	VcsUpdateApplier(UpgradePlanService service) {
		this(service, service.getVcs(), new FileUpdateEngine(service.getProject()));
	}

	VcsUpdateApplier(UpgradePlanService service, PlanVcs vcs, FileUpdateEngine engine) {
		this.service = service;
		this.vcs = vcs;
		this.engine = engine;
	}

	@Override
	public AppliedUpdates apply(UpgradePlan plan, ProgressIndicator indicator) throws VcsException {

		AppliedUpdates appliedUpdates = new AppliedUpdates();
		List<UpgradePlanItem> ordered = ticketedFirst(plan);
		FileScope scope = plan.getScope();
		doWithItems(ordered, indicator, it -> {

			List<DependencyUpdate> updates = it.createUpdates();
			UpgradeResult result = applyItem(scope, updates,
					applied -> appliedUpdates.record(scope, applied, it.getDisplayName()));
			if (result.hasChanges()) {
				commit(scope, it);
			}
		});

		return appliedUpdates;
	}

	private UpgradeResult applyItem(FileScope scope, List<DependencyUpdate> updates,
			Consumer<DependencyUpdate> afterApply) {

		return WriteAction.computeAndWait(() -> {
			AtomicReference<UpgradeResult> result = new AtomicReference<>();
			CommandProcessor instance = CommandProcessor.getInstance();
			instance.runUndoTransparentAction(() -> {
				try {
					result.set(engine.apply(scope, updates, afterApply));
				} catch (RuntimeException ex) {
					throw ex;
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			});

			return result.get();
		});
	}

	private void commit(FileScope scope, UpgradePlanItem item) throws VcsException {

		boolean committed;
		try {
			committed = vcs.commit(scope, service.getCommitMessage(item));
		} catch (VcsException commitFailure) {
			// Some VCS implementations can report an error after creating the commit.
			// Keep the plan aligned with the repository in that case.
			if (!vcs.hasChanges(scope)) {
				service.removeCommittedItem(item);
			}
			throw commitFailure;
		}

		service.removeCommittedItem(item);
		if (!committed) {
			throw new VcsException(MessageBundle.message("plan.vcs.commit.no-changes"));
		}
	}

	private static List<UpgradePlanItem> ticketedFirst(UpgradePlan plan) {

		List<UpgradePlanItem> ordered = new ArrayList<>(plan.size());
		plan.stream().filter(UpgradePlanItem::hasTicket).forEach(ordered::add);
		plan.stream().filter(item -> !item.hasTicket()).forEach(ordered::add);
		return ordered;
	}

}
