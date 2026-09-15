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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.support.UpgradeResult;
import biz.paluch.dap.support.VersionControl;
import biz.paluch.dap.upgrade.FileUpdateEngine;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StepsProgressIndicator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;

/**
 * Executes one Upgrade Plan with platform undo or per-item VCS commits.
 * <p>Create an executor for each run and invoke an operation on a background
 * thread. The executor owns write commands and execution completion.
 *
 * @author Mark Paluch
 */
class UpgradePlanExecutor {

	private final UpgradePlanService service;

	private final UpgradePlan plan;

	private final ProgressIndicator indicator;

	private final VersionControl vcs;

	private final FileUpdateEngine engine;

	private boolean committedItemsRemoved;

	UpgradePlanExecutor(UpgradePlanService service, UpgradePlan plan, ProgressIndicator indicator) {
		this(service, plan, indicator, service.getVcs(), new FileUpdateEngine(service.getProject()));
	}

	private UpgradePlanExecutor(UpgradePlanService service, UpgradePlan plan, ProgressIndicator indicator,
			VersionControl vcs, FileUpdateEngine engine) {
		this.service = service;
		this.plan = plan;
		this.indicator = indicator;
		this.vcs = vcs;
		this.engine = engine;
	}

	/**
	 * Apply items in plan order, with file changes and plan removal sharing an
	 * undoable command per item.
	 * @return the applied updates.
	 */
	public AppliedUpdates apply() {

		AppliedUpdates appliedUpdates = new AppliedUpdates();
		if (plan.isEmpty()) {
			return appliedUpdates;
		}

		FileScope scope = plan.getScope();
		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, plan.size());
		for (PlannedUpgrade item : plan) {
			reportProgress(item);
			List<DependencyUpdate> updates = item.createUpdates();
			WriteCommandAction.writeCommandAction(service.getProject())
					.withName(getCommandName(item.getDisplayName(), item.getToVersion()))
					.withGlobalUndo()
					.run(() -> engine.apply(scope, updates, applied -> {
						appliedUpdates.record(scope, applied, item.getDisplayName());
						service.removeItem(item);
					}));
			steps.nextStep();
		}
		return appliedUpdates;
	}

	/**
	 * Apply and commit each changed item, processing ticketed items first and
	 * retaining plan order within each partition.
	 * @return the applied updates.
	 * @throws IOException if the scope cannot be committed or a VCS operation
	 * fails.
	 */
	public AppliedUpdates applyAndCommit() throws IOException {

		FileScope scope = plan.getScope();
		if (!vcs.canCommit(scope)) {
			throw new IOException(MessageBundle.message("plan.vcs.commit.unsupported"));
		}

		AppliedUpdates appliedUpdates = new AppliedUpdates();
		if (plan.isEmpty()) {
			return appliedUpdates;
		}

		List<PlannedUpgrade> ordered = new ArrayList<>(plan.size());
		plan.stream().filter(PlannedUpgrade::hasTicket).forEach(ordered::add);
		plan.stream().filter(item -> !item.hasTicket()).forEach(ordered::add);
		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, ordered.size());
		try {
			for (PlannedUpgrade item : ordered) {
				reportProgress(item);
				UpgradeResult result = applyItem(item.createUpdates(),
						applied -> appliedUpdates.record(scope, applied, item.getDisplayName()));
				if (result.hasChanges()) {
					commit(item);
				}
				steps.nextStep();
			}
		} finally {
			if (committedItemsRemoved) {
				ProgressManager.getInstance().executeNonCancelableSection(
						() -> ApplicationManager.getApplication().invokeAndWait(() -> service.vcsApplied(scope),
								indicator.getModalityState()));
			}
		}
		return appliedUpdates;
	}

	private void reportProgress(PlannedUpgrade item) {
		indicator.checkCanceled();
		indicator.setText2(MessageBundle.message("plan.apply.upgrading", item.getDisplayName(), item.getToVersion()));
	}

	private UpgradeResult applyItem(List<DependencyUpdate> updates,
			Consumer<DependencyUpdate> afterApply) {

		return WriteAction.computeAndWait(() -> {
			AtomicReference<UpgradeResult> result = new AtomicReference<>();
			CommandProcessor instance = CommandProcessor.getInstance();
			instance.runUndoTransparentAction(() -> {
				result.set(engine.apply(plan.getScope(), updates, afterApply));
			});

			return result.get();
		});
	}

	private void commit(PlannedUpgrade item) throws IOException {

		FileScope scope = plan.getScope();
		boolean committed;
		try {
			committed = vcs.commit(scope, service.getCommitMessage(item));
		} catch (IOException commitFailure) {
			// Some VCS implementations can report an error after creating the commit.
			// Keep the plan aligned with the repository in that case.
			if (!vcs.hasChanges(scope)) {
				service.removeCommittedItem(item);
				committedItemsRemoved = true;
			}
			throw commitFailure;
		}

		service.removeCommittedItem(item);
		committedItemsRemoved = true;
		if (!committed) {
			throw new IOException(MessageBundle.message("plan.vcs.commit.no-changes"));
		}
	}

	static String getCommandName(String displayName, ArtifactVersion version) {
		return MessageBundle.message("plan.apply.upgrade", displayName, version);
	}

}
