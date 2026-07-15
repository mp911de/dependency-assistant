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

import biz.paluch.dap.support.UpgradeResult;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;

/**
 * Apply-and-commit transaction that creates one commit per changed plan item.
 *
 * <p>Items are applied in plan order, ticketed ones first.
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
	public int apply(UpgradePlan plan, ProgressIndicator indicator) throws VcsException {

		List<UpgradePlanItem> ordered = ticketedFirst(plan);
		return doWithItems(ordered, indicator, it -> {

			UpgradeResult result = applyItem(plan.getScope(), it);
			if (result.hasChanges()) {
				commit(plan.getScope(), it);
			}
			return result;
		});
	}

	/**
	 * Apply one item's updates, on the EDT and without an undo boundary.
	 *
	 * <p>The write action must be taken on the EDT because saving a document fires
	 * {@code beforeAnyDocumentSaving}, which posts to the EDT and waits for it;
	 * holding the write lock on the calling background thread meanwhile dead-locks
	 * against an EDT that wants the write-intent lock. The command is
	 * undo-transparent: the PSI mutation needs a command, but the item is committed
	 * right after, so an undo entry would only offer to desynchronize the working
	 * tree from the commit.
	 */
	private UpgradeResult applyItem(FileScope scope, UpgradePlanItem item) {

		return WriteAction.computeAndWait(() -> {
			CommandProcessor instance = CommandProcessor.getInstance();
			try (AutoCloseable c = instance.withUndoTransparentAction()) {
				return engine.apply(scope, item.createUpdates());
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
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
				complete(item);
			}
			throw commitFailure;
		}

		complete(item);
		if (!committed) {
			throw new VcsException(MessageBundle.message("plan.vcs.commit.no-changes"));
		}
	}

	private void complete(UpgradePlanItem item) {
		service.removeCommittedItem(item);
	}

	private static List<UpgradePlanItem> ticketedFirst(UpgradePlan plan) {

		List<UpgradePlanItem> ordered = new ArrayList<>(plan.size());
		plan.stream().filter(UpgradePlanItem::hasTicket).forEach(ordered::add);
		plan.stream().filter(item -> !item.hasTicket()).forEach(ordered::add);
		return ordered;
	}

}
