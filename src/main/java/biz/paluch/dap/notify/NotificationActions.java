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

package biz.paluch.dap.notify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import biz.paluch.dap.assistant.AppliedUpdates;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;

/**
 * Factory for the follow-up actions offered on plugin notifications.
 *
 * <p>Every action is one-shot: choosing it expires the notification. Actions
 * that drive platform services take the project; actions that merely label a
 * caller-supplied operation take the operation.
 *
 * @author Mark Paluch
 * @see NotificationBuilder#action(NotificationAction)
 */
public class NotificationActions {

	/**
	 * Undo the platform's current undo operation, if any.
	 *
	 * @param project the project whose undo stack is used.
	 * @return the action.
	 */
	public static NotificationAction undo(Project project) {

		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.undo"), () -> {
			UndoManager undoManager = UndoManager.getInstance(project);
			if (undoManager.isUndoAvailable(null)) {
				undoManager.undo(null);
			}
		});
	}

	/**
	 * Undo every undo step of a run that created one global command per item.
	 *
	 * <p>Steps are popped from the top of the undo stack while the next step's
	 * description ends with one of the given command names, each name consuming one
	 * step. The loop stops at the first foreign step, so edits made after the run
	 * are never undone, and steps the user already reverted with Ctrl-Z are simply
	 * skipped.
	 *
	 * @param project the project whose undo stack is used.
	 * @param commandNames the command names the run created, one per step.
	 * @return the action.
	 */
	public static NotificationAction undoAll(Project project, Collection<String> commandNames) {

		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.undo"), () -> {

			UndoManager undoManager = UndoManager.getInstance(project);
			List<String> remaining = new ArrayList<>(commandNames);

			while (!remaining.isEmpty() && undoManager.isUndoAvailable(null)) {

				String next = undoManager.getUndoActionNameAndDescription(null).getSecond();
				if (!consume(remaining, next)) {
					return;
				}
				undoManager.undo(null);
			}
		});
	}

	// remove the first command name the undo description ends with
	private static boolean consume(List<String> commandNames, String undoDescription) {

		for (Iterator<String> iterator = commandNames.iterator(); iterator.hasNext();) {
			if (undoDescription.endsWith(iterator.next())) {
				iterator.remove();
				return true;
			}
		}

		return false;
	}

	/**
	 * Save all documents and open the commit dialog for the default change list,
	 * pre-filled with a commit message for the applied updates.
	 *
	 * @param project the project to commit in.
	 * @param updates the applied updates the commit message describes.
	 * @return the action.
	 */
	public static NotificationAction commit(Project project, AppliedUpdates updates) {

		String message = new NotificationTextTemplates(project).getCommitMessage(updates);

		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.commit"), () -> {

			FileDocumentManager.getInstance().saveAllDocuments();

			ChangeListManager manager = ChangeListManager.getInstance(project);
			manager.invokeAfterUpdate(true, () -> {
				LocalChangeList changeList = manager.getDefaultChangeList();
				AbstractVcsHelper.getInstance(project).commitChanges(changeList.getChanges(), changeList, message,
						null);
			});
		});
	}

	/**
	 * Reverse-apply the flagged entries of an applied-updates run.
	 *
	 * @param revert the operation that reverse-applies only flagged entries.
	 * @return the action.
	 */
	public static NotificationAction revertFlagged(Runnable revert) {
		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.undo-out-of-bounds"),
				revert);
	}

	/**
	 * Push committed changes.
	 *
	 * @param push the push operation.
	 * @return the action.
	 */
	public static NotificationAction push(Runnable push) {
		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.push"), push);
	}

	/**
	 * Restore a shelf created for a run.
	 *
	 * @param unshelve the unshelve operation.
	 * @return the action.
	 */
	public static NotificationAction unshelve(Runnable unshelve) {
		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.unshelve"), unshelve);
	}

	/**
	 * Refresh the release metadata cache.
	 *
	 * @param refresh the refresh operation.
	 * @return the action.
	 */
	public static NotificationAction refreshReleaseMetadata(Runnable refresh) {
		return NotificationAction.createSimpleExpiring(
				MessageBundle.message("notification.action.refresh-releases-metadata"), refresh);
	}

	/**
	 * Dismiss a prompt for now.
	 *
	 * @param dismiss the operation that records the dismissal.
	 * @return the action.
	 */
	public static NotificationAction notNow(Runnable dismiss) {
		return NotificationAction.createSimpleExpiring(MessageBundle.message("notification.not-now"), dismiss);
	}

}
